//! Salami: Freunde und Chat im Launcher.
//!
//! Spricht mit `server/capes/friends.mjs` auf dem Cape-Server (dieselbe API wie salami-core im
//! Spiel): Freundesliste, Anfragen, Nachrichten, Bilder und Clips. Solange die Funktion
//! eingeschaltet ist, hält der Launcher eine Live-Verbindung (`/v1/friends/events?client=launcher`)
//! – dadurch sehen Freunde „online (im Launcher)“ und Nachrichten kommen sofort an.
//!
//! Anmeldung wie bei den Capes ([`salami_capes::authenticate`]): das Zugangstoken geht nur an
//! Mojang, der Freunde-Server gibt ein eigenes Token zurück (30 Tage gültig). Es liegt zusammen
//! mit dem Ein/Aus-Schalter in `salami_friends/friends_session.key`; die Endung `.key` sperrt
//! das Asset-Protokoll, die Oberfläche kann die Datei also nicht lesen.
//!
//! Ausgeschaltet (Standard) passiert nichts: keine Anmeldung, keine Verbindung.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::Duration;

use log::{debug, info, warn};
use once_cell::sync::{Lazy, OnceCell};
use reqwest::{Method, StatusCode};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::{AppHandle, Emitter};
use tokio::sync::{Mutex, Notify};
use uuid::Uuid;

use crate::config::{ProjectDirsExt, HTTP_CLIENT, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use crate::integrations::salami_capes;
use crate::state::state_manager::State;

/// Live-Ereignis des Servers, unverändert an die Oberfläche (Payload: das JSON des Ereignisses).
pub const EVENT: &str = "salami-friends-event";
/// Verbindung steht oder ist weg, Payload `bool`.
pub const CONNECTION_EVENT: &str = "salami-friends-connection";

const STORE_FILE: &str = "friends_session.key";
const REQUEST_TIMEOUT: Duration = Duration::from_secs(15);
const TRANSFER_TIMEOUT: Duration = Duration::from_secs(600);
/// Der Server schickt alle 20 s ein Lebenszeichen; bleibt so lange alles still, ist die Verbindung tot.
const STREAM_SILENCE: Duration = Duration::from_secs(60);
const RETRY_MIN: Duration = Duration::from_secs(2);
const RETRY_MAX: Duration = Duration::from_secs(60);
/// Grenzen wie auf dem Server (`FRIENDS_IMAGE_MB`, `FRIENDS_CLIP_MB`), damit zu Großes gar nicht erst hochlädt.
const MAX_IMAGE_BYTES: u64 = 10 * 1024 * 1024;
const MAX_CLIP_BYTES: u64 = 50 * 1024 * 1024;

/// Dateiendung → (Content-Type, ist Clip)
const MEDIA_TYPES: [(&str, &str, bool); 6] = [
    ("png", "image/png", false),
    ("jpg", "image/jpeg", false),
    ("jpeg", "image/jpeg", false),
    ("webp", "image/webp", false),
    ("mp4", "video/mp4", true),
    ("webm", "video/webm", true),
];

#[derive(Serialize, Deserialize, Default, Clone)]
struct Store {
    #[serde(default)]
    enabled: bool,
    /// Account, zu dem das Token gehört
    #[serde(default)]
    account: Option<Uuid>,
    #[serde(default)]
    token: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FriendsState {
    /// Ein Server ist eingerichtet (`branding::CAPE_SERVER_URL`).
    pub available: bool,
    pub enabled: bool,
    pub connected: bool,
    /// Im Launcher ist ein Minecraft-Account angemeldet.
    pub signed_in: bool,
}

static APP: OnceCell<AppHandle> = OnceCell::new();
/// Nur eine Anmeldung zur Zeit, sonst holen gleichzeitige Anfragen mehrere Tokens.
static LOGIN: Lazy<Mutex<()>> = Lazy::new(|| Mutex::new(()));
static CONNECTED: AtomicBool = AtomicBool::new(false);
/// Zählt bei jedem Neustart der Live-Verbindung hoch; ältere Verbindungen beenden sich.
static GENERATION: AtomicU64 = AtomicU64::new(0);
static STOP: Lazy<Notify> = Lazy::new(Notify::new);

fn dir() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join("salami_friends")
}

fn media_dir() -> PathBuf {
    dir().join("media")
}

fn load() -> Store {
    std::fs::read(dir().join(STORE_FILE))
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

fn save(store: &Store) -> Result<()> {
    std::fs::create_dir_all(dir()).map_err(AppError::Io)?;
    let bytes = serde_json::to_vec(store).map_err(|e| AppError::Other(format!("Freunde: {e}")))?;
    std::fs::write(dir().join(STORE_FILE), bytes).map_err(AppError::Io)
}

fn base_url() -> Result<String> {
    salami_capes::server_url().ok_or_else(|| AppError::Other("Kein Freunde-Server eingerichtet".into()))
}

fn unreachable(e: reqwest::Error) -> AppError {
    AppError::Other(format!("Freunde-Server nicht erreichbar: {e}"))
}

async fn active_account() -> Result<crate::minecraft::auth::minecraft_auth::Credentials> {
    State::get()
        .await?
        .minecraft_account_manager_v2
        .get_active_account()
        .await?
        .ok_or_else(|| AppError::Other("Für Freunde bitte im Launcher mit einem Minecraft-Account anmelden".into()))
}

/// Fehlertext des Servers (`{"error": "..."}`), sonst der Statuscode.
async fn server_error(response: reqwest::Response) -> AppError {
    let status = response.status();
    let message = response
        .json::<Value>()
        .await
        .ok()
        .and_then(|body| body.get("error").and_then(Value::as_str).map(str::to_string))
        .unwrap_or_else(|| format!("Freunde-Server antwortet mit {status}"));
    AppError::Other(message)
}

/// Token für den aktiven Account: das gespeicherte, sonst (oder mit `renew`) ein neues.
async fn token(renew: bool) -> Result<String> {
    let _guard = LOGIN.lock().await;
    let account = active_account().await?;
    let mut store = load();
    if !renew && store.account == Some(account.id) {
        if let Some(token) = store.token.clone() {
            return Ok(token);
        }
    }
    let base = base_url()?;
    let server_id = salami_capes::authenticate(&base, account.id, &account.access_token).await?;
    let response = HTTP_CLIENT
        .post(format!("{base}/v1/friends/session"))
        .timeout(REQUEST_TIMEOUT)
        .json(&json!({ "name": account.username, "serverId": server_id }))
        .send()
        .await
        .map_err(unreachable)?;
    if !response.status().is_success() {
        return Err(server_error(response).await);
    }
    let body: Value = response.json().await.map_err(unreachable)?;
    let token = body
        .get("token")
        .and_then(Value::as_str)
        .ok_or_else(|| AppError::Other("Antwort des Freunde-Servers unlesbar".into()))?
        .to_string();
    store.account = Some(account.id);
    store.token = Some(token.clone());
    save(&store)?;
    info!("[Friends] Signed in as {}", account.username);
    Ok(token)
}

/// Angemeldete Anfrage. Ist das Token abgelaufen (401), meldet sich der Launcher einmal neu an.
async fn call(method: Method, path: &str, query: &[(&str, &str)], body: Option<Value>) -> Result<Value> {
    let base = base_url()?;
    for attempt in 0..2 {
        let token = token(attempt == 1).await?;
        let mut request = HTTP_CLIENT
            .request(method.clone(), format!("{base}{path}"))
            .timeout(REQUEST_TIMEOUT)
            .bearer_auth(token)
            .query(query);
        if let Some(body) = &body {
            request = request.json(body);
        }
        let response = request.send().await.map_err(unreachable)?;
        if response.status() == StatusCode::UNAUTHORIZED && attempt == 0 {
            continue;
        }
        if !response.status().is_success() {
            return Err(server_error(response).await);
        }
        return response
            .json()
            .await
            .map_err(|e| AppError::Other(format!("Antwort des Freunde-Servers unlesbar: {e}")));
    }
    Err(AppError::Other("Anmeldung beim Freunde-Server fehlgeschlagen".into()))
}

pub async fn overview() -> Result<Value> {
    call(Method::GET, "/v1/friends", &[], None).await
}

pub async fn request(name: &str) -> Result<Value> {
    call(Method::POST, "/v1/friends/request", &[], Some(json!({ "name": name.trim() }))).await
}

/// `action`: `accept`, `deny` (auch: eigene Anfrage zurückziehen) oder `remove`.
pub async fn respond(action: &str, uuid: &str) -> Result<Value> {
    if !["accept", "deny", "remove"].contains(&action) {
        return Err(AppError::Other(format!("Unbekannte Aktion: {action}")));
    }
    call(Method::POST, &format!("/v1/friends/{action}"), &[], Some(json!({ "uuid": uuid }))).await
}

pub async fn messages(with: &str) -> Result<Value> {
    call(Method::GET, "/v1/friends/messages", &[("with", with)], None).await
}

pub async fn send(to: &str, text: &str) -> Result<Value> {
    call(Method::POST, "/v1/friends/messages", &[], Some(json!({ "to": to, "text": text }))).await
}

pub async fn mark_read(with: &str) -> Result<Value> {
    call(Method::POST, "/v1/friends/read", &[], Some(json!({ "with": with }))).await
}

fn media_type_of(path: &Path) -> Option<(&'static str, bool)> {
    let extension = path.extension()?.to_str()?.to_ascii_lowercase();
    MEDIA_TYPES
        .iter()
        .find(|(ext, _, _)| *ext == extension)
        .map(|(_, mime, clip)| (*mime, *clip))
}

/// Schickt ein Bild (PNG, JPEG, WebP) oder einen Clip (MP4, WebM) an einen Freund.
pub async fn send_file(to: &str, path: &Path) -> Result<Value> {
    let (mime, clip) = media_type_of(path)
        .ok_or_else(|| AppError::Other("Nur PNG, JPEG, WebP, MP4 und WebM lassen sich verschicken".into()))?;
    let size = tokio::fs::metadata(path).await.map_err(AppError::Io)?.len();
    let limit = if clip { MAX_CLIP_BYTES } else { MAX_IMAGE_BYTES };
    if size == 0 {
        return Err(AppError::Other("Die Datei ist leer".into()));
    }
    if size > limit {
        return Err(AppError::Other(format!(
            "{} ist zu groß (höchstens {} MB)",
            if clip { "Der Clip" } else { "Das Bild" },
            limit / (1024 * 1024)
        )));
    }
    let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("datei").to_string();
    let base = base_url()?;
    for attempt in 0..2 {
        let token = token(attempt == 1).await?;
        let bytes = tokio::fs::read(path).await.map_err(AppError::Io)?;
        let response = HTTP_CLIENT
            .post(format!("{base}/v1/friends/media"))
            .timeout(TRANSFER_TIMEOUT)
            .bearer_auth(token)
            .query(&[("to", to), ("name", name.as_str())])
            .header(reqwest::header::CONTENT_TYPE, mime)
            .body(bytes)
            .send()
            .await
            .map_err(unreachable)?;
        if response.status() == StatusCode::UNAUTHORIZED && attempt == 0 {
            continue;
        }
        if !response.status().is_success() {
            return Err(server_error(response).await);
        }
        return response
            .json()
            .await
            .map_err(|e| AppError::Other(format!("Antwort des Freunde-Servers unlesbar: {e}")));
    }
    Err(AppError::Other("Anmeldung beim Freunde-Server fehlgeschlagen".into()))
}

/// Medien-IDs des Servers: 32 Hex-Zeichen. Alles andere wird nie Teil von URL oder Pfad.
fn is_media_id(id: &str) -> bool {
    id.len() == 32 && id.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

/// Lädt ein Bild oder einen Clip aus einem Gespräch und legt es im Launcher-Ordner ab
/// (einmal geladen, bleibt es dort). → Pfad der Datei
pub async fn media_file(id: &str, mime: &str) -> Result<PathBuf> {
    if !is_media_id(id) {
        return Err(AppError::Other(format!("Ungültige Medien-ID: {id}")));
    }
    let extension = MEDIA_TYPES
        .iter()
        .find(|(_, known, _)| *known == mime)
        .map(|(ext, _, _)| *ext)
        .ok_or_else(|| AppError::Other(format!("Unbekannter Dateityp: {mime}")))?;
    let folder = media_dir();
    let file = folder.join(format!("{id}.{extension}"));
    if tokio::fs::try_exists(&file).await.unwrap_or(false) {
        return Ok(file);
    }
    tokio::fs::create_dir_all(&folder).await.map_err(AppError::Io)?;
    let base = base_url()?;
    for attempt in 0..2 {
        let token = token(attempt == 1).await?;
        let response = HTTP_CLIENT
            .get(format!("{base}/v1/friends/media/{id}"))
            .timeout(TRANSFER_TIMEOUT)
            .bearer_auth(token)
            .send()
            .await
            .map_err(unreachable)?;
        if response.status() == StatusCode::UNAUTHORIZED && attempt == 0 {
            continue;
        }
        if !response.status().is_success() {
            return Err(server_error(response).await);
        }
        let bytes = response.bytes().await.map_err(unreachable)?;
        if bytes.len() as u64 > MAX_CLIP_BYTES {
            return Err(AppError::Other("Die Datei ist größer als erlaubt".into()));
        }
        let part = folder.join(format!("{id}.part"));
        tokio::fs::write(&part, &bytes).await.map_err(AppError::Io)?;
        tokio::fs::rename(&part, &file).await.map_err(AppError::Io)?;
        return Ok(file);
    }
    Err(AppError::Other("Anmeldung beim Freunde-Server fehlgeschlagen".into()))
}

// ---------------------------------------------------------------- Zustand und Live-Verbindung

pub async fn state() -> FriendsState {
    let signed_in = match State::get().await {
        Ok(state) => matches!(state.minecraft_account_manager_v2.get_active_account().await, Ok(Some(_))),
        Err(_) => false,
    };
    FriendsState {
        available: salami_capes::server_url().is_some(),
        enabled: load().enabled,
        connected: CONNECTED.load(Ordering::SeqCst),
        signed_in,
    }
}

fn set_connected(connected: bool) {
    if CONNECTED.swap(connected, Ordering::SeqCst) != connected {
        if let Some(app) = APP.get() {
            let _ = app.emit(CONNECTION_EVENT, connected);
        }
    }
}

/// Schaltet Freunde ein oder aus. Aus trennt die Verbindung sofort (Freunde sehen „offline“,
/// sofern das Spiel nicht läuft); das Token bleibt für das nächste Einschalten liegen.
pub fn set_enabled(enabled: bool) -> Result<()> {
    let mut store = load();
    store.enabled = enabled;
    save(&store)?;
    if enabled {
        connect();
    } else {
        disconnect();
    }
    Ok(())
}

/// Beim Start des Launchers: verbindet, wenn Freunde eingeschaltet sind.
pub fn start(app: AppHandle) {
    let _ = APP.set(app);
    if salami_capes::server_url().is_some() && load().enabled {
        connect();
    }
}

/// Live-Verbindung neu aufbauen – auch nach einem Account-Wechsel.
pub fn connect() {
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    STOP.notify_waiters();
    tauri::async_runtime::spawn(async move {
        let mut delay = RETRY_MIN;
        while GENERATION.load(Ordering::SeqCst) == generation {
            match stream(generation).await {
                Ok(()) => delay = RETRY_MIN,
                Err(e) => debug!("[Friends] Live connection lost: {e}"),
            }
            if GENERATION.load(Ordering::SeqCst) != generation {
                break;
            }
            set_connected(false);
            tokio::time::sleep(delay).await;
            delay = (delay * 2).min(RETRY_MAX);
        }
    });
}

pub fn disconnect() {
    GENERATION.fetch_add(1, Ordering::SeqCst);
    STOP.notify_waiters();
    set_connected(false);
}

/// Hält die Live-Verbindung, bis sie abbricht oder eine neuere sie ablöst.
async fn stream(generation: u64) -> Result<()> {
    let base = base_url()?;
    let mut response = None;
    for attempt in 0..2 {
        let token = token(attempt == 1).await?;
        let candidate = HTTP_CLIENT
            .get(format!("{base}/v1/friends/events"))
            .query(&[("client", "launcher")])
            .bearer_auth(token)
            .send()
            .await
            .map_err(unreachable)?;
        if candidate.status() == StatusCode::UNAUTHORIZED && attempt == 0 {
            continue;
        }
        if !candidate.status().is_success() {
            return Err(server_error(candidate).await);
        }
        response = Some(candidate);
        break;
    }
    let mut response = response.ok_or_else(|| AppError::Other("Anmeldung beim Freunde-Server fehlgeschlagen".into()))?;

    let mut buffer: Vec<u8> = Vec::new();
    loop {
        let chunk = tokio::select! {
            _ = STOP.notified() => return Ok(()),
            chunk = tokio::time::timeout(STREAM_SILENCE, response.chunk()) => chunk,
        };
        if GENERATION.load(Ordering::SeqCst) != generation {
            return Ok(());
        }
        let bytes = match chunk {
            Ok(Ok(Some(bytes))) => bytes,
            Ok(Ok(None)) => return Ok(()),
            Ok(Err(e)) => return Err(unreachable(e)),
            Err(_) => return Err(AppError::Other("keine Lebenszeichen mehr".into())),
        };
        buffer.extend_from_slice(&bytes);
        // Ereignisse sind durch eine Leerzeile getrennt; Zeilen ohne „data: “ sind Lebenszeichen.
        while let Some(end) = buffer.windows(2).position(|w| w == b"\n\n") {
            let block: Vec<u8> = buffer.drain(..end + 2).collect();
            for line in String::from_utf8_lossy(&block).lines() {
                let Some(data) = line.strip_prefix("data: ") else { continue };
                let Ok(event) = serde_json::from_str::<Value>(data) else {
                    warn!("[Friends] Unreadable live event");
                    continue;
                };
                if event.get("type").and_then(Value::as_str) == Some("ready") {
                    set_connected(true);
                }
                if let Some(app) = APP.get() {
                    let _ = app.emit(EVENT, event);
                }
            }
        }
        if buffer.len() > 1024 * 1024 {
            return Err(AppError::Other("Live-Ereignis zu groß".into()));
        }
    }
}
