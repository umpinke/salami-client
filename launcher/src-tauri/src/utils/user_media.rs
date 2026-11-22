//! Bilder und Videos außerhalb der Launcher-Ordner, die die Oberfläche dauerhaft anzeigt: eigener
//! Hintergrund (Einstellungen → Darstellung) und eigene Profil-Icons. Das Asset-Protokoll gibt
//! nur Launcher-Ordner frei (`asset_scope`); solche Dateien merkt sich der Launcher in
//! `user_media.json`, sobald der Nutzer sie im Datei-Dialog gewählt (oder im nativen Dialog
//! erlaubt) hat, und gibt sie bei jedem Start wieder frei.

use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::CommandError;
use crate::utils::{asset_scope, launch_guard, path_guard, user_paths};
use log::{info, warn};
use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Runtime};

const FILE: &str = "user_media.json";
const MAX_ENTRIES: usize = 100;
const MEDIA_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "mp4", "webm"];

fn store_path() -> PathBuf {
    LAUNCHER_DIRECTORY.root_dir().join(FILE)
}

fn load() -> BTreeSet<String> {
    std::fs::read_to_string(store_path())
        .ok()
        .and_then(|text| serde_json::from_str::<Vec<String>>(&text).ok())
        .map(|list| list.into_iter().collect())
        .unwrap_or_default()
}

fn save(entries: &BTreeSet<String>) {
    let list: Vec<&String> = entries.iter().rev().take(MAX_ENTRIES).collect();
    match serde_json::to_vec_pretty(&list) {
        Ok(bytes) => {
            if let Err(e) = crate::utils::mc_utils::atomic_write(&store_path(), &bytes) {
                warn!("[UserMedia] Could not save {}: {}", FILE, e);
            }
        }
        Err(e) => warn!("[UserMedia] Could not encode {}: {}", FILE, e),
    }
}

/// Ein Bild/Video auf der lokalen Platte (kein Netzwerkpfad, bekannte Endung, existiert)?
pub fn is_media_file(path: &Path) -> bool {
    let extension_ok = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| MEDIA_EXTENSIONS.contains(&e.to_ascii_lowercase().as_str()))
        .unwrap_or(false);
    extension_ok && !path_guard::is_network_path(path) && !path_guard::is_protected(path) && path.is_file()
}

fn key(path: &Path) -> String {
    path.to_string_lossy().to_string()
}

/// Beim Start: gemerkte Dateien wieder freigeben.
pub fn restore<R: Runtime>(app: &AppHandle<R>) {
    for entry in load() {
        let path = PathBuf::from(&entry);
        if is_media_file(&path) {
            asset_scope::allow_launcher_file(app, &path);
        }
    }
}

/// Gibt eigene Bilder/Videos für die Anzeige frei. Gemerkte und gerade im Dialog gewählte
/// Dateien sofort; andere nur, wenn `ask` gesetzt ist und der Nutzer sie im nativen Dialog
/// erlaubt (einmalig, z. B. nach dem Update für einen schon eingestellten Hintergrund).
/// → die freigegebenen Pfade
#[tauri::command]
pub async fn allow_user_media(
    app: AppHandle,
    paths: Vec<String>,
    ask: Option<bool>,
) -> Result<Vec<String>, CommandError> {
    let mut stored = load();
    let mut allowed = Vec::new();
    let mut unknown = Vec::new();
    let mut changed = false;

    for raw in paths.into_iter().take(MAX_ENTRIES) {
        let path = PathBuf::from(&raw);
        if !is_media_file(&path) {
            continue;
        }
        if stored.contains(&key(&path)) {
            allowed.push(raw);
        } else if user_paths::was_chosen(&path) {
            stored.insert(key(&path));
            changed = true;
            allowed.push(raw);
        } else {
            unknown.push(raw);
        }
    }

    if !unknown.is_empty() && ask.unwrap_or(false) {
        let message = [
            "Diese eigenen Bilder/Videos weiter im Launcher anzeigen?".to_string(),
            "Keep showing these images/videos from your computer in the launcher?".to_string(),
            String::new(),
            unknown.join("\n"),
        ]
        .join("\n");
        if launch_guard::confirm_dialog(&app, message).await {
            for raw in unknown {
                stored.insert(key(Path::new(&raw)));
                allowed.push(raw);
            }
            changed = true;
        }
    }

    if changed {
        save(&stored);
    }
    for raw in &allowed {
        asset_scope::allow_launcher_file(&app, Path::new(raw));
    }
    if !allowed.is_empty() {
        info!("[UserMedia] {} file(s) allowed for display", allowed.len());
    }
    Ok(allowed)
}

/// Vergisst eine eigene Datei (Hintergrund entfernt, Icon aus der Bibliothek gelöscht).
#[tauri::command]
pub async fn forget_user_media(path: String) -> Result<(), CommandError> {
    let mut stored = load();
    if stored.remove(&path) {
        save(&stored);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_local_media_files() {
        let base = std::env::temp_dir().join(format!("salami-user-media-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        std::fs::create_dir_all(&base).unwrap();
        for name in ["bg.png", "bg.MP4", "notes.txt", "accounts.json"] {
            std::fs::write(base.join(name), "x").unwrap();
        }
        assert!(is_media_file(&base.join("bg.png")));
        assert!(is_media_file(&base.join("bg.MP4")));
        assert!(!is_media_file(&base.join("notes.txt")));
        assert!(!is_media_file(&base.join("accounts.json")));
        assert!(!is_media_file(&base.join("missing.png")));
        #[cfg(windows)]
        assert!(!is_media_file(Path::new(r"\\server\share\bg.png")));
        let _ = std::fs::remove_dir_all(&base);
    }
}
