//! Bereich des Asset-Protokolls (`asset://` / `http://asset.localhost`), über den die Oberfläche
//! lokale Dateien anzeigt (Screenshots, Clips, Profilbilder …). Statt der ganzen Platte gibt der
//! Launcher zur Laufzeit nur seine eigenen Ordner frei: Launcher- und Meta-Ordner, einen eigenen
//! Spielordner (Profile) und den Clip-Ordner. Was der Nutzer im Datei-Dialog wählt, gibt Tauri
//! selbst frei; Bilder und Videos außerhalb, die die Oberfläche dauerhaft zeigt (eigener
//! Hintergrund, eigene Profil-Icons), merkt sich `user_media`.

use crate::config::{standard_meta_dir, ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::state::config_state::LauncherConfig;
use crate::utils::path_guard;
use log::{info, warn};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use tauri::{AppHandle, Manager, Runtime};

/// Solange gesetzt, gibt der Launcher selbst etwas frei – das zählt nicht als Auswahl des Nutzers.
static INTERNAL: AtomicBool = AtomicBool::new(false);
static INTERNAL_LOCK: Mutex<()> = Mutex::new(());

/// Kommt die gerade gemeldete Freigabe vom Launcher selbst (nicht aus dem Datei-Dialog)?
pub fn is_internal_grant() -> bool {
    INTERNAL.load(Ordering::SeqCst)
}

fn with_internal<T>(f: impl FnOnce() -> T) -> T {
    let _guard = INTERNAL_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    INTERNAL.store(true, Ordering::SeqCst);
    let result = f();
    INTERNAL.store(false, Ordering::SeqCst);
    result
}

/// Gibt einen Ordner des Launchers (rekursiv) für die Anzeige frei.
pub fn allow_launcher_dir<R: Runtime>(app: &AppHandle<R>, dir: &Path) {
    if dir.as_os_str().is_empty() || path_guard::is_network_path(dir) {
        return;
    }
    let dir = dunce(dir);
    with_internal(|| {
        if let Err(e) = app.asset_protocol_scope().allow_directory(&dir, true) {
            warn!("[AssetScope] Could not allow {}: {}", dir.display(), e);
        }
    });
}

/// Gibt eine einzelne Datei für die Anzeige frei (vom Launcher geprüft, kein Dialog).
pub fn allow_launcher_file<R: Runtime>(app: &AppHandle<R>, file: &Path) {
    if path_guard::is_network_path(file) {
        return;
    }
    let file = dunce(file);
    with_internal(|| {
        if let Err(e) = app.asset_protocol_scope().allow_file(&file) {
            warn!("[AssetScope] Could not allow {}: {}", file.display(), e);
        }
    });
}

fn dunce(path: &Path) -> std::path::PathBuf {
    let text = path.to_string_lossy();
    match text.strip_prefix(r"\\?\") {
        Some(rest) if !rest.starts_with("UNC\\") => std::path::PathBuf::from(rest),
        _ => path.to_path_buf(),
    }
}

/// Beim Start: die festen Launcher-Ordner. Danach meldet `user_paths` Auswahlen im Datei-Dialog.
pub fn init<R: Runtime>(app: &AppHandle<R>) {
    for dir in [LAUNCHER_DIRECTORY.root_dir(), standard_meta_dir(), LAUNCHER_DIRECTORY.data_dir().to_path_buf()] {
        allow_launcher_dir(app, &dir);
    }
    crate::utils::user_paths::install(app);
    crate::utils::user_media::restore(app);
    info!("[AssetScope] Launcher folders allowed for display");
}

/// Ordner aus der Konfiguration (eigener Spielordner, Clip-Ordner) – nach dem Laden und nach
/// jeder bestätigten Änderung.
pub fn allow_config_dirs<R: Runtime>(app: &AppHandle<R>, config: &LauncherConfig) {
    if let Some(dir) = config.custom_game_directory.as_deref() {
        allow_launcher_dir(app, dir);
    }
    allow_launcher_dir(app, &LAUNCHER_DIRECTORY.meta_dir());
    allow_launcher_dir(app, &crate::state::profile_state::default_profile_path());
    allow_launcher_dir(app, &config.clips.resolved_output_dir());
}
