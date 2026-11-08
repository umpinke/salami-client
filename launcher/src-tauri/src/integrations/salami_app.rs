//! Salami-App fürs Handy: Der Launcher liefert dem Sync-Server (`branding::APP_SERVER_URL`)
//! Spielzeit und Screenshots. Die App zeigt damit pro Spieler Gesamtspielzeit, Login-Streak,
//! alle Screenshots und die Capes.
//!
//! - **Anmeldung** pro Minecraft-Konto wie beim Cape-Server: der Launcher meldet eine serverId
//!   bei Mojang an, der Sync-Server fragt dort nach und gibt ein Launcher-Token (`L.…`) aus. Das
//!   Token liegt mit der `installId` dieses Launchers in `salami_app.json`, unter Windows mit DPAPI
//!   verschlüsselt (siehe `utils::secret_store`). Bei 401 meldet sich der Launcher neu an.
//! - **Spielzeit**: Jeder Minecraft-Prozess ist eine Sitzung mit eigener UUID. Läuft das Spiel,
//!   geht dieselbe Sitzung alle 5 min mit `open: true` und aktuellem Ende an den Server, nach dem
//!   Spiel mit `open: false`. Offline bleiben Sitzungen gepuffert und werden später nachgesendet.
//!   Die Streak rechnet der Server selbst.
//! - **Screenshots** aller Profile: SHA-256 → `check` → fehlende per PUT hochladen. Beim Start des
//!   Launchers einmal alle (also auch alte), danach neue während und nach dem Spiel. Erledigte
//!   Hashes merkt sich `salami_app_screenshots.json`; in der App gelöschte Bilder meldet der
//!   Server nicht als fehlend, sie kommen also nicht wieder.
//! - **Kopplung**: Code + QR-Code für die App, Geräteliste, „Daten löschen“ (siehe
//!   `commands::salami_app_command`). Abschalten per Einstellung `salami_app_sync`.

use crate::config::{ProjectDirsExt, HTTP_CLIENT, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use crate::minecraft::minecraft_auth::Credentials;
use crate::state::process_state::ProcessMetadata;
use crate::state::state_manager::State;
use crate::utils::secret_store;
use chrono::{DateTime, Utc};
use once_cell::sync::Lazy;
use reqwest::StatusCode;
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::future::Future;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use uuid::Uuid;

const STORE_FILE: &str = "salami_app.json";
const SCREENSHOTS_FILE: &str = "salami_app_screenshots.json";
const MOJANG_JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";
/// Takt der Hintergrundaufgabe; Sitzungen gehen jeden [`SEND_EVERY`]-ten Takt an den Server.
const TICK: Duration = Duration::from_secs(60);
const SEND_EVERY: u64 = 5;
/// Wartezeit nach dem Launcher-Start, bevor die erste Runde läuft.
const STARTUP_DELAY: Duration = Duration::from_secs(20);
const MAX_SESSIONS_PER_CALL: usize = 500;
const MAX_HASHES_PER_CHECK: usize = 1000;
const MAX_SCREENSHOT_BYTES: u64 = 25 * 1024 * 1024;
/// Längste Sitzung, die der Server annimmt. Längere teilt der Launcher auf.
const MAX_SESSION_HOURS: i64 = 24;
/// Nach „Speicher voll“ (507) so lange keine Uploads versuchen.
const STORAGE_FULL_PAUSE: Duration = Duration::from_secs(60 * 60);
const RATE_LIMIT_WAIT: Duration = Duration::from_secs(15);

/// Inhalt von `salami_app.json` (verschlüsselt abgelegt).
#[derive(Serialize, Deserialize, Default, Debug, Clone, PartialEq)]
struct AppStore {
    /// Einmal erzeugte UUID v4 dieses Launchers. Gleiche installId ersetzt beim Server das alte Token.
    #[serde(default)]
    install_id: Option<String>,
    /// Minecraft-UUID (mit Bindestrichen) → Launcher-Token `L.…`
    #[serde(default)]
    tokens: BTreeMap<String, String>,
    /// Noch nicht (vollständig) gemeldete Sitzungen. Offene bleiben bis zum Spielende liegen.
    #[serde(default)]
    sessions: Vec<PlaySession>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
struct PlaySession {
    id: Uuid,
    /// Minecraft-Prozess (`ProcessMetadata::id`), zu dem die Sitzung gehört.
    process: Uuid,
    account: Uuid,
    start: DateTime<Utc>,
    end: DateTime<Utc>,
    profile: String,
    mc_version: String,
    open: bool,
    /// Ende, das der Server schon kennt (nur für offene Sitzungen).
    #[serde(default)]
    sent_end: Option<DateTime<Utc>>,
}

/// Was der Launcher über einen laufenden Minecraft-Prozess braucht.
#[derive(Debug, Clone)]
struct GameInfo {
    process: Uuid,
    account: Uuid,
    start: DateTime<Utc>,
    profile: String,
    mc_version: String,
}

impl GameInfo {
    fn from_metadata(meta: &ProcessMetadata) -> Option<Self> {
        let account = Uuid::parse_str(meta.account_uuid.as_deref()?).ok()?;
        Some(Self {
            process: meta.id,
            account,
            start: meta.start_time,
            profile: meta.profile_name.clone().unwrap_or_default(),
            mc_version: meta.minecraft_version.clone().unwrap_or_default(),
        })
    }
}

impl AppStore {
    fn load(path: &Path) -> AppStore {
        let Ok(contents) = std::fs::read_to_string(path) else {
            return AppStore::default();
        };
        let json = match secret_store::open(&contents) {
            Ok(secret_store::Opened::Protected(json)) => json,
            Ok(secret_store::Opened::Plain) => contents,
            Err(e) => {
                // z. B. von einem anderen PC kopiert: neu anfangen, der Server gibt neue Tokens aus
                log::warn!("[Salami-App] {} not readable, starting fresh: {}", STORE_FILE, e);
                return AppStore::default();
            }
        };
        serde_json::from_str(&json).unwrap_or_else(|e| {
            log::warn!("[Salami-App] {} damaged, starting fresh: {}", STORE_FILE, e);
            AppStore::default()
        })
    }

    fn save(&self, path: &Path) -> Result<()> {
        if let Some(dir) = path.parent() {
            std::fs::create_dir_all(dir)?;
        }
        let sealed = secret_store::seal(&serde_json::to_string(self)?)?;
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, sealed)?;
        std::fs::rename(tmp, path)?;
        Ok(())
    }
}

fn store_path() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join(STORE_FILE)
}

fn screenshots_path() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join(SCREENSHOTS_FILE)
}

/// `salami_app.json` im Speicher; geladen beim ersten Zugriff.
static STORE: Lazy<std::sync::Mutex<Option<AppStore>>> = Lazy::new(Default::default);
/// Nur eine Sitzungsmeldung zur Zeit.
static SESSIONS_LOCK: Lazy<tokio::sync::Mutex<()>> = Lazy::new(Default::default);
/// Nur ein Screenshot-Abgleich zur Zeit.
static SCREENSHOTS_LOCK: Lazy<tokio::sync::Mutex<()>> = Lazy::new(Default::default);
/// Prozesse, deren Spielende schon verbucht ist (die Prozessliste räumt erst danach auf).
static ENDED: Lazy<std::sync::Mutex<HashSet<Uuid>>> = Lazy::new(Default::default);
/// Zeitpunkt der letzten 507-Antwort („Speicher voll“).
static STORAGE_FULL: Lazy<std::sync::Mutex<Option<Instant>>> = Lazy::new(Default::default);
static STARTED: AtomicBool = AtomicBool::new(false);

fn read_store<T>(f: impl FnOnce(&AppStore) -> T) -> T {
    let mut guard = STORE.lock().unwrap_or_else(|e| e.into_inner());
    f(guard.get_or_insert_with(|| AppStore::load(&store_path())))
}

fn update_store<T>(f: impl FnOnce(&mut AppStore) -> T) -> T {
    let mut guard = STORE.lock().unwrap_or_else(|e| e.into_inner());
    let store = guard.get_or_insert_with(|| AppStore::load(&store_path()));
    let before = store.clone();
    let result = f(store);
    if *store != before {
        if let Err(e) = store.save(&store_path()) {
            log::warn!("[Salami-App] could not save {}: {}", STORE_FILE, e);
        }
    }
    result
}

fn install_id() -> String {
    update_store(|s| s.install_id.get_or_insert_with(|| Uuid::new_v4().to_string()).clone())
}

/// Adresse des Sync-Servers. In Debug-Builds überschreibbar per `SALAMI_APP_URL`.
pub fn server_url() -> Option<String> {
    #[cfg(debug_assertions)]
    if let Ok(url) = std::env::var("SALAMI_APP_URL") {
        if !url.is_empty() {
            return Some(url.trim_end_matches('/').to_string());
        }
    }
    crate::branding::APP_SERVER_URL.map(|url| url.trim_end_matches('/').to_string())
}

/// Sync an? (Server eingerichtet und Schalter `salami_app_sync` in den Einstellungen.)
pub async fn sync_enabled() -> bool {
    if server_url().is_none() {
        return false;
    }
    match State::get().await {
        Ok(state) => state.config_manager.get_config().await.salami_app_sync,
        Err(_) => false,
    }
}

// ------------------------------------------------------------------ HTTP

/// Fehler beim Sprechen mit dem Sync-Server.
#[derive(Debug)]
pub enum Failure {
    /// Token ungültig oder abgelaufen → neu anmelden.
    Unauthorized,
    /// 429: kurz warten und dann weitermachen.
    RateLimited,
    /// 507: 3 GB oder 5000 Bilder erreicht.
    StorageFull,
    /// Das Konto ist nicht (mehr) im Launcher.
    NoAccount,
    Server(StatusCode, String),
    Offline(String),
}

impl Failure {
    pub fn message(&self) -> String {
        match self {
            Failure::Unauthorized => "Anmeldung bei der Salami-App fehlgeschlagen".into(),
            Failure::RateLimited => "Zu viele Anfragen an die Salami-App, bitte kurz warten".into(),
            Failure::StorageFull => "Dein Speicher in der Salami-App ist voll (3 GB oder 5000 Bilder)".into(),
            Failure::NoAccount => "Bitte im Launcher mit einem Minecraft-Konto anmelden".into(),
            Failure::Server(_, message) => format!("Salami-App: {message}"),
            Failure::Offline(e) => format!("Salami-App-Server nicht erreichbar: {e}"),
        }
    }

    /// Später nochmal versuchen (statt die Daten zu verwerfen)?
    fn is_temporary(&self) -> bool {
        match self {
            Failure::Offline(_) | Failure::RateLimited | Failure::StorageFull | Failure::Unauthorized => true,
            Failure::Server(status, _) => status.is_server_error(),
            Failure::NoAccount => false,
        }
    }
}

impl From<Failure> for AppError {
    fn from(failure: Failure) -> Self {
        AppError::Other(failure.message())
    }
}

#[derive(Deserialize)]
struct ServerError {
    error: String,
}

async fn read_json<T: DeserializeOwned>(sent: reqwest::Result<reqwest::Response>) -> std::result::Result<T, Failure> {
    let response = sent.map_err(|e| Failure::Offline(e.to_string()))?;
    let status = response.status();
    if status.is_success() {
        return response
            .json()
            .await
            .map_err(|e| Failure::Server(status, format!("Antwort unlesbar: {e}")));
    }
    Err(match status {
        StatusCode::UNAUTHORIZED => Failure::Unauthorized,
        StatusCode::TOO_MANY_REQUESTS => Failure::RateLimited,
        StatusCode::INSUFFICIENT_STORAGE => Failure::StorageFull,
        _ => {
            let message = match response.json::<ServerError>().await {
                Ok(body) => body.error,
                Err(_) => status.to_string(),
            };
            Failure::Server(status, message)
        }
    })
}

fn base() -> std::result::Result<String, Failure> {
    server_url().ok_or_else(|| Failure::Server(StatusCode::NOT_FOUND, "kein Server eingerichtet".into()))
}

#[derive(Deserialize)]
struct Challenge {
    #[serde(rename = "serverId")]
    server_id: String,
}

#[derive(Deserialize)]
struct LoginResponse {
    token: String,
}

/// Anmeldung wie beim Betreten eines Minecraft-Servers: Das Zugangstoken geht nur an Mojang,
/// der Sync-Server prüft dort die serverId. → Launcher-Token `L.…`
async fn login(account: &Credentials) -> std::result::Result<String, Failure> {
    let base = base()?;
    let challenge: Challenge = read_json(
        HTTP_CLIENT
            .post(format!("{base}/v1/auth/challenge"))
            .timeout(Duration::from_secs(10))
            .send()
            .await,
    )
    .await?;

    let joined = HTTP_CLIENT
        .post(MOJANG_JOIN_URL)
        .timeout(Duration::from_secs(10))
        .json(&serde_json::json!({
            "accessToken": account.access_token,
            "selectedProfile": account.id.simple().to_string(),
            "serverId": challenge.server_id,
        }))
        .send()
        .await
        .map_err(|e| Failure::Offline(format!("Mojang: {e}")))?;
    if !joined.status().is_success() {
        return Err(Failure::Server(
            joined.status(),
            format!("Anmeldung bei Mojang fehlgeschlagen ({})", joined.status()),
        ));
    }

    let body: LoginResponse = read_json(
        HTTP_CLIENT
            .post(format!("{base}/v1/auth/launcher"))
            .timeout(Duration::from_secs(10))
            .json(&serde_json::json!({
                "name": account.username,
                "serverId": challenge.server_id,
                "installId": install_id(),
            }))
            .send()
            .await,
    )
    .await?;
    if !body.token.starts_with("L.") {
        return Err(Failure::Server(StatusCode::OK, "unerwartetes Token".into()));
    }
    Ok(body.token)
}

/// Launcher-Token für dieses Minecraft-Konto; meldet sich an, wenn noch keins da ist
/// oder `renew` gesetzt ist.
async fn token_for(account_id: Uuid, renew: bool) -> std::result::Result<String, Failure> {
    let key = account_id.to_string();
    if !renew {
        if let Some(token) = read_store(|s| s.tokens.get(&key).cloned()) {
            return Ok(token);
        }
    }
    let state = State::get().await.map_err(|e| Failure::Offline(e.to_string()))?;
    let account = state
        .minecraft_account_manager_v2
        .get_account_by_id_with_refresh(account_id)
        .await
        .map_err(|e| Failure::Offline(e.to_string()))?
        .ok_or(Failure::NoAccount)?;
    let token = login(&account).await?;
    log::info!("[Salami-App] signed in as {}", account.username);
    update_store(|s| {
        s.tokens.insert(key, token.clone());
    });
    Ok(token)
}

/// Führt `call` mit dem Token des Kontos aus; bei 401 einmal neu anmelden und wiederholen.
async fn with_token<T, F, Fut>(account: Uuid, call: F) -> std::result::Result<T, Failure>
where
    F: Fn(String) -> Fut,
    Fut: Future<Output = std::result::Result<T, Failure>>,
{
    let token = token_for(account, false).await?;
    match call(token).await {
        Err(Failure::Unauthorized) => {
            let token = token_for(account, true).await?;
            call(token).await
        }
        other => other,
    }
}

// ------------------------------------------------------------------ Spielzeit

/// Bringt die Sitzung eines laufenden Spiels auf `now`. Fehlt sie (Sync war beim Start aus,
/// oder die letzte wurde nach 24 h abgeschlossen), beginnt eine neue. Sitzungen über
/// [`MAX_SESSION_HOURS`] werden abgeschlossen und nahtlos fortgesetzt.
fn touch_running(sessions: &mut Vec<PlaySession>, game: &GameInfo, now: DateTime<Utc>) {
    let index = match sessions.iter().position(|s| s.process == game.process && s.open) {
        Some(index) => index,
        None => {
            let start = sessions
                .iter()
                .filter(|s| s.process == game.process)
                .map(|s| s.end)
                .max()
                .unwrap_or(game.start)
                .min(now);
            sessions.push(PlaySession {
                id: Uuid::new_v4(),
                process: game.process,
                account: game.account,
                start,
                end: start,
                profile: game.profile.clone(),
                mc_version: game.mc_version.clone(),
                open: true,
                sent_end: None,
            });
            sessions.len() - 1
        }
    };
    let limit = chrono::Duration::hours(MAX_SESSION_HOURS);
    let mut index = index;
    while now - sessions[index].start > limit {
        let session = &mut sessions[index];
        session.end = session.start + limit;
        session.open = false;
        let next = PlaySession {
            id: Uuid::new_v4(),
            start: session.end,
            end: session.end,
            open: true,
            sent_end: None,
            ..session.clone()
        };
        sessions.push(next);
        index = sessions.len() - 1;
    }
    sessions[index].end = now.max(sessions[index].start);
}

/// Schließt die offene Sitzung dieses Prozesses (Spielende). → `true`, wenn es eine gab.
fn close_session(sessions: &mut Vec<PlaySession>, game: &GameInfo, now: DateTime<Utc>) -> bool {
    if !sessions.iter().any(|s| s.process == game.process && s.open) {
        return false;
    }
    touch_running(sessions, game, now);
    if let Some(session) = sessions.iter_mut().find(|s| s.process == game.process && s.open) {
        session.open = false;
    }
    true
}

/// Offene Sitzungen von Prozessen, die nicht mehr laufen (Launcher war zu, Absturz …),
/// enden beim zuletzt bekannten Stand. → `true`, wenn sich etwas geändert hat.
fn close_orphans(sessions: &mut [PlaySession], running: &HashSet<Uuid>) -> bool {
    let mut changed = false;
    for session in sessions.iter_mut().filter(|s| s.open && !running.contains(&s.process)) {
        session.open = false;
        changed = true;
    }
    changed
}

/// Sitzungen, die an den Server müssen: abgeschlossene immer, offene nur mit neuem Ende.
fn due_sessions(sessions: &[PlaySession]) -> Vec<PlaySession> {
    sessions
        .iter()
        .filter(|s| !s.open || s.sent_end != Some(s.end))
        .cloned()
        .collect()
}

/// Nach erfolgreichem Senden: abgeschlossene Sitzungen vergessen, bei offenen das gesendete
/// Ende merken. Hat sich eine Sitzung inzwischen geändert, bleibt sie für die nächste Runde.
fn mark_sent(sessions: &mut Vec<PlaySession>, sent: &[PlaySession]) {
    for done in sent {
        if let Some(index) = sessions.iter().position(|s| s.id == done.id) {
            let current = &mut sessions[index];
            if current.end != done.end || current.open != done.open {
                continue;
            }
            if current.open {
                current.sent_end = Some(current.end);
            } else {
                sessions.remove(index);
            }
        }
    }
}

fn session_json(session: &PlaySession) -> serde_json::Value {
    serde_json::json!({
        "id": session.id.to_string(),
        "start": session.start.to_rfc3339_opts(chrono::SecondsFormat::Secs, true),
        "end": session.end.to_rfc3339_opts(chrono::SecondsFormat::Secs, true),
        "profile": session.profile,
        "mcVersion": session.mc_version,
        "open": session.open,
    })
}

#[derive(Deserialize)]
struct SessionsResponse {
    #[serde(default)]
    accepted: u32,
    #[serde(default)]
    duplicates: u32,
}

/// Sendet alle fälligen Sitzungen, gruppiert nach Konto, höchstens 500 pro Aufruf.
pub async fn flush_sessions() {
    if !sync_enabled().await {
        return;
    }
    let _guard = SESSIONS_LOCK.lock().await;
    let due = read_store(|s| due_sessions(&s.sessions));
    if due.is_empty() {
        return;
    }
    let mut by_account: BTreeMap<Uuid, Vec<PlaySession>> = BTreeMap::new();
    for session in due {
        by_account.entry(session.account).or_default().push(session);
    }
    let base = match base() {
        Ok(base) => base,
        Err(_) => return,
    };
    for (account, sessions) in by_account {
        for chunk in sessions.chunks(MAX_SESSIONS_PER_CALL) {
            let body = serde_json::json!({ "sessions": chunk.iter().map(session_json).collect::<Vec<_>>() });
            let result: std::result::Result<SessionsResponse, Failure> = with_token(account, |token| {
                let request = HTTP_CLIENT
                    .post(format!("{base}/v1/sync/sessions"))
                    .timeout(Duration::from_secs(20))
                    .bearer_auth(token)
                    .json(&body);
                async move { read_json(request.send().await).await }
            })
            .await;
            match result {
                Ok(response) => {
                    log::debug!(
                        "[Salami-App] sessions sent: {} accepted, {} duplicates",
                        response.accepted,
                        response.duplicates
                    );
                    update_store(|s| mark_sent(&mut s.sessions, chunk));
                }
                Err(failure) if failure.is_temporary() => {
                    log::info!("[Salami-App] sessions kept for later: {}", failure.message());
                    break;
                }
                Err(failure) => {
                    // Nie annehmbar (Konto entfernt, ungültige Daten): nicht ewig wiederholen
                    log::warn!("[Salami-App] dropping {} session(s): {}", chunk.len(), failure.message());
                    update_store(|s| {
                        s.sessions.retain(|x| x.open || !chunk.iter().any(|c| c.id == x.id));
                        for session in s.sessions.iter_mut().filter(|x| x.open) {
                            if let Some(sent) = chunk.iter().find(|c| c.id == session.id) {
                                session.sent_end = Some(sent.end);
                            }
                        }
                    });
                }
            }
        }
    }
}

/// Spielstart: Sitzung anlegen und gleich melden. Läuft im Hintergrund.
pub fn game_started(meta: &ProcessMetadata) {
    let Some(game) = GameInfo::from_metadata(meta) else {
        return;
    };
    tokio::spawn(async move {
        if !sync_enabled().await {
            return;
        }
        update_store(|s| touch_running(&mut s.sessions, &game, Utc::now()));
        flush_sessions().await;
    });
}

/// Spielende: Sitzung abschließen und melden, danach neue Screenshots dieses Profils hochladen.
pub fn game_exited(meta: &ProcessMetadata) {
    ENDED.lock().unwrap_or_else(|e| e.into_inner()).insert(meta.id);
    let Some(game) = GameInfo::from_metadata(meta) else {
        return;
    };
    let profile_id = meta.profile_id;
    tokio::spawn(async move {
        update_store(|s| close_session(&mut s.sessions, &game, Utc::now()));
        if !sync_enabled().await {
            return;
        }
        flush_sessions().await;
        sync_profile_screenshots(game.account, profile_id, &game.profile, game.start).await;
    });
}

// ------------------------------------------------------------------ Screenshots

/// Inhalt von `salami_app_screenshots.json` (keine Geheimnisse, nur Pfade und Hashes).
#[derive(Serialize, Deserialize, Default, Debug)]
struct ScreenshotIndex {
    /// Pfad → bekannter Hash, solange Größe und Änderungszeit gleich sind.
    #[serde(default)]
    files: HashMap<String, IndexedFile>,
    /// Erledigt: hochgeladen, schon auf dem Server oder in der App gelöscht.
    #[serde(default)]
    done: HashSet<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
struct IndexedFile {
    size: u64,
    modified: i64,
    sha256: String,
}

impl ScreenshotIndex {
    fn load(path: &Path) -> ScreenshotIndex {
        std::fs::read(path)
            .ok()
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default()
    }

    fn save(&self, path: &Path) {
        let tmp = path.with_extension("json.tmp");
        let written = serde_json::to_vec(self)
            .map_err(AppError::from)
            .and_then(|bytes| std::fs::write(&tmp, bytes).map_err(AppError::from))
            .and_then(|_| std::fs::rename(&tmp, path).map_err(AppError::from));
        if let Err(e) = written {
            log::warn!("[Salami-App] could not save {}: {}", SCREENSHOTS_FILE, e);
        }
    }
}

/// Ein Screenshot auf der Platte.
#[derive(Debug, Clone)]
struct Shot {
    path: PathBuf,
    profile: String,
    file_name: String,
    taken: DateTime<Utc>,
    size: u64,
    modified: i64,
}

/// PNGs direkt in `<ordner>/screenshots`, neuer als `since` (falls gesetzt).
fn list_shots(dirs: &[(PathBuf, String)], since: Option<DateTime<Utc>>) -> Vec<Shot> {
    let mut shots = Vec::new();
    let mut seen = HashSet::new();
    for (dir, profile) in dirs {
        let Ok(entries) = std::fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            let is_png = path
                .extension()
                .and_then(|e| e.to_str())
                .is_some_and(|e| e.eq_ignore_ascii_case("png"));
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            if !is_png || !meta.is_file() || meta.len() == 0 || meta.len() > MAX_SCREENSHOT_BYTES {
                continue;
            }
            let modified: DateTime<Utc> = meta.modified().map(DateTime::from).unwrap_or_else(|_| Utc::now());
            if since.is_some_and(|since| modified < since) {
                continue;
            }
            if !seen.insert(path.clone()) {
                continue;
            }
            shots.push(Shot {
                file_name: entry.file_name().to_string_lossy().into_owned(),
                profile: profile.clone(),
                taken: modified,
                size: meta.len(),
                modified: modified.timestamp_millis(),
                path,
            });
        }
    }
    shots.sort_by_key(|shot| shot.taken);
    shots
}

fn sha256_file(path: &Path) -> std::io::Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha256::new();
    std::io::copy(&mut file, &mut hasher)?;
    Ok(hex::encode(hasher.finalize()))
}

/// Wie `encodeURIComponent` in JavaScript (für die Kopfzeilen X-Profile und X-File-Name).
pub fn encode_uri_component(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'!' | b'~' | b'*' | b'\'' | b'(' | b')' => {
                out.push(byte as char)
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}

fn storage_full_recently() -> bool {
    STORAGE_FULL
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .is_some_and(|at| at.elapsed() < STORAGE_FULL_PAUSE)
}

/// Ergebnis eines Screenshot-Abgleichs.
#[derive(Debug, Default, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScreenshotReport {
    pub uploaded: u32,
    pub already_there: u32,
    pub storage_full: bool,
}

#[derive(Deserialize)]
struct CheckResponse {
    missing: Vec<String>,
}

async fn upload_shot(base: &str, account: Uuid, shot: &Shot, hash: &str) -> std::result::Result<(), Failure> {
    let bytes = tokio::fs::read(&shot.path)
        .await
        .map_err(|e| Failure::Server(StatusCode::BAD_REQUEST, format!("{} nicht lesbar: {e}", shot.file_name)))?;
    let url = format!("{base}/v1/sync/screenshots/{hash}");
    let taken = shot.taken.to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let profile = encode_uri_component(&shot.profile);
    let file_name = encode_uri_component(&shot.file_name);
    with_token(account, |token| {
        let request = HTTP_CLIENT
            .put(&url)
            .timeout(Duration::from_secs(120))
            .bearer_auth(token)
            // Content-Length setzt reqwest selbst (Body mit bekannter Länge)
            .header(reqwest::header::CONTENT_TYPE, "image/png")
            .header("X-Taken-At", &taken)
            .header("X-Profile", &profile)
            .header("X-File-Name", &file_name)
            .body(bytes.clone());
        async move { read_json::<serde_json::Value>(request.send().await).await.map(|_| ()) }
    })
    .await
}

/// Gleicht diese Screenshots mit dem Server ab und lädt fehlende für `account` hoch.
async fn sync_shots(account: Uuid, shots: Vec<Shot>, prune: bool) -> std::result::Result<ScreenshotReport, Failure> {
    let mut report = ScreenshotReport::default();
    if storage_full_recently() {
        report.storage_full = true;
        return Ok(report);
    }
    let base = base()?;
    let _guard = SCREENSHOTS_LOCK.lock().await;
    let index_path = screenshots_path();
    let mut index = tokio::task::spawn_blocking({
        let path = index_path.clone();
        move || ScreenshotIndex::load(&path)
    })
    .await
    .unwrap_or_default();

    if prune {
        let present: HashSet<String> = shots.iter().map(|s| s.path.display().to_string()).collect();
        index.files.retain(|path, _| present.contains(path));
    }

    // Hashes: aus dem Index, sonst neu berechnen
    let mut pending: Vec<(Shot, String)> = Vec::new();
    let mut seen_hashes = HashSet::new();
    for shot in shots {
        let key = shot.path.display().to_string();
        let known = index
            .files
            .get(&key)
            .filter(|f| f.size == shot.size && f.modified == shot.modified)
            .map(|f| f.sha256.clone());
        let hash = match known {
            Some(hash) => hash,
            None => {
                let path = shot.path.clone();
                match tokio::task::spawn_blocking(move || sha256_file(&path)).await {
                    Ok(Ok(hash)) => {
                        index.files.insert(
                            key,
                            IndexedFile { size: shot.size, modified: shot.modified, sha256: hash.clone() },
                        );
                        hash
                    }
                    _ => continue,
                }
            }
        };
        if index.done.contains(&hash) || !seen_hashes.insert(hash.clone()) {
            continue;
        }
        pending.push((shot, hash));
    }

    let mut outcome = Ok(());
    'chunks: for chunk in pending.chunks(MAX_HASHES_PER_CHECK) {
        let hashes: Vec<&String> = chunk.iter().map(|(_, hash)| hash).collect();
        let body = serde_json::json!({ "hashes": hashes });
        let checked: std::result::Result<CheckResponse, Failure> = with_token(account, |token| {
            let request = HTTP_CLIENT
                .post(format!("{base}/v1/sync/screenshots/check"))
                .timeout(Duration::from_secs(20))
                .bearer_auth(token)
                .json(&body);
            async move { read_json(request.send().await).await }
        })
        .await;
        let missing: HashSet<String> = match checked {
            Ok(response) => response.missing.into_iter().collect(),
            Err(failure) => {
                outcome = Err(failure);
                break;
            }
        };
        for (shot, hash) in chunk {
            if !missing.contains(hash) {
                index.done.insert(hash.clone());
                report.already_there += 1;
                continue;
            }
            let mut attempts = 0;
            loop {
                attempts += 1;
                match upload_shot(&base, account, shot, hash).await {
                    Ok(()) => {
                        index.done.insert(hash.clone());
                        report.uploaded += 1;
                        *STORAGE_FULL.lock().unwrap_or_else(|e| e.into_inner()) = None;
                        break;
                    }
                    Err(Failure::RateLimited) if attempts < 4 => tokio::time::sleep(RATE_LIMIT_WAIT).await,
                    Err(Failure::StorageFull) => {
                        *STORAGE_FULL.lock().unwrap_or_else(|e| e.into_inner()) = Some(Instant::now());
                        report.storage_full = true;
                        break 'chunks;
                    }
                    Err(failure) if failure.is_temporary() => {
                        outcome = Err(failure);
                        break 'chunks;
                    }
                    Err(failure) => {
                        // z. B. kein gültiges PNG: nicht bei jedem Start erneut versuchen
                        log::warn!("[Salami-App] screenshot {} skipped: {}", shot.file_name, failure.message());
                        index.done.insert(hash.clone());
                        break;
                    }
                }
            }
            if report.uploaded > 0 && report.uploaded % 10 == 0 {
                index.save(&index_path);
            }
        }
    }

    tokio::task::spawn_blocking(move || index.save(&index_path)).await.ok();
    if report.uploaded > 0 || report.storage_full {
        log::info!(
            "[Salami-App] screenshots: {} uploaded, {} already there{}",
            report.uploaded,
            report.already_there,
            if report.storage_full { ", storage full" } else { "" }
        );
    }
    outcome.map(|_| report)
}

/// `(screenshots-Ordner, Profilname)` aller Profile, auch der Standardprofile. Profile, die sich
/// einen Spielordner teilen, ergeben denselben Ordner nur einmal.
async fn screenshot_dirs() -> Vec<(PathBuf, String)> {
    let Ok(state) = State::get().await else {
        return Vec::new();
    };
    let mut profiles = state.profile_manager.list_profiles().await.unwrap_or_default();
    profiles.extend(state.norisk_version_manager.get_config().await.profiles);
    let mut dirs: Vec<(PathBuf, String)> = Vec::new();
    for profile in profiles {
        let Ok(instance) = state.profile_manager.calculate_instance_path_for_profile(&profile) else {
            continue;
        };
        let dir = instance.join("screenshots");
        if !dirs.iter().any(|(known, _)| *known == dir) {
            dirs.push((dir, profile.name.clone()));
        }
    }
    dirs
}

/// Alle Screenshots aller Profile abgleichen (beim Launcher-Start und nach dem Koppeln).
pub async fn sync_all_screenshots(account: Uuid) -> std::result::Result<ScreenshotReport, Failure> {
    let dirs = screenshot_dirs().await;
    let shots = tokio::task::spawn_blocking(move || list_shots(&dirs, None)).await.unwrap_or_default();
    sync_shots(account, shots, true).await
}

/// Neue Screenshots eines Profils seit `since` (während und nach dem Spiel).
async fn sync_profile_screenshots(account: Uuid, profile_id: Uuid, profile_name: &str, since: DateTime<Utc>) {
    let Ok(state) = State::get().await else {
        return;
    };
    let Ok(instance) = state.profile_manager.get_profile_instance_path(profile_id).await else {
        return;
    };
    let dirs = vec![(instance.join("screenshots"), profile_name.to_string())];
    // etwas Spielraum für ungenaue Dateizeiten
    let since = since - chrono::Duration::minutes(1);
    let shots = tokio::task::spawn_blocking(move || list_shots(&dirs, Some(since))).await.unwrap_or_default();
    if shots.is_empty() {
        return;
    }
    if let Err(failure) = sync_shots(account, shots, false).await {
        log::info!("[Salami-App] screenshots kept for later: {}", failure.message());
    }
}

// ------------------------------------------------------------------ Hintergrund

async fn active_account_id() -> Option<Uuid> {
    let state = State::get().await.ok()?;
    state
        .minecraft_account_manager_v2
        .get_active_account()
        .await
        .ok()
        .flatten()
        .map(|account| account.id)
}

/// Alles nachholen: Sitzungen senden und alle Screenshots des aktiven Kontos abgleichen.
pub async fn sync_everything() {
    if !sync_enabled().await {
        return;
    }
    flush_sessions().await;
    if let Some(account) = active_account_id().await {
        if let Err(failure) = sync_all_screenshots(account).await {
            log::info!("[Salami-App] screenshot sync postponed: {}", failure.message());
        }
    }
}

async fn tick(round: u64, was_enabled: &mut bool) {
    let enabled = sync_enabled().await;
    if !enabled {
        if *was_enabled || round == 1 {
            // Ausgeschaltet: Gepuffertes nicht später doch noch senden
            update_store(|s| s.sessions.clear());
        }
        *was_enabled = false;
        return;
    }
    let just_enabled = !*was_enabled;
    *was_enabled = true;

    let Ok(state) = State::get().await else {
        return;
    };
    let ended = ENDED.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let running: Vec<GameInfo> = state
        .process_manager
        .list_processes()
        .await
        .iter()
        .filter(|meta| !ended.contains(&meta.id))
        .filter_map(GameInfo::from_metadata)
        .collect();
    let running_ids: HashSet<Uuid> = running.iter().map(|g| g.process).collect();
    let now = Utc::now();
    let closed = update_store(|s| {
        for game in &running {
            touch_running(&mut s.sessions, game, now);
        }
        close_orphans(&mut s.sessions, &running_ids)
    });

    if just_enabled {
        sync_everything().await;
        return;
    }
    if closed || round % SEND_EVERY == 0 {
        flush_sessions().await;
    }
    if round % SEND_EVERY == 0 {
        for meta in state.process_manager.list_processes().await {
            if let Some(game) = GameInfo::from_metadata(&meta).filter(|g| running_ids.contains(&g.process)) {
                sync_profile_screenshots(game.account, meta.profile_id, &game.profile, game.start).await;
            }
        }
    }
}

/// Startet die Hintergrundaufgabe (einmal pro Launcher-Lauf): nach dem Start alles nachholen,
/// danach jede Minute laufende Sitzungen fortschreiben und alle 5 min melden.
pub fn start_background_sync() {
    if STARTED.swap(true, Ordering::SeqCst) || server_url().is_none() {
        return;
    }
    tokio::spawn(async {
        tokio::time::sleep(STARTUP_DELAY).await;
        let mut was_enabled = false;
        let mut round: u64 = 0;
        let mut interval = tokio::time::interval(TICK);
        loop {
            interval.tick().await;
            round += 1;
            tick(round, &mut was_enabled).await;
        }
    });
}

// ------------------------------------------------------------------ Kopplung & Konto

/// Kopplungscode für die App, mit QR-Code (SVG) für genau die `url` des Servers.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairCode {
    pub code: String,
    pub expires_at: String,
    pub url: String,
    #[serde(default)]
    pub qr_svg: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Device {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub paired_at: Option<String>,
    #[serde(default)]
    pub last_seen: Option<String>,
}

#[derive(Deserialize)]
struct DevicesResponse {
    devices: Vec<Device>,
}

/// QR-Code als SVG (schwarz auf weiß, 4 Module Ruhezone wie im Standard).
pub fn qr_svg(text: &str) -> Result<String> {
    use qrcodegen::{QrCode, QrCodeEcc};
    use std::fmt::Write;
    let qr = QrCode::encode_text(text, QrCodeEcc::Medium)
        .map_err(|e| AppError::Other(format!("QR-Code konnte nicht erzeugt werden: {e:?}")))?;
    let border = 4;
    let size = qr.size() + border * 2;
    let mut path = String::new();
    for y in 0..qr.size() {
        for x in 0..qr.size() {
            if qr.get_module(x, y) {
                let _ = write!(path, "M{},{}h1v1h-1z", x + border, y + border);
            }
        }
    }
    Ok(format!(
        r##"<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" shape-rendering="crispEdges"><rect width="{size}" height="{size}" fill="#fff"/><path d="{path}" fill="#000"/></svg>"##
    ))
}

/// Kopplungscode für das aktive Konto (6 Ziffern, 10 min gültig).
pub async fn pair_code(account: Uuid) -> Result<PairCode> {
    let base = base()?;
    let mut code: PairCode = with_token(account, |token| {
        let request = HTTP_CLIENT
            .post(format!("{base}/v1/pair/code"))
            .timeout(Duration::from_secs(10))
            .bearer_auth(token);
        async move { read_json(request.send().await).await }
    })
    .await?;
    if !code.url.starts_with("salami://pair?") || !code.code.chars().all(|c| c.is_ascii_digit()) {
        return Err(AppError::Other("Salami-App: unerwarteter Kopplungscode".into()));
    }
    code.qr_svg = qr_svg(&code.url)?;
    Ok(code)
}

pub async fn devices(account: Uuid) -> Result<Vec<Device>> {
    let base = base()?;
    let response: DevicesResponse = with_token(account, |token| {
        let request = HTTP_CLIENT
            .get(format!("{base}/v1/devices"))
            .timeout(Duration::from_secs(10))
            .bearer_auth(token);
        async move { read_json(request.send().await).await }
    })
    .await?;
    Ok(response.devices)
}

fn is_valid_device_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 128 && id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

pub async fn remove_device(account: Uuid, id: &str) -> Result<()> {
    if !is_valid_device_id(id) {
        return Err(AppError::Other(format!("Ungültige Geräte-ID: {id}")));
    }
    let base = base()?;
    with_token(account, |token| {
        let request = HTTP_CLIENT
            .delete(format!("{base}/v1/devices/{id}"))
            .timeout(Duration::from_secs(10))
            .bearer_auth(token);
        async move { read_json::<serde_json::Value>(request.send().await).await }
    })
    .await?;
    Ok(())
}

/// Löscht alle Daten dieses Kontos auf dem Sync-Server (Spielzeit, Screenshots, Geräte).
async fn delete_account(account: Uuid) -> std::result::Result<(), Failure> {
    let base = base()?;
    with_token(account, |token| {
        let request = HTTP_CLIENT
            .delete(format!("{base}/v1/account"))
            .timeout(Duration::from_secs(30))
            .bearer_auth(token);
        async move { read_json::<serde_json::Value>(request.send().await).await }
    })
    .await
    .map(|_| ())
}

/// „Daten löschen“: Für alle gekoppelten Konten (und das aktive) alles auf dem Server löschen,
/// danach lokal Tokens, Puffer und Screenshot-Liste vergessen. Den Schalter stellt der Aufrufer ab.
/// → Anzahl der Konten
pub async fn delete_all_data(active: Option<Uuid>) -> Result<usize> {
    let mut accounts: Vec<Uuid> = read_store(|s| s.tokens.keys().filter_map(|k| Uuid::parse_str(k).ok()).collect());
    if let Some(active) = active {
        if !accounts.contains(&active) {
            accounts.push(active);
        }
    }
    let _sessions = SESSIONS_LOCK.lock().await;
    let _screenshots = SCREENSHOTS_LOCK.lock().await;
    let mut deleted = 0;
    for account in accounts {
        match delete_account(account).await {
            Ok(()) => deleted += 1,
            // Konto nicht mehr im Launcher und kein gültiges Token: dort ist nichts mehr zu löschen
            Err(Failure::NoAccount) => {}
            Err(failure) => return Err(failure.into()),
        }
        update_store(|s| {
            s.tokens.remove(&account.to_string());
            s.sessions.retain(|x| x.account != account);
        });
    }
    update_store(|s| {
        s.tokens.clear();
        s.sessions.clear();
    });
    let index_path = screenshots_path();
    if index_path.exists() {
        std::fs::remove_file(&index_path)?;
    }
    *STORAGE_FULL.lock().unwrap_or_else(|e| e.into_inner()) = None;
    log::info!("[Salami-App] data deleted for {} account(s)", deleted);
    Ok(deleted)
}

/// Stand für die Einstellungen und den Dialog „Handy koppeln“.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppStatus {
    pub available: bool,
    pub enabled: bool,
    /// Aktives Konto ist beim Sync-Server angemeldet.
    pub linked: bool,
    pub account_name: Option<String>,
    pub pending_sessions: usize,
    pub storage_full: bool,
    pub download_url: String,
    pub privacy_url: String,
}

pub async fn status() -> AppStatus {
    let account = match State::get().await {
        Ok(state) => state.minecraft_account_manager_v2.get_active_account().await.ok().flatten(),
        Err(_) => None,
    };
    let (linked, pending_sessions) = read_store(|s| {
        (
            account.as_ref().is_some_and(|a| s.tokens.contains_key(&a.id.to_string())),
            s.sessions.len(),
        )
    });
    AppStatus {
        available: server_url().is_some(),
        enabled: sync_enabled().await,
        linked,
        account_name: account.map(|a| a.username),
        pending_sessions,
        storage_full: storage_full_recently(),
        download_url: crate::branding::APP_DOWNLOAD_PAGE.to_string(),
        privacy_url: crate::branding::APP_PRIVACY_PAGE.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::TimeZone;

    /// Zeitpunkt `minutes` Minuten nach einem festen Start.
    fn at(minutes: i64) -> DateTime<Utc> {
        Utc.timestamp_opt(1_790_000_000 + minutes * 60, 0).unwrap()
    }

    fn game(process: Uuid, start: DateTime<Utc>) -> GameInfo {
        GameInfo {
            process,
            account: Uuid::from_u128(7),
            start,
            profile: "Salami 1.21.11".into(),
            mc_version: "1.21.11".into(),
        }
    }

    #[test]
    fn running_game_extends_one_open_session() {
        let process = Uuid::new_v4();
        let g = game(process, at(0));
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &g, at(0));
        touch_running(&mut sessions, &g, at(5));
        touch_running(&mut sessions, &g, at(10));
        assert_eq!(sessions.len(), 1);
        assert!(sessions[0].open);
        assert_eq!(sessions[0].start, at(0));
        assert_eq!(sessions[0].end, at(10));
        assert_eq!(sessions[0].id.get_version_num(), 4);

        assert!(close_session(&mut sessions, &g, at(12)));
        assert!(!sessions[0].open);
        assert_eq!(sessions[0].end, at(12));
        // zweites Spielende ändert nichts mehr
        assert!(!close_session(&mut sessions, &g, at(20)));
        assert_eq!(sessions[0].end, at(12));
    }

    #[test]
    fn sessions_longer_than_a_day_are_split() {
        let g = game(Uuid::new_v4(), at(0));
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &g, at(0));
        touch_running(&mut sessions, &g, at(25 * 60));
        assert_eq!(sessions.len(), 2);
        assert!(!sessions[0].open);
        assert_eq!(sessions[0].end, at(24 * 60));
        assert!(sessions[1].open);
        assert_eq!(sessions[1].start, at(24 * 60));
        assert_eq!(sessions[1].end, at(25 * 60));
        assert_ne!(sessions[0].id, sessions[1].id);
    }

    #[test]
    fn game_already_running_when_sync_starts_counts_from_launch() {
        let g = game(Uuid::new_v4(), at(0));
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &g, at(30));
        assert_eq!(sessions[0].start, at(0));
        assert_eq!(sessions[0].end, at(30));
    }

    #[test]
    fn orphans_close_at_last_known_end() {
        let running = Uuid::new_v4();
        let gone = Uuid::new_v4();
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &game(running, at(0)), at(5));
        touch_running(&mut sessions, &game(gone, at(0)), at(5));
        assert!(close_orphans(&mut sessions, &HashSet::from([running])));
        let closed = sessions.iter().find(|s| s.process == gone).unwrap();
        assert!(!closed.open);
        assert_eq!(closed.end, at(5));
        assert!(sessions.iter().find(|s| s.process == running).unwrap().open);
        assert!(!close_orphans(&mut sessions, &HashSet::from([running])));
    }

    #[test]
    fn sent_sessions_are_forgotten_or_remembered() {
        let open_game = game(Uuid::new_v4(), at(0));
        let closed_game = game(Uuid::new_v4(), at(0));
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &open_game, at(5));
        touch_running(&mut sessions, &closed_game, at(5));
        close_session(&mut sessions, &closed_game, at(6));

        let due = due_sessions(&sessions);
        assert_eq!(due.len(), 2);
        mark_sent(&mut sessions, &due);
        assert_eq!(sessions.len(), 1, "closed session is done");
        assert!(due_sessions(&sessions).is_empty(), "open session unchanged since sending");

        // läuft weiter → wieder fällig; ändert sich während des Sendens → bleibt fällig
        touch_running(&mut sessions, &open_game, at(10));
        let due = due_sessions(&sessions);
        assert_eq!(due.len(), 1);
        touch_running(&mut sessions, &open_game, at(11));
        mark_sent(&mut sessions, &due);
        assert_eq!(due_sessions(&sessions).len(), 1);
    }

    #[test]
    fn session_json_matches_contract() {
        let mut sessions = Vec::new();
        touch_running(&mut sessions, &game(Uuid::new_v4(), at(0)), at(5));
        let json = session_json(&sessions[0]);
        assert_eq!(json["open"], true);
        assert_eq!(json["mcVersion"], "1.21.11");
        assert_eq!(json["profile"], "Salami 1.21.11");
        assert!(json["start"].as_str().unwrap().ends_with('Z'));
        assert_eq!(json["id"].as_str().unwrap().len(), 36);
    }

    #[test]
    fn encodes_like_encode_uri_component() {
        assert_eq!(encode_uri_component("Salami 1.21.11"), "Salami%201.21.11");
        assert_eq!(encode_uri_component("2026-11-08_14.03.22.png"), "2026-11-08_14.03.22.png");
        assert_eq!(encode_uri_component("Größe & Spaß/ä"), "Gr%C3%B6%C3%9Fe%20%26%20Spa%C3%9F%2F%C3%A4");
        assert_eq!(encode_uri_component("(a)!*'~"), "(a)!*'~");
    }

    #[test]
    fn qr_code_is_svg() {
        let svg = qr_svg("salami://pair?code=482913").unwrap();
        assert!(svg.starts_with("<svg"));
        assert!(svg.contains("<path d=\"M"));
    }

    #[test]
    fn device_ids_are_validated() {
        assert!(is_valid_device_id("d_8f3a-91"));
        assert!(!is_valid_device_id("../account"));
        assert!(!is_valid_device_id(""));
    }

    #[test]
    fn store_survives_a_roundtrip_without_plain_tokens() {
        let dir = std::env::temp_dir().join(format!("salami-app-store-{}", std::process::id()));
        let path = dir.join(STORE_FILE);
        let mut store = AppStore::default();
        store.install_id = Some(Uuid::new_v4().to_string());
        store.tokens.insert(Uuid::from_u128(7).to_string(), "L.geheim-123".into());
        touch_running(&mut store.sessions, &game(Uuid::new_v4(), at(0)), at(5));
        store.save(&path).unwrap();
        #[cfg(windows)]
        assert!(!std::fs::read_to_string(&path).unwrap().contains("L.geheim-123"));
        assert_eq!(AppStore::load(&path), store);
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn lists_only_pngs_newer_than_since() {
        let dir = std::env::temp_dir().join(format!("salami-app-shots-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("a.png"), b"png").unwrap();
        std::fs::write(dir.join("b.PNG"), b"png").unwrap();
        std::fs::write(dir.join("notes.txt"), b"x").unwrap();
        std::fs::write(dir.join("empty.png"), b"").unwrap();
        let dirs = vec![(dir.clone(), "Profil".to_string())];
        let shots = list_shots(&dirs, None);
        let mut names: Vec<_> = shots.iter().map(|s| s.file_name.as_str()).collect();
        names.sort();
        assert_eq!(names, ["a.png", "b.PNG"]);
        assert!(list_shots(&dirs, Some(Utc::now() + chrono::Duration::hours(1))).is_empty());
        assert_eq!(sha256_file(&dir.join("a.png")).unwrap().len(), 64);
        let _ = std::fs::remove_dir_all(dir);
    }
}
