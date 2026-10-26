//! Salami Capes: eigene Capes (PNG im Minecraft-Cape-Layout) und die Auswahl, die beim
//! Spielstart als `-Dsalami.cape=…` an salami-core geht.
//!
//! Die Bilder liegen lokal im Launcher-Ordner (`salami_capes/`). Mit anderen Salami-Spielern
//! teilt salami-core das gewählte Cape über den Cape-Server (`branding::CAPE_SERVER_URL`,
//! Code in `server/capes/`); eigene Bilder sehen andere erst nach Freigabe durch einen Admin.
//! Der Launcher fragt dort den Prüfstatus ab ([`review_status`]) und reicht neue eigene Capes
//! gleich zur Prüfung ein ([`submit_for_review`]), nicht erst wenn man damit spielt.

use crate::config::{ProjectDirsExt, HTTP_CLIENT, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use base64::Engine;
use image::{GenericImageView, ImageFormat};
use once_cell::sync::Lazy;
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::io::Cursor;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use uuid::Uuid;

/// Mitgelieferte Capes (Dateinamen in salami-core und `launcher/public/capes`).
pub const BUILTIN_CAPES: [&str; 3] = ["salami", "pepperoni", "board"];
pub const BUILTIN_PREFIX: &str = "builtin:";
const DEFAULT_SELECTION: &str = "builtin:salami";
const STORE_FILE: &str = "capes.json";
/// Vorlage zum Selbermalen (erzeugt von `branding/generate_capes.py`).
pub const TEMPLATE_PNG: &[u8] = include_bytes!("../../assets/salami_cape_template.png");
const MAX_WIDTH: u32 = 2048;
const MAX_NAME_CHARS: usize = 40;
const MOJANG_JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";
/// Nach einem Einreichversuch (offline, Tageslimit …) so lange nicht erneut versuchen.
const SUBMIT_RETRY: Duration = Duration::from_secs(10 * 60);

/// share_id → letzter Einreichversuch in diesem Launcher-Lauf.
static SUBMIT_ATTEMPTS: Lazy<Mutex<HashMap<String, Instant>>> = Lazy::new(Default::default);

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct StoredCape {
    pub id: String,
    pub name: String,
    pub created: String,
}

/// Inhalt von `capes.json`. `selected`: `builtin:<name>`, die ID eines eigenen Capes
/// oder `None` = kein Cape.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct CapeStore {
    pub selected: Option<String>,
    #[serde(default)]
    pub capes: Vec<StoredCape>,
}

impl Default for CapeStore {
    fn default() -> Self {
        Self { selected: Some(DEFAULT_SELECTION.to_string()), capes: Vec::new() }
    }
}

/// Eigenes Cape fürs Frontend, Bild als Data-URL.
#[derive(Serialize, Debug, Clone)]
pub struct CapeView {
    pub id: String,
    pub name: String,
    pub created: String,
    pub image: String,
    /// ID des Bildes auf dem Cape-Server ([`share_id`]), für den Prüfstatus.
    pub share_id: String,
}

/// Prüfstatus eines eigenen Capes auf dem Cape-Server.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct CapeReview {
    /// `pending`, `approved` oder `rejected`.
    pub status: String,
    /// Begründung des Admins bei `rejected`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

#[derive(Serialize, Debug)]
pub struct CapeOverview {
    pub selected: Option<String>,
    pub builtin: Vec<String>,
    pub capes: Vec<CapeView>,
}

pub fn capes_dir() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join("salami_capes")
}

/// IDs sind UUIDs; alles andere (z. B. `..\\`) darf nie zu einem Pfad werden.
fn is_valid_cape_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
}

fn png_path(dir: &Path, id: &str) -> PathBuf {
    dir.join(format!("{id}.png"))
}

impl CapeStore {
    pub fn load(dir: &Path) -> CapeStore {
        std::fs::read(dir.join(STORE_FILE))
            .ok()
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default()
    }

    fn save(&self, dir: &Path) -> Result<()> {
        std::fs::create_dir_all(dir)?;
        let tmp = dir.join(format!("{STORE_FILE}.tmp"));
        std::fs::write(&tmp, serde_json::to_vec_pretty(self)?)?;
        std::fs::rename(tmp, dir.join(STORE_FILE))?;
        Ok(())
    }

    fn find(&self, id: &str) -> Option<&StoredCape> {
        self.capes.iter().find(|c| c.id == id)
    }
}

/// Prüft ein Bild auf Cape-Format (Breite = 2 × Höhe, 64 bis 2048 px, Vielfaches von 64)
/// und speichert es neu als PNG – ohne Metadaten.
pub fn normalize_cape_png(bytes: &[u8]) -> Result<Vec<u8>> {
    let image = image::load_from_memory(bytes)
        .map_err(|e| AppError::Other(format!("Bild konnte nicht gelesen werden: {e}")))?;
    let (w, h) = image.dimensions();
    if w != h * 2 || w < 64 || w > MAX_WIDTH || w % 64 != 0 {
        return Err(AppError::Other(format!(
            "Kein Cape-Format: {w}x{h}. Erlaubt sind 64x32, 128x64 … bis {MAX_WIDTH}x{} (Breite = 2 × Höhe).",
            MAX_WIDTH / 2
        )));
    }
    let mut out = Cursor::new(Vec::new());
    image
        .to_rgba8()
        .write_to(&mut out, ImageFormat::Png)
        .map_err(|e| AppError::Other(format!("PNG konnte nicht geschrieben werden: {e}")))?;
    Ok(out.into_inner())
}

fn clean_name(name: &str) -> String {
    let trimmed: String = name.trim().chars().take(MAX_NAME_CHARS).collect();
    if trimmed.is_empty() { "Mein Cape".to_string() } else { trimmed }
}

/// Cape-ID wie auf dem Cape-Server (`server/capes/png.mjs`): SHA-256 über Breite, Höhe
/// (je 4 Byte Big Endian) und die RGBA-Pixel. Hängt nur am Bildinhalt, nicht an der Datei.
pub fn share_id(png: &[u8]) -> Option<String> {
    let image = image::load_from_memory(png).ok()?.to_rgba8();
    let mut hasher = Sha256::new();
    hasher.update(image.width().to_be_bytes());
    hasher.update(image.height().to_be_bytes());
    hasher.update(image.as_raw());
    Some(hex::encode(hasher.finalize()))
}

fn view(dir: &Path, cape: &StoredCape) -> Option<CapeView> {
    let bytes = std::fs::read(png_path(dir, &cape.id)).ok()?;
    Some(CapeView {
        id: cape.id.clone(),
        name: cape.name.clone(),
        created: cape.created.clone(),
        share_id: share_id(&bytes).unwrap_or_default(),
        image: format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)),
    })
}

pub fn overview(dir: &Path) -> CapeOverview {
    let store = CapeStore::load(dir);
    CapeOverview {
        selected: store.selected.clone(),
        builtin: BUILTIN_CAPES.iter().map(|s| s.to_string()).collect(),
        capes: store.capes.iter().filter_map(|c| view(dir, c)).collect(),
    }
}

/// Neues Cape anlegen (`id = None`) oder ein vorhandenes ersetzen.
pub fn save_cape(dir: &Path, id: Option<&str>, name: &str, bytes: &[u8]) -> Result<CapeView> {
    let png = normalize_cape_png(bytes)?;
    let mut store = CapeStore::load(dir);
    std::fs::create_dir_all(dir)?;
    let cape = match id.and_then(|id| store.capes.iter_mut().find(|c| c.id == id)) {
        Some(existing) => {
            existing.name = clean_name(name);
            existing.clone()
        }
        None => {
            let cape = StoredCape {
                id: uuid::Uuid::new_v4().to_string(),
                name: clean_name(name),
                created: chrono::Utc::now().to_rfc3339(),
            };
            store.capes.push(cape.clone());
            cape
        }
    };
    if !is_valid_cape_id(&cape.id) {
        return Err(AppError::Other(format!("Ungültige Cape-ID: {}", cape.id)));
    }
    std::fs::write(png_path(dir, &cape.id), png)?;
    store.save(dir)?;
    view(dir, &cape).ok_or_else(|| AppError::Other("Cape wurde nicht gespeichert".into()))
}

pub fn rename_cape(dir: &Path, id: &str, name: &str) -> Result<()> {
    let mut store = CapeStore::load(dir);
    let cape = store
        .capes
        .iter_mut()
        .find(|c| c.id == id)
        .ok_or_else(|| AppError::Other(format!("Cape {id} nicht gefunden")))?;
    cape.name = clean_name(name);
    store.save(dir)
}

pub fn delete_cape(dir: &Path, id: &str) -> Result<()> {
    // Salami: nur gespeicherte Capes mit sauberer ID – die ID wird Teil eines Dateinamens
    if !is_valid_cape_id(id) {
        return Err(AppError::Other(format!("Ungültige Cape-ID: {id}")));
    }
    let mut store = CapeStore::load(dir);
    if store.find(id).is_none() {
        return Err(AppError::Other(format!("Cape {id} nicht gefunden")));
    }
    store.capes.retain(|c| c.id != id);
    if store.selected.as_deref() == Some(id) {
        store.selected = Some(DEFAULT_SELECTION.to_string());
    }
    let _ = std::fs::remove_file(png_path(dir, id));
    store.save(dir)
}

/// Auswahl setzen: `builtin:<name>`, ID eines eigenen Capes oder `None` (kein Cape).
pub fn select(dir: &Path, selection: Option<&str>) -> Result<()> {
    if let Some(sel) = selection {
        let valid = match sel.strip_prefix(BUILTIN_PREFIX) {
            Some(name) => BUILTIN_CAPES.contains(&name),
            None => CapeStore::load(dir).find(sel).is_some(),
        };
        if !valid {
            return Err(AppError::Other(format!("Unbekanntes Cape: {sel}")));
        }
    }
    let mut store = CapeStore::load(dir);
    store.selected = selection.map(str::to_string);
    store.save(dir)
}

/// Adresse des Cape-Servers. In Debug-Builds überschreibbar per `SALAMI_CAPES_URL`
/// (zum Testen gegen einen lokalen Server).
pub fn server_url() -> Option<String> {
    #[cfg(debug_assertions)]
    if let Ok(url) = std::env::var("SALAMI_CAPES_URL") {
        if !url.is_empty() {
            return Some(url.trim_end_matches('/').to_string());
        }
    }
    crate::branding::CAPE_SERVER_URL.map(|url| url.trim_end_matches('/').to_string())
}

#[derive(Deserialize)]
struct StatusResponse {
    status: HashMap<String, CapeReview>,
}

/// Prüfstatus dieser Cape-IDs ([`share_id`]). Fehlt eine ID, wurde das Bild noch nie
/// eingereicht (das macht [`submit_for_review`] oder salami-core beim Spielen mit dem Cape).
pub async fn review_status(ids: &[String]) -> Result<HashMap<String, CapeReview>> {
    let Some(base) = server_url() else {
        return Ok(HashMap::new());
    };
    let ids: Vec<&String> = ids.iter().filter(|id| id.len() == 64).take(100).collect();
    if ids.is_empty() {
        return Ok(HashMap::new());
    }
    let response = HTTP_CLIENT
        .post(format!("{base}/v1/capes/status"))
        .timeout(Duration::from_secs(10))
        .json(&serde_json::json!({ "ids": ids }))
        .send()
        .await
        .map_err(|e| AppError::Other(format!("Cape-Server nicht erreichbar: {e}")))?;
    if !response.status().is_success() {
        return Err(AppError::Other(format!("Cape-Server antwortet mit {}", response.status())));
    }
    let body: StatusResponse = response
        .json()
        .await
        .map_err(|e| AppError::Other(format!("Antwort des Cape-Servers unlesbar: {e}")))?;
    Ok(body.status)
}

/// Eigene Capes (share_id, PNG) aus `ids`, die laut `known` noch nie eingereicht wurden und
/// keinen frischen Versuch hinter sich haben. Der Versuch wird sofort vermerkt, damit
/// gleichzeitige Aufrufe nichts doppelt einreichen.
pub fn take_unsubmitted(
    dir: &Path,
    ids: &[String],
    known: &HashMap<String, CapeReview>,
) -> Vec<(String, Vec<u8>)> {
    let now = Instant::now();
    let mut attempts = SUBMIT_ATTEMPTS.lock().unwrap_or_else(|e| e.into_inner());
    let mut due: Vec<(String, Vec<u8>)> = Vec::new();
    for cape in CapeStore::load(dir).capes {
        let Ok(png) = std::fs::read(png_path(dir, &cape.id)) else {
            continue;
        };
        let Some(id) = share_id(&png) else {
            continue;
        };
        let recently_tried = attempts.get(&id).is_some_and(|at| now.duration_since(*at) < SUBMIT_RETRY);
        if !ids.contains(&id) || known.contains_key(&id) || recently_tried || due.iter().any(|(d, _)| *d == id) {
            continue;
        }
        attempts.insert(id.clone(), now);
        due.push((id, png));
    }
    due
}

#[derive(Deserialize)]
struct Challenge {
    #[serde(rename = "serverId")]
    server_id: String,
}

#[derive(Deserialize)]
struct ServerError {
    error: String,
}

async fn cape_server_json<T: DeserializeOwned>(response: reqwest::Response) -> Result<T> {
    let status = response.status();
    if !status.is_success() {
        let message = match response.json::<ServerError>().await {
            Ok(body) => body.error,
            Err(_) => status.to_string(),
        };
        return Err(AppError::Other(format!("Cape-Server: {message}")));
    }
    response
        .json()
        .await
        .map_err(|e| AppError::Other(format!("Antwort des Cape-Servers unlesbar: {e}")))
}

/// Reicht ein eigenes Cape zur Prüfung ein, ohne das getragene Cape zu ändern. Angemeldet
/// wird wie beim Betreten eines Minecraft-Servers: der Launcher meldet eine serverId bei
/// Mojang an, der Cape-Server fragt dort nach. Das Zugangstoken geht nur an Mojang.
pub async fn submit_for_review(
    player_id: Uuid,
    player_name: &str,
    access_token: &str,
    png: &[u8],
) -> Result<CapeReview> {
    let base = server_url().ok_or_else(|| AppError::Other("Kein Cape-Server eingerichtet".into()))?;
    let server_down = |e: reqwest::Error| AppError::Other(format!("Cape-Server nicht erreichbar: {e}"));

    let challenge: Challenge = cape_server_json(
        HTTP_CLIENT
            .post(format!("{base}/v1/auth/challenge"))
            .timeout(Duration::from_secs(10))
            .send()
            .await
            .map_err(server_down)?,
    )
    .await?;

    let joined = HTTP_CLIENT
        .post(MOJANG_JOIN_URL)
        .timeout(Duration::from_secs(10))
        .json(&serde_json::json!({
            "accessToken": access_token,
            "selectedProfile": player_id.simple().to_string(),
            "serverId": challenge.server_id,
        }))
        .send()
        .await
        .map_err(|e| AppError::Other(format!("Mojang nicht erreichbar: {e}")))?;
    if !joined.status().is_success() {
        return Err(AppError::Other(format!("Anmeldung bei Mojang fehlgeschlagen ({})", joined.status())));
    }

    cape_server_json(
        HTTP_CLIENT
            .post(format!("{base}/v1/capes/submit"))
            .timeout(Duration::from_secs(30))
            .json(&serde_json::json!({
                "name": player_name,
                "serverId": challenge.server_id,
                "png": base64::engine::general_purpose::STANDARD.encode(png),
            }))
            .send()
            .await
            .map_err(server_down)?,
    )
    .await
}

/// Wert für `-Dsalami.cape`: `none`, `builtin:<name>` oder der Pfad zur PNG-Datei.
pub fn launch_arg(dir: &Path) -> String {
    let store = CapeStore::load(dir);
    match store.selected.as_deref() {
        None => "none".to_string(),
        Some(sel) if sel.starts_with(BUILTIN_PREFIX) => sel.to_string(),
        Some(id) => {
            let path = png_path(dir, id);
            if store.find(id).is_some() && path.is_file() {
                path.display().to_string()
            } else {
                DEFAULT_SELECTION.to_string()
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("salami-capes-{}-{}", tag, std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        dir
    }

    fn png(w: u32, h: u32) -> Vec<u8> {
        let mut out = Cursor::new(Vec::new());
        image::RgbaImage::from_pixel(w, h, image::Rgba([178, 58, 58, 255]))
            .write_to(&mut out, ImageFormat::Png)
            .unwrap();
        out.into_inner()
    }

    #[test]
    fn accepts_cape_layouts_only() {
        assert!(normalize_cape_png(&png(64, 32)).is_ok());
        assert!(normalize_cape_png(&png(512, 256)).is_ok());
        assert!(normalize_cape_png(&png(64, 64)).is_err()); // Skin, kein Cape
        assert!(normalize_cape_png(&png(100, 50)).is_err()); // kein Vielfaches von 64
        assert!(normalize_cape_png(b"kein bild").is_err());
    }

    #[test]
    fn share_id_depends_on_pixels_only() {
        let a = share_id(&png(64, 32)).unwrap();
        assert_eq!(a.len(), 64);
        // gleiche Pixel, anders kodiert (RGB statt RGBA) → gleiche ID
        let mut rgb = Cursor::new(Vec::new());
        image::RgbImage::from_pixel(64, 32, image::Rgb([178, 58, 58]))
            .write_to(&mut rgb, ImageFormat::Png)
            .unwrap();
        assert_eq!(share_id(&rgb.into_inner()).unwrap(), a);
        assert_ne!(share_id(&png(128, 64)).unwrap(), a);
        // gleiche Rechnung wie server/capes/png.mjs (capeId) für 64x32 einfarbig rgba(178,58,58,255)
        assert_eq!(a, "866fdf3660b57ee457c5fa997f35d0cc495c72eaa03d02427b05f85a8779364d");
    }

    #[test]
    fn template_has_cape_layout() {
        assert!(normalize_cape_png(TEMPLATE_PNG).is_ok());
    }

    #[test]
    fn default_selection_is_salami() {
        let dir = temp_dir("default");
        assert_eq!(launch_arg(&dir), "builtin:salami");
    }

    #[test]
    fn own_cape_lifecycle() {
        let dir = temp_dir("lifecycle");
        let cape = save_cape(&dir, None, "  Meins  ", &png(64, 32)).unwrap();
        assert_eq!(cape.name, "Meins");
        assert!(cape.image.starts_with("data:image/png;base64,"));

        select(&dir, Some(&cape.id)).unwrap();
        assert_eq!(launch_arg(&dir), png_path(&dir, &cape.id).display().to_string());

        save_cape(&dir, Some(&cape.id), "Neu", &png(128, 64)).unwrap();
        assert_eq!(overview(&dir).capes.len(), 1);
        rename_cape(&dir, &cape.id, "Umbenannt").unwrap();
        assert_eq!(overview(&dir).capes[0].name, "Umbenannt");

        delete_cape(&dir, &cape.id).unwrap();
        assert!(overview(&dir).capes.is_empty());
        assert_eq!(launch_arg(&dir), "builtin:salami");
    }

    #[test]
    fn selection_is_validated() {
        let dir = temp_dir("select");
        assert!(select(&dir, Some("builtin:pepperoni")).is_ok());
        assert_eq!(launch_arg(&dir), "builtin:pepperoni");
        assert!(select(&dir, Some("builtin:gibtsnicht")).is_err());
        assert!(select(&dir, Some("unbekannte-id")).is_err());
        select(&dir, None).unwrap();
        assert_eq!(launch_arg(&dir), "none");
    }

    #[test]
    fn new_capes_are_submitted_once() {
        let dir = temp_dir("submit");
        let new = save_cape(&dir, None, "Neu", &png(256, 128)).unwrap();
        let known = save_cape(&dir, None, "Bekannt", &png(512, 256)).unwrap();
        let ids = vec![new.share_id.clone(), known.share_id.clone()];
        let status = HashMap::from([(known.share_id.clone(), CapeReview { status: "pending".into(), reason: None })]);

        let due = take_unsubmitted(&dir, &ids, &status);
        assert_eq!(due.len(), 1);
        assert_eq!(due[0].0, new.share_id);
        assert_eq!(share_id(&due[0].1).as_deref(), Some(new.share_id.as_str()));

        // gerade versucht → nicht gleich nochmal; nicht angefragte Capes nie
        save_cape(&dir, None, "Nicht angefragt", &png(128, 64)).unwrap();
        assert!(take_unsubmitted(&dir, &ids, &status).is_empty());
    }
}
