//! Salami Capes: eigene Capes (PNG im Minecraft-Cape-Layout) und die Auswahl, die beim
//! Spielstart als `-Dsalami.cape=…` an salami-core geht.
//!
//! Alles bleibt lokal im Launcher-Ordner (`salami_capes/`). Es gibt keinen Cape-Server –
//! andere Spieler sehen das Cape deshalb nicht.

use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use base64::Engine;
use image::{GenericImageView, ImageFormat};
use serde::{Deserialize, Serialize};
use std::io::Cursor;
use std::path::{Path, PathBuf};

/// Mitgelieferte Capes (Dateinamen in salami-core und `launcher/public/capes`).
pub const BUILTIN_CAPES: [&str; 3] = ["salami", "pepperoni", "board"];
pub const BUILTIN_PREFIX: &str = "builtin:";
const DEFAULT_SELECTION: &str = "builtin:salami";
const STORE_FILE: &str = "capes.json";
/// Vorlage zum Selbermalen (erzeugt von `branding/generate_capes.py`).
pub const TEMPLATE_PNG: &[u8] = include_bytes!("../../assets/salami_cape_template.png");
const MAX_WIDTH: u32 = 2048;
const MAX_NAME_CHARS: usize = 40;

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

fn view(dir: &Path, cape: &StoredCape) -> Option<CapeView> {
    let bytes = std::fs::read(png_path(dir, &cape.id)).ok()?;
    Some(CapeView {
        id: cape.id.clone(),
        name: cape.name.clone(),
        created: cape.created.clone(),
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
}
