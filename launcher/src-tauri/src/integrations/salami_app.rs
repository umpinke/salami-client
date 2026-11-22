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
//! - **Clips** aus dem Clip-Ordner (`clip_library::list`), auch die schon vorhandenen: wie bei den
//!   Screenshots SHA-256 → `check` → fehlende per PUT, aber einzeln nacheinander und als Stream
//!   aus der Datei. Nur fertige Dateien (seit 30 s unverändert). Eigener Schalter
//!   `salami_app_clips`, erledigte Hashes in `salami_app_clips.json`.
//! - **Kopplung**: Code + QR-Code für die App, Geräteliste, „Daten löschen“ (siehe
//!   `commands::salami_app_command`). Abschalten per Einstellung `salami_app_sync`.
//! - **Erst nach dem Koppeln** (Salami): Hochgeladen wird nur für Konten, mit denen laut der
//!   letzten Geräteliste des Servers mindestens ein Handy gekoppelt ist (`paired_devices` in
//!   `salami_app.json`). Bis dahin bleiben Sitzungen nur lokal liegen; Screenshots und Clips werden
//!   nicht einmal angefasst. Das „alles nachholen“ läuft, sobald eine Geräteliste das erste Gerät
//!   zeigt. Wird das letzte Gerät entfernt oder werden die Daten gelöscht, hört der Upload auf.

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
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::{Duration, Instant};
use uuid::Uuid;

const STORE_FILE: &str = "salami_app.json";
const SCREENSHOTS_FILE: &str = "salami_app_screenshots.json";
const CLIPS_FILE: &str = "salami_app_clips.json";
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
/// Ein Clip gilt als fertig, wenn seine Datei so lange nicht mehr geändert wurde. Hält Clips
/// zurück, die gerade gespeichert, geschnitten oder exportiert werden.
const CLIP_SETTLE_SECS: i64 = 30;
/// Ein Clip darf bis 600 MiB groß sein; auch über eine langsame Leitung soll das durchgehen.
const CLIP_UPLOAD_TIMEOUT: Duration = Duration::from_secs(30 * 60);

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
    /// Salami: Minecraft-UUID → Anzahl gekoppelter Handys laut der letzten Geräteliste vom
    /// Server. Hochgeladen (Spielzeit, Screenshots, Clips) wird nur für Konten mit mindestens
    /// einem Gerät. Ältere Launcher kannten das Feld nicht: nach dem Update gilt erst einmal kein
    /// Konto als gekoppelt, bis der Server eine nicht leere Geräteliste meldet.
    #[serde(default)]
    paired_devices: BTreeMap<String, u32>,
}

/// Salami: Darf für ein Konto hochgeladen werden? Nur mit eingeschaltetem Sync (`salami_app_sync`)
/// und mindestens einem gekoppelten Handy.
fn uploads_allowed(sync_on: bool, paired_devices: u32) -> bool {
    sync_on && paired_devices > 0
}

/// Salami: Clips zusätzlich nur mit eingeschaltetem Schalter „Clips hochladen“.
fn clip_uploads_allowed(sync_on: bool, clips_on: bool, paired_devices: u32) -> bool {
    clips_on && uploads_allowed(sync_on, paired_devices)
}

/// Salami: Trägt die Anzahl der Geräte aus einer Geräteliste des Servers ein (0 = entkoppelt).
/// → `true`, wenn das Konto damit gerade erst gekoppelt wurde (vorher kein Gerät).
fn record_devices(paired: &mut BTreeMap<String, u32>, account: &str, count: usize) -> bool {
    let before = paired.get(account).copied().unwrap_or(0);
    let count = u32::try_from(count).unwrap_or(u32::MAX);
    if count == 0 {
        paired.remove(account);
    } else {
        paired.insert(account.to_string(), count);
    }
    before == 0 && count > 0
}

/// Salami: Wie viele Handys sind mit diesem Konto gekoppelt (letzter bekannter Stand)?
fn paired_devices(account: Uuid) -> u32 {
    read_store(|s| s.paired_devices.get(&account.to_string()).copied().unwrap_or(0))
}

/// Salami: Ungekoppelt bleiben Sitzungen nur lokal liegen – höchstens so viele (älteste fliegen).
const MAX_BUFFERED_SESSIONS: usize = 2000;

/// Salami: Puffer für ungekoppelte Konten begrenzen (offene Sitzungen bleiben immer).
fn trim_buffer(sessions: &mut Vec<PlaySession>) {
    let closed = sessions.iter().filter(|s| !s.open).count();
    if closed <= MAX_BUFFERED_SESSIONS {
        return;
    }
    let mut drop = closed - MAX_BUFFERED_SESSIONS;
    sessions.sort_by_key(|s| s.end);
    sessions.retain(|s| {
        if drop > 0 && !s.open {
            drop -= 1;
            false
        } else {
            true
        }
    });
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

fn clips_path() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join(CLIPS_FILE)
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
/// Nur ein Clip-Abgleich zur Zeit. Wer währenddessen einen will, setzt [`CLIPS_AGAIN`].
static CLIPS_LOCK: Lazy<tokio::sync::Mutex<()>> = Lazy::new(Default::default);
static CLIPS_AGAIN: AtomicBool = AtomicBool::new(false);
/// Der Server hat einen Clip mit 507 abgelehnt: bis zum nächsten Launcher-Start keine Clips mehr.
static CLIPS_FULL: AtomicBool = AtomicBool::new(false);
/// Steigt bei „Daten löschen“; ein laufender Clip-Abgleich bricht dann ab.
static CLIPS_EPOCH: AtomicU64 = AtomicU64::new(0);
/// Zuletzt vom Server genannte Höchstgröße eines Clips (0 = noch unbekannt).
static CLIP_MAX_BYTES: AtomicU64 = AtomicU64::new(0);

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
    /// Die Datei auf der Platte ließ sich nicht lesen oder hat sich gerade geändert.
    Unreadable(String),
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
            Failure::Unreadable(e) => format!("Datei nicht lesbar: {e}"),
        }
    }

    /// Später nochmal versuchen (statt die Daten zu verwerfen)?
    fn is_temporary(&self) -> bool {
        match self {
            Failure::Offline(_)
            | Failure::RateLimited
            | Failure::StorageFull
            | Failure::Unauthorized
            | Failure::Unreadable(_) => true,
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
    // Nur Salami-Anmeldungen unterschreiben, nie die eines echten Minecraft-Servers
    if let Err(e) = crate::integrations::salami_capes::require_salami_server_id(&challenge.server_id) {
        return Err(Failure::Server(StatusCode::OK, e.to_string()));
    }

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
        // Salami: erst senden, wenn ein Handy gekoppelt ist – bis dahin bleiben sie lokal
        if !uploads_allowed(true, paired_devices(account)) {
            log::debug!("[Salami-App] {} session(s) kept locally until a phone is paired", sessions.len());
            continue;
        }
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
        request_clip_sync();
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
            log::warn!("[Salami-App] could not save {}: {}", path.display(), e);
        }
    }

    /// Hash dieser Datei, falls er schon berechnet wurde und Größe und Änderungszeit noch stimmen.
    fn known_hash(&self, key: &str, size: u64, modified: i64) -> Option<String> {
        self.files
            .get(key)
            .filter(|f| f.size == size && f.modified == modified)
            .map(|f| f.sha256.clone())
    }

    fn remember_hash(&mut self, key: String, size: u64, modified: i64, sha256: String) {
        self.files.insert(key, IndexedFile { size, modified, sha256 });
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
    // Salami: ohne gekoppeltes Handy nichts hochladen (und nicht einmal anmelden)
    if !uploads_allowed(sync_enabled().await, paired_devices(account)) {
        return Ok(report);
    }
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
        let known = index.known_hash(&key, shot.size, shot.modified);
        let hash = match known {
            Some(hash) => hash,
            None => {
                let path = shot.path.clone();
                match tokio::task::spawn_blocking(move || sha256_file(&path)).await {
                    Ok(Ok(hash)) => {
                        index.remember_hash(key, shot.size, shot.modified, hash.clone());
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

// ------------------------------------------------------------------ Clips

/// Ein fertiger Clip auf der Platte.
#[derive(Debug, Clone, PartialEq)]
struct Clip {
    path: PathBuf,
    /// Nur gesetzt, wenn der Nutzer den Clip umbenannt hat.
    name: Option<String>,
    game: Option<String>,
    recorded: DateTime<Utc>,
    size: u64,
    /// Änderungszeit in Sekunden (wie `ClipEntry::created_at`).
    modified: i64,
}

/// Name, wie ihn der Launcher selbst vergibt: `<Unix-Zeit>_<Anlass>`, dazu Zähler und die
/// Anhänge fürs Schneiden und Hochkant-Exportieren (`1788803479_clip-2_trimmed`).
fn is_default_clip_name(stem: &str) -> bool {
    let Some((stamp, rest)) = stem.split_once('_') else {
        return false;
    };
    stamp.len() >= 9
        && stamp.bytes().all(|b| b.is_ascii_digit())
        && !rest.is_empty()
        && rest.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

/// Wählt aus der Clip-Bibliothek (neueste zuerst) die Clips, die jetzt hochgeladen werden können:
/// seit [`CLIP_SETTLE_SECS`] unverändert, nicht leer und höchstens `max_bytes` groß (0 = Grenze
/// noch unbekannt). Fremde Videos im Clip-Ordner, die der Launcher nie angefasst hat, bleiben
/// auf dem PC.
fn ready_clips(entries: Vec<crate::utils::clip_library::ClipEntry>, now: i64, max_bytes: u64) -> Vec<Clip> {
    entries
        .into_iter()
        .filter(|entry| entry.managed || is_default_clip_name(&entry.name))
        .filter(|entry| entry.size_bytes > 0 && (max_bytes == 0 || entry.size_bytes <= max_bytes))
        .filter(|entry| entry.created_at > 0 && entry.created_at <= now - CLIP_SETTLE_SECS)
        .filter_map(|entry| {
            Some(Clip {
                recorded: DateTime::from_timestamp(entry.created_at, 0)?,
                name: Some(entry.name).filter(|name| !is_default_clip_name(name)),
                game: entry.game.filter(|game| !game.trim().is_empty()),
                size: entry.size_bytes,
                modified: entry.created_at,
                path: entry.path,
            })
        })
        .collect()
}

/// Antwort auf `POST /v1/sync/clips/check`.
#[derive(Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct ClipCheck {
    missing: Vec<String>,
    #[serde(default)]
    used_bytes: u64,
    #[serde(default)]
    quota_bytes: u64,
    #[serde(default)]
    max_bytes: u64,
}

fn quota_reached(used_bytes: u64, quota_bytes: u64) -> bool {
    quota_bytes > 0 && used_bytes >= quota_bytes
}

/// Was mit einem Clip nach dem `check` geschieht.
#[derive(Debug, PartialEq)]
enum ClipStep {
    /// Schon auf dem Server oder in der App gelöscht: erledigt.
    AlreadyThere,
    /// Größer als der Server annimmt: überspringen, aber nicht als erledigt merken.
    TooBig,
    /// Speicher voll: liegen lassen.
    NoRoom,
    Upload,
}

fn clip_step(size: u64, missing: bool, max_bytes: u64, full: bool) -> ClipStep {
    if !missing {
        ClipStep::AlreadyThere
    } else if max_bytes > 0 && size > max_bytes {
        ClipStep::TooBig
    } else if full {
        ClipStep::NoRoom
    } else {
        ClipStep::Upload
    }
}

/// Wie es nach einem Upload-Versuch weitergeht.
#[derive(Debug, PartialEq)]
enum AfterUpload {
    Uploaded,
    /// 429: kurz warten, denselben Clip nochmal.
    Retry,
    /// 507: bis zum nächsten Launcher-Start keine Clips mehr.
    Full,
    /// Server nicht erreichbar o. Ä.: diese Runde beenden, später weiter.
    Later,
    /// Der Server nimmt diesen Clip nie an (400, 413, 415): als erledigt merken.
    Rejected,
    /// Datei gerade nicht lesbar oder verändert: diesen Clip auslassen, die anderen weiter.
    Skip,
}

fn after_upload(result: &std::result::Result<(), Failure>, attempts: u32) -> AfterUpload {
    match result {
        Ok(()) => AfterUpload::Uploaded,
        Err(Failure::RateLimited) if attempts < 4 => AfterUpload::Retry,
        Err(Failure::StorageFull) => AfterUpload::Full,
        Err(Failure::Unreadable(_)) => AfterUpload::Skip,
        Err(failure) if failure.is_temporary() => AfterUpload::Later,
        Err(_) => AfterUpload::Rejected,
    }
}

/// Wert für die Kopfzeile `X-Game`: nur druckbares ASCII, sonst lieber weglassen.
fn header_text(value: &str) -> Option<&str> {
    let value = value.trim();
    (!value.is_empty() && value.len() <= 100 && value.bytes().all(|b| (0x20..0x7f).contains(&b))).then_some(value)
}

/// Lädt die Datei als Stream hoch (nie ganz im Speicher). Hat sie sich seit dem Hashen geändert,
/// wird nichts gesendet.
async fn put_clip(url: &str, token: &str, clip: &Clip) -> std::result::Result<(), Failure> {
    let unreadable = |e: std::io::Error| Failure::Unreadable(format!("{}: {e}", clip.path.display()));
    let file = tokio::fs::File::open(&clip.path).await.map_err(unreadable)?;
    let meta = file.metadata().await.map_err(unreadable)?;
    let modified = meta
        .modified()
        .ok()
        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64);
    if meta.len() != clip.size || modified != Some(clip.modified) {
        return Err(Failure::Unreadable(format!("{} hat sich geändert", clip.path.display())));
    }
    let mut request = HTTP_CLIENT
        .put(url)
        .timeout(CLIP_UPLOAD_TIMEOUT)
        .bearer_auth(token)
        .header(reqwest::header::CONTENT_TYPE, "video/mp4")
        // der Body ist ein Stream, die Länge steht trotzdem fest
        .header(reqwest::header::CONTENT_LENGTH, clip.size)
        .header("X-Recorded-At", clip.recorded.to_rfc3339_opts(chrono::SecondsFormat::Secs, true));
    if let Some(name) = &clip.name {
        request = request.header("X-Clip-Name", encode_uri_component(name));
    }
    if let Some(game) = clip.game.as_deref().and_then(header_text) {
        request = request.header("X-Game", game);
    }
    read_json::<serde_json::Value>(request.body(reqwest::Body::from(file)).send().await)
        .await
        .map(|_| ())
}

/// Clip-Ordner, wenn Clips hochgeladen werden sollen: Sync an, Schalter „Clips hochladen“ an und
/// (Salami) ein Handy mit dem aktiven Konto gekoppelt.
async fn clip_dir_if_enabled() -> Option<PathBuf> {
    server_url()?;
    let state = State::get().await.ok()?;
    let config = state.config_manager.get_config().await;
    let paired = match active_account_id().await {
        Some(account) => paired_devices(account),
        None => 0,
    };
    clip_uploads_allowed(config.salami_app_sync, config.salami_app_clips, paired)
        .then(|| config.clips.resolved_output_dir())
}

/// Wird fertig, sobald der laufende Abgleich aufhören soll: „Daten löschen“ oder Schalter aus.
async fn clip_sync_stopped(epoch: u64) {
    loop {
        tokio::time::sleep(Duration::from_secs(2)).await;
        if CLIPS_EPOCH.load(Ordering::SeqCst) != epoch || clip_dir_if_enabled().await.is_none() {
            return;
        }
    }
}

/// Ergebnis eines Clip-Abgleichs.
#[derive(Debug, Default, Clone, PartialEq)]
struct ClipReport {
    uploaded: u32,
    already_there: u32,
    too_big: u32,
    storage_full: bool,
}

/// Ein Abgleich: fertige Clips hashen, beim Server nachfragen, fehlende einzeln hochladen.
async fn run_clip_sync() -> std::result::Result<ClipReport, Failure> {
    let mut report = ClipReport::default();
    let Some(dir) = clip_dir_if_enabled().await else {
        return Ok(report);
    };
    if CLIPS_FULL.load(Ordering::SeqCst) {
        report.storage_full = true;
        return Ok(report);
    }
    let Some(account) = active_account_id().await else {
        return Ok(report);
    };
    // Salami: ohne gekoppeltes Handy keine Clips hochladen
    if paired_devices(account) == 0 {
        return Ok(report);
    }
    let base = base()?;
    let epoch = CLIPS_EPOCH.load(Ordering::SeqCst);
    let index_path = clips_path();
    let known_max = CLIP_MAX_BYTES.load(Ordering::SeqCst);

    let listed = tokio::task::spawn_blocking({
        let index_path = index_path.clone();
        move || {
            let entries = crate::utils::clip_library::list(&dir)?;
            let present: HashSet<String> = entries.iter().map(|e| e.path.display().to_string()).collect();
            let mut index = ScreenshotIndex::load(&index_path);
            index.files.retain(|path, _| present.contains(path));
            Ok::<_, AppError>((index, ready_clips(entries, Utc::now().timestamp(), known_max)))
        }
    })
    .await;
    let (mut index, clips) = match listed {
        Ok(Ok(listed)) => listed,
        Ok(Err(e)) => return Err(Failure::Unreadable(e.to_string())),
        Err(e) => return Err(Failure::Unreadable(e.to_string())),
    };

    // Hashes: aus dem Index, sonst die Datei einmal durchlesen
    let mut pending: Vec<(Clip, String)> = Vec::new();
    let mut seen_hashes = HashSet::new();
    let mut hashed = false;
    for clip in clips {
        let key = clip.path.display().to_string();
        let hash = match index.known_hash(&key, clip.size, clip.modified) {
            Some(hash) => hash,
            None => {
                if CLIPS_EPOCH.load(Ordering::SeqCst) != epoch {
                    return Ok(report);
                }
                let path = clip.path.clone();
                match tokio::task::spawn_blocking(move || sha256_file(&path)).await {
                    Ok(Ok(hash)) => {
                        index.remember_hash(key, clip.size, clip.modified, hash.clone());
                        hashed = true;
                        hash
                    }
                    _ => continue,
                }
            }
        };
        if index.done.contains(&hash) || !seen_hashes.insert(hash.clone()) {
            continue;
        }
        pending.push((clip, hash));
    }
    let save = |index: &ScreenshotIndex| {
        if CLIPS_EPOCH.load(Ordering::SeqCst) == epoch {
            index.save(&index_path);
        }
    };
    if hashed {
        save(&index);
    }

    let mut outcome = Ok(());
    'chunks: for chunk in pending.chunks(MAX_HASHES_PER_CHECK) {
        let hashes: Vec<&String> = chunk.iter().map(|(_, hash)| hash).collect();
        let body = serde_json::json!({ "hashes": hashes });
        let checked: std::result::Result<ClipCheck, Failure> = with_token(account, |token| {
            let request = HTTP_CLIENT
                .post(format!("{base}/v1/sync/clips/check"))
                .timeout(Duration::from_secs(20))
                .bearer_auth(token)
                .json(&body);
            async move { read_json(request.send().await).await }
        })
        .await;
        let check = match checked {
            Ok(check) => check,
            Err(failure) => {
                outcome = Err(failure);
                break;
            }
        };
        CLIP_MAX_BYTES.store(check.max_bytes, Ordering::SeqCst);
        let missing: HashSet<&String> = check.missing.iter().collect();
        let full = quota_reached(check.used_bytes, check.quota_bytes);
        report.storage_full |= full;

        for (clip, hash) in chunk {
            match clip_step(clip.size, missing.contains(hash), check.max_bytes, full) {
                ClipStep::AlreadyThere => {
                    index.done.insert(hash.clone());
                    report.already_there += 1;
                    continue;
                }
                ClipStep::TooBig => {
                    report.too_big += 1;
                    continue;
                }
                ClipStep::NoRoom => continue,
                ClipStep::Upload => {}
            }
            let url = format!("{base}/v1/sync/clips/{hash}");
            let mut attempts = 0;
            loop {
                attempts += 1;
                let upload = with_token(account, |token| {
                    let url = &url;
                    async move { put_clip(url, &token, clip).await }
                });
                let result = tokio::select! {
                    result = upload => result,
                    _ = clip_sync_stopped(epoch) => {
                        log::info!("[Salami-App] clip upload stopped");
                        break 'chunks;
                    }
                };
                match after_upload(&result, attempts) {
                    AfterUpload::Uploaded => {
                        index.done.insert(hash.clone());
                        report.uploaded += 1;
                        save(&index);
                        break;
                    }
                    AfterUpload::Retry => tokio::time::sleep(RATE_LIMIT_WAIT).await,
                    AfterUpload::Full => {
                        CLIPS_FULL.store(true, Ordering::SeqCst);
                        report.storage_full = true;
                        break 'chunks;
                    }
                    AfterUpload::Later => {
                        outcome = result;
                        break 'chunks;
                    }
                    AfterUpload::Rejected => {
                        if let Err(failure) = &result {
                            log::warn!(
                                "[Salami-App] clip {} skipped for good: {}",
                                clip.path.display(),
                                failure.message()
                            );
                        }
                        index.done.insert(hash.clone());
                        break;
                    }
                    AfterUpload::Skip => {
                        if let Err(failure) = &result {
                            log::info!("[Salami-App] clip left for later: {}", failure.message());
                        }
                        break;
                    }
                }
            }
        }
    }

    save(&index);
    outcome.map(|_| report)
}

/// Gleicht die Clips mit dem Server ab. Läuft schon ein Abgleich, hängt der eine weitere Runde an
/// (damit ein eben gespeicherter Clip nicht bis zum nächsten Takt wartet).
pub async fn sync_clips() {
    let Ok(_guard) = CLIPS_LOCK.try_lock() else {
        CLIPS_AGAIN.store(true, Ordering::SeqCst);
        return;
    };
    loop {
        CLIPS_AGAIN.store(false, Ordering::SeqCst);
        match run_clip_sync().await {
            Ok(report) if report != ClipReport::default() => log::info!(
                "[Salami-App] clips: {} uploaded, {} already there, {} too big{}",
                report.uploaded,
                report.already_there,
                report.too_big,
                if report.storage_full { ", storage full" } else { "" }
            ),
            Ok(_) => {}
            Err(failure) => log::info!("[Salami-App] clip sync postponed: {}", failure.message()),
        }
        if !CLIPS_AGAIN.swap(false, Ordering::SeqCst) {
            break;
        }
    }
}

/// Stößt den Clip-Abgleich im Hintergrund an. Ein Upload kann lange dauern, darum wartet
/// niemand darauf.
pub fn request_clip_sync() {
    tokio::spawn(sync_clips());
}

/// Ein Clip wurde gespeichert, geschnitten oder exportiert: hochladen, sobald die Datei als
/// fertig gilt.
pub fn clip_saved() {
    tokio::spawn(async {
        tokio::time::sleep(Duration::from_secs(CLIP_SETTLE_SECS as u64 + 5)).await;
        sync_clips().await;
    });
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
    // Salami: Sitzungen nur für gekoppelte Konten (siehe flush_sessions)
    flush_sessions().await;
    if let Some(account) = active_account_id().await {
        // Salami: ohne gekoppeltes Handy nicht einmal die Screenshots durchsuchen
        if paired_devices(account) > 0 {
            if let Err(failure) = sync_all_screenshots(account).await {
                log::info!("[Salami-App] screenshot sync postponed: {}", failure.message());
            }
        }
    }
    request_clip_sync();
}

/// Salami: So oft (in Takten, also Minuten) prüft der Launcher, ob das Handy noch gekoppelt ist.
const PAIRING_CHECK_EVERY: u64 = 60;

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

    // Salami: Kopplung nachprüfen (nach dem Start/Einschalten und dann stündlich) – nur mit schon
    // vorhandenem Token. Hochgeladen wird erst mit gekoppeltem Handy.
    let mut newly_paired = false;
    if just_enabled || round % PAIRING_CHECK_EVERY == 0 {
        if let Some(account) = active_account_id().await {
            newly_paired = refresh_pairing(account).await;
        }
    }

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
        let closed = close_orphans(&mut s.sessions, &running_ids);
        // Salami: bis zum Koppeln bleiben Sitzungen nur lokal – in Grenzen
        trim_buffer(&mut s.sessions);
        closed
    });

    if just_enabled || newly_paired {
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
        request_clip_sync();
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
    // Salami: gerade gekoppelt → jetzt (und erst jetzt) alles nachholen, damit die App nicht leer ist
    if note_devices(account, response.devices.len()) && sync_enabled().await {
        log::info!("[Salami-App] phone paired, syncing now");
        tokio::spawn(sync_everything());
    }
    Ok(response.devices)
}

/// Salami: Geräteliste vom Server merken. → `true`, wenn das Konto damit gerade erst gekoppelt
/// wurde (vorher kein Gerät).
fn note_devices(account: Uuid, count: usize) -> bool {
    let newly_paired = update_store(|s| record_devices(&mut s.paired_devices, &account.to_string(), count));
    if count == 0 {
        log::info!("[Salami-App] no phone paired with this account, nothing is uploaded");
    }
    newly_paired
}

/// Salami: Kopplung im Hintergrund nachprüfen (Handy in der App entkoppelt, Stand nach dem
/// Update) – nur mit schon vorhandenem Token, ohne neue Anmeldung: Wer nie gekoppelt hat, löst
/// so keine Anfrage aus. → `true`, wenn das Konto dabei gerade erst als gekoppelt erkannt wurde.
async fn refresh_pairing(account: Uuid) -> bool {
    let Ok(base) = base() else { return false };
    let Some(token) = read_store(|s| s.tokens.get(&account.to_string()).cloned()) else {
        return false;
    };
    let request = HTTP_CLIENT
        .get(format!("{base}/v1/devices"))
        .timeout(Duration::from_secs(10))
        .bearer_auth(token);
    match read_json::<DevicesResponse>(request.send().await).await {
        Ok(response) => note_devices(account, response.devices.len()),
        // Token abgelaufen: nur wer schon gekoppelt ist, meldet sich dafür neu an (wie beim
        // Hochladen); sonst bleibt das Konto ungekoppelt, bis der Nutzer selbst koppelt
        Err(Failure::Unauthorized) => {
            if paired_devices(account) > 0 {
                if let Err(e) = devices(account).await {
                    log::debug!("[Salami-App] pairing check postponed: {}", e);
                }
            }
            false
        }
        Err(failure) => {
            log::debug!("[Salami-App] pairing check postponed: {}", failure.message());
            false
        }
    }
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
    // Salami: ein Gerät weniger; war es das letzte, hört der Upload sofort auf. Danach den
    // genauen Stand vom Server holen.
    let left = paired_devices(account).saturating_sub(1);
    note_devices(account, left as usize);
    if let Err(e) = devices(account).await {
        log::debug!("[Salami-App] device list after removal not available: {}", e);
    }
    Ok(())
}

/// Löscht alle Daten dieses Kontos auf dem Sync-Server (Spielzeit, Screenshots, Clips, Geräte).
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
/// danach lokal Tokens, Puffer, Screenshot- und Clip-Liste vergessen. Den Schalter stellt der Aufrufer ab.
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
            s.paired_devices.remove(&account.to_string());
        });
    }
    update_store(|s| {
        s.tokens.clear();
        s.sessions.clear();
        // Salami: die Geräte sind mit gelöscht – bis zum nächsten Koppeln nichts mehr hochladen
        s.paired_devices.clear();
    });
    // ein laufender Clip-Upload bricht ab und schreibt seine Liste nicht mehr zurück
    CLIPS_EPOCH.fetch_add(1, Ordering::SeqCst);
    for index_path in [screenshots_path(), clips_path()] {
        if index_path.exists() {
            std::fs::remove_file(&index_path)?;
        }
    }
    *STORAGE_FULL.lock().unwrap_or_else(|e| e.into_inner()) = None;
    CLIPS_FULL.store(false, Ordering::SeqCst);
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
    /// Salami: Mit dem aktiven Konto ist mindestens ein Handy gekoppelt – nur dann lädt der
    /// Launcher etwas hoch (Spielzeit, Screenshots, Clips).
    pub paired: bool,
    pub account_name: Option<String>,
    pub pending_sessions: usize,
    pub storage_full: bool,
    /// Der Clip-Speicher (5 GiB) ist voll; bis zum nächsten Start lädt der Launcher keine Clips.
    pub clips_storage_full: bool,
    pub download_url: String,
    pub privacy_url: String,
}

pub async fn status() -> AppStatus {
    let account = match State::get().await {
        Ok(state) => state.minecraft_account_manager_v2.get_active_account().await.ok().flatten(),
        Err(_) => None,
    };
    let (linked, paired, pending_sessions) = read_store(|s| {
        (
            account.as_ref().is_some_and(|a| s.tokens.contains_key(&a.id.to_string())),
            account
                .as_ref()
                .is_some_and(|a| s.paired_devices.get(&a.id.to_string()).copied().unwrap_or(0) > 0),
            s.sessions.len(),
        )
    });
    AppStatus {
        available: server_url().is_some(),
        enabled: sync_enabled().await,
        linked,
        paired,
        account_name: account.map(|a| a.username),
        pending_sessions,
        storage_full: storage_full_recently(),
        clips_storage_full: CLIPS_FULL.load(Ordering::SeqCst),
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
    fn nothing_is_uploaded_until_a_phone_is_paired() {
        assert!(!uploads_allowed(true, 0));
        assert!(uploads_allowed(true, 1));
        assert!(!uploads_allowed(false, 3));
        assert!(!clip_uploads_allowed(true, true, 0));
        assert!(!clip_uploads_allowed(true, false, 1));
        assert!(!clip_uploads_allowed(false, true, 1));
        assert!(clip_uploads_allowed(true, true, 1));
    }

    #[test]
    fn pairing_comes_only_from_device_lists() {
        // salami_app.json eines älteren Launchers (Sync war an, Token da): gilt als nicht gekoppelt
        let old: AppStore = serde_json::from_str(
            r#"{"install_id":"x","tokens":{"00000000-0000-0000-0000-000000000007":"L.t"},"sessions":[]}"#,
        )
        .unwrap();
        assert!(old.paired_devices.is_empty());

        let mut paired = BTreeMap::new();
        let account = "00000000-0000-0000-0000-000000000007";
        assert!(record_devices(&mut paired, account, 1)); // erstes Handy → jetzt nachholen
        assert!(!record_devices(&mut paired, account, 2)); // weiteres Handy: war schon gekoppelt
        assert_eq!(paired.get(account), Some(&2));
        assert!(!record_devices(&mut paired, account, 0)); // letztes Handy entfernt
        assert!(!paired.contains_key(account));
        assert!(record_devices(&mut paired, account, 1)); // neu gekoppelt → wieder nachholen

        let mut store = old;
        store.paired_devices = paired;
        let again: AppStore = serde_json::from_str(&serde_json::to_string(&store).unwrap()).unwrap();
        assert_eq!(again.paired_devices.get(account), Some(&1));
    }

    #[test]
    fn buffered_sessions_stay_bounded_until_pairing() {
        let mut sessions = Vec::new();
        for i in 0..(MAX_BUFFERED_SESSIONS as i64 + 50) {
            let g = game(Uuid::new_v4(), at(i * 10));
            touch_running(&mut sessions, &g, at(i * 10 + 5));
            close_session(&mut sessions, &g, at(i * 10 + 5));
        }
        let running = game(Uuid::new_v4(), at(0));
        touch_running(&mut sessions, &running, at(1));
        trim_buffer(&mut sessions);
        assert_eq!(sessions.iter().filter(|s| !s.open).count(), MAX_BUFFERED_SESSIONS);
        assert!(sessions.iter().any(|s| s.open), "the running game's session is kept");
        // die ältesten sind weg
        assert!(sessions.iter().filter(|s| !s.open).all(|s| s.end >= at(50 * 10)));
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

    // ---------------------------------------------------------------- Clips

    fn clip_entry(name: &str, size: u64, age_secs: i64, managed: bool) -> crate::utils::clip_library::ClipEntry {
        crate::utils::clip_library::ClipEntry {
            path: PathBuf::from(format!("{name}.mp4")),
            name: name.into(),
            size_bytes: size,
            created_at: NOW - age_secs,
            duration_seconds: Some(30.0),
            game: Some("Minecraft".into()),
            thumbnail: None,
            favourite: false,
            managed,
        }
    }

    const NOW: i64 = 1_790_000_000;

    #[test]
    fn launcher_names_are_told_from_renamed_clips() {
        for name in ["1788803479_clip", "1788803479_clip-2", "1787099385_clip_trimmed", "1787099385_clip_vertical2"] {
            assert!(is_default_clip_name(name), "{name}");
        }
        for name in ["insane clutch", "keeper", "2024_urlaub", "1788803479", "1788803479_", "1788803479_bed rush"] {
            assert!(!is_default_clip_name(name), "{name}");
        }
    }

    #[test]
    fn only_finished_clips_are_picked() {
        let clips = ready_clips(
            vec![
                clip_entry("1789999990_clip", 10, 5, true),   // wird gerade geschrieben
                clip_entry("1789999900_clip", 10, 29, true),  // noch nicht 30 s ruhig
                clip_entry("1789999800_clip", 10, 30, true),  // fertig
                clip_entry("insane clutch", 20, 3_600, true), // fertig, umbenannt
                clip_entry("empty", 0, 3_600, true),
            ],
            NOW,
            0,
        );
        let names: Vec<_> = clips.iter().map(|c| c.path.display().to_string()).collect();
        assert_eq!(names, ["1789999800_clip.mp4", "insane clutch.mp4"], "newest first, order kept");
        assert_eq!(clips[0].name, None, "the launcher's own name is not sent");
        assert_eq!(clips[1].name.as_deref(), Some("insane clutch"));
        assert_eq!(clips[0].game.as_deref(), Some("Minecraft"));
        assert_eq!(clips[0].recorded.timestamp(), NOW - 30);
        assert_eq!((clips[1].size, clips[1].modified), (20, NOW - 3_600));
    }

    #[test]
    fn foreign_videos_in_the_clip_folder_stay_on_the_pc() {
        let clips = ready_clips(
            vec![
                clip_entry("holiday-2019", 10, 3_600, false),
                // ohne Infodatei, aber eindeutig vom Launcher geschrieben (z. B. Hochkant-Export)
                clip_entry("1787099385_clip_vertical", 10, 3_600, false),
            ],
            NOW,
            0,
        );
        assert_eq!(clips.len(), 1);
        assert_eq!(clips[0].path, PathBuf::from("1787099385_clip_vertical.mp4"));
    }

    #[test]
    fn clips_over_the_known_limit_are_left_out_before_hashing() {
        let entries = || vec![clip_entry("1789990000_clip", 601, 100, true), clip_entry("1789980000_clip", 600, 100, true)];
        assert_eq!(ready_clips(entries(), NOW, 0).len(), 2, "limit not known yet");
        let clips = ready_clips(entries(), NOW, 600);
        assert_eq!(clips.len(), 1);
        assert_eq!(clips[0].size, 600);
    }

    #[test]
    fn hash_cache_is_keyed_by_path_size_and_mtime() {
        let dir = std::env::temp_dir().join(format!("salami-app-clips-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let clip = dir.join("a.mp4");
        std::fs::write(&clip, b"abc").unwrap();
        let hash = sha256_file(&clip).unwrap();
        assert_eq!(hash, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        let mut index = ScreenshotIndex::default();
        index.remember_hash("a.mp4".into(), 3, 100, hash.clone());
        assert_eq!(index.known_hash("a.mp4", 3, 100), Some(hash.clone()));
        assert_eq!(index.known_hash("a.mp4", 4, 100), None, "size changed");
        assert_eq!(index.known_hash("a.mp4", 3, 101), None, "mtime changed");
        assert_eq!(index.known_hash("b.mp4", 3, 100), None, "other file");

        // übersteht Speichern und Laden, zusammen mit den erledigten Hashes
        index.done.insert(hash.clone());
        let path = dir.join(CLIPS_FILE);
        index.save(&path);
        let loaded = ScreenshotIndex::load(&path);
        assert_eq!(loaded.known_hash("a.mp4", 3, 100), Some(hash.clone()));
        assert!(loaded.done.contains(&hash));
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn check_answer_matches_contract() {
        let check: ClipCheck = serde_json::from_str(
            r#"{"missing":["ab"],"usedBytes":1048576,"quotaBytes":5368709120,"maxBytes":629145600}"#,
        )
        .unwrap();
        assert_eq!(check.missing, ["ab"]);
        assert_eq!(check.quota_bytes, 5 * 1024 * 1024 * 1024);
        assert_eq!(check.max_bytes, 600 * 1024 * 1024);
        assert!(!quota_reached(check.used_bytes, check.quota_bytes));
        // ältere Server ohne Speicherangaben: keine Grenze annehmen
        let bare: ClipCheck = serde_json::from_str(r#"{"missing":[]}"#).unwrap();
        assert!(!quota_reached(bare.used_bytes, bare.quota_bytes));
    }

    #[test]
    fn quota_and_size_limit_decide_what_is_uploaded() {
        assert_eq!(clip_step(10, false, 100, false), ClipStep::AlreadyThere);
        assert_eq!(clip_step(500, false, 100, true), ClipStep::AlreadyThere, "known to the server wins");
        assert_eq!(clip_step(100, true, 100, false), ClipStep::Upload, "exactly the limit is fine");
        assert_eq!(clip_step(101, true, 100, false), ClipStep::TooBig);
        assert_eq!(clip_step(101, true, 0, false), ClipStep::Upload, "no limit known");
        assert_eq!(clip_step(10, true, 100, true), ClipStep::NoRoom);
        assert!(quota_reached(5, 5));
        assert!(quota_reached(6, 5));
        assert!(!quota_reached(4, 5));
        assert!(!quota_reached(4, 0));
    }

    #[test]
    fn upload_failures_lead_to_the_right_next_step() {
        let server = |code: u16| Err(Failure::Server(StatusCode::from_u16(code).unwrap(), "x".into()));
        assert_eq!(after_upload(&Ok(()), 1), AfterUpload::Uploaded);
        assert_eq!(after_upload(&Err(Failure::RateLimited), 1), AfterUpload::Retry);
        assert_eq!(after_upload(&Err(Failure::RateLimited), 4), AfterUpload::Later, "not forever");
        assert_eq!(after_upload(&Err(Failure::StorageFull), 1), AfterUpload::Full);
        for code in [400, 413, 415] {
            assert_eq!(after_upload(&server(code), 1), AfterUpload::Rejected, "{code}");
        }
        assert_eq!(after_upload(&server(502), 1), AfterUpload::Later);
        assert_eq!(after_upload(&Err(Failure::Offline("x".into())), 1), AfterUpload::Later);
        assert_eq!(after_upload(&Err(Failure::Unauthorized), 1), AfterUpload::Later);
        assert_eq!(after_upload(&Err(Failure::Unreadable("x".into())), 1), AfterUpload::Skip);
    }

    #[test]
    fn game_header_is_plain_ascii_or_absent() {
        assert_eq!(header_text(" Minecraft 1.21 "), Some("Minecraft 1.21"));
        assert_eq!(header_text("Räuber"), None);
        assert_eq!(header_text("a\nb"), None);
        assert_eq!(header_text("  "), None);
    }

    /// Winziger HTTP-Server: nimmt eine Anfrage an, antwortet mit `status` und gibt Kopf und
    /// Body der Anfrage zurück.
    async fn one_request(status: &'static str) -> (String, tokio::task::JoinHandle<(String, Vec<u8>)>) {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let handle = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            let mut data = Vec::new();
            let mut buffer = [0u8; 8192];
            let (head, body_start) = loop {
                let n = socket.read(&mut buffer).await.unwrap();
                assert!(n > 0, "connection closed before the head was complete");
                data.extend_from_slice(&buffer[..n]);
                if let Some(end) = data.windows(4).position(|w| w == b"\r\n\r\n") {
                    break (String::from_utf8_lossy(&data[..end]).to_lowercase(), end + 4);
                }
            };
            let length: usize = head
                .lines()
                .find_map(|line| line.strip_prefix("content-length:"))
                .map(|v| v.trim().parse().unwrap())
                .unwrap_or(0);
            while data.len() < body_start + length {
                let n = socket.read(&mut buffer).await.unwrap();
                if n == 0 {
                    break;
                }
                data.extend_from_slice(&buffer[..n]);
            }
            let body = r#"{"ok":true}"#;
            let answer = format!("HTTP/1.1 {status}\r\ncontent-type: application/json\r\ncontent-length: {}\r\nconnection: close\r\n\r\n{body}", body.len());
            socket.write_all(answer.as_bytes()).await.unwrap();
            let _ = socket.shutdown().await;
            (head, data[body_start..].to_vec())
        });
        (url, handle)
    }

    fn clip_on_disk(tag: &str, bytes: &[u8], name: Option<&str>) -> (PathBuf, Clip) {
        let dir = std::env::temp_dir().join(format!("salami-app-put-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("clip.mp4");
        std::fs::write(&path, bytes).unwrap();
        let modified = std::fs::metadata(&path)
            .unwrap()
            .modified()
            .unwrap()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        let clip = Clip {
            path,
            name: name.map(str::to_string),
            game: Some("Minecraft".into()),
            recorded: at(0),
            size: bytes.len() as u64,
            modified,
        };
        (dir, clip)
    }

    #[tokio::test]
    async fn a_clip_is_streamed_with_its_length_and_headers() {
        // größer als ein Lesepuffer, damit der Body in mehreren Stücken kommt
        let bytes: Vec<u8> = (0..300_000u32).map(|i| (i % 251) as u8).collect();
        let (dir, clip) = clip_on_disk("ok", &bytes, Some("Größe & Spaß"));
        let (url, server) = one_request("200 OK").await;

        put_clip(&format!("{url}/v1/sync/clips/abc"), "L.test", &clip).await.unwrap();

        let (head, body) = server.await.unwrap();
        assert!(head.starts_with("put /v1/sync/clips/abc "), "{head}");
        assert!(head.contains("content-length: 300000"), "{head}");
        assert!(!head.contains("transfer-encoding"), "{head}");
        assert!(head.contains("content-type: video/mp4"), "{head}");
        assert!(head.contains("authorization: bearer l.test"), "{head}");
        assert!(head.contains("x-recorded-at: 2026-09-21t"), "{head}");
        assert!(head.contains("x-clip-name: gr%c3%b6%c3%9fe%20%26%20spa%c3%9f"), "{head}");
        assert!(head.contains("x-game: minecraft"), "{head}");
        assert_eq!(body, bytes);
        let _ = std::fs::remove_dir_all(dir);
    }

    #[tokio::test]
    async fn a_full_server_is_reported_as_storage_full() {
        let (dir, clip) = clip_on_disk("full", b"mp4", None);
        let (url, server) = one_request("507 Insufficient Storage").await;

        let result = put_clip(&format!("{url}/v1/sync/clips/abc"), "L.test", &clip).await;

        assert!(matches!(result, Err(Failure::StorageFull)), "{result:?}");
        let (head, _) = server.await.unwrap();
        assert!(!head.contains("x-clip-name"), "an unrenamed clip sends no name: {head}");
        let _ = std::fs::remove_dir_all(dir);
    }

    #[tokio::test]
    async fn a_clip_that_changed_since_hashing_is_not_sent() {
        let (dir, mut clip) = clip_on_disk("changed", b"mp4", None);
        clip.size += 1;
        // kein Server: die Anfrage darf gar nicht erst rausgehen
        let result = put_clip("http://127.0.0.1:9/v1/sync/clips/abc", "L.test", &clip).await;
        assert!(matches!(result, Err(Failure::Unreadable(_))), "{result:?}");
        let _ = std::fs::remove_dir_all(dir);
    }
}
