//! Aufräumen von Resten aus der Zeit als NoRisk-Fork: Zwischenspeicher, die der Launcher
//! nicht mehr nutzt oder unter neuem Namen neu aufbaut. Spielordner (Welten, Wegpunkte,
//! Einstellungen) werden nicht angefasst.

use crate::config::{standard_meta_dir, ProjectDirsExt, LAUNCHER_DIRECTORY};
use log::{info, warn};
use std::path::Path;

/// Nutzt der Launcher seinen eigenen Spielordner? Zeigt ein eigener Spielordner z. B. auf eine
/// NoRisk-Installation, darf dort nichts gelöscht oder umbenannt werden.
pub fn uses_own_meta_dir() -> bool {
    LAUNCHER_DIRECTORY.meta_dir() == standard_meta_dir()
}

/// Läuft beim Start im Hintergrund; Fehler werden nur protokolliert.
pub async fn remove_norisk_leftovers() {
    // Bis 1.5.0 hieß die Clip-Aufnahme norisk-capture.exe. Ein Update legt salami-capture.exe
    // daneben, die alte Datei bliebe sonst im Installationsordner liegen.
    #[cfg(windows)]
    if let Some(install_dir) = std::env::current_exe().ok().and_then(|exe| exe.parent().map(Path::to_path_buf)) {
        remove_file(&install_dir.join("norisk-capture.exe")).await;
    }
    if !uses_own_meta_dir() {
        info!("[Cleanup] Custom game folder in use – leaving its files untouched");
        return;
    }
    let root = LAUNCHER_DIRECTORY.root_dir();
    let meta = LAUNCHER_DIRECTORY.meta_dir();

    for name in [
        "norisk_modpacks.json",
        "norisk_modpacks_exp.json",
        "norisk_versions.json",
        "norisk_versions_exp.json",
    ] {
        remove_file(&root.join(name)).await;
    }
    // NoRisk-Grafiken und der frühere Forge-Mod-Lader von NoRisk
    remove_dir(&meta.join("assets").join("noriskclient")).await;
    remove_dir(&meta.join("libraries").join("gg").join("norisk")).await;
    // Laufzeitdateien mit altem Präfix und heruntergeladene NoRisk-Mods
    remove_matching(&meta.join("runtime"), |n| n.starts_with("nrc_") && n.ends_with(".txt")).await;
    remove_matching(&meta.join("assets").join("log_configs"), |n| n.starts_with("nrc-") && n.ends_with(".xml")).await;
    remove_matching(&meta.join("mod_cache"), |n| n.starts_with("nrc-") && n.ends_with(".jar")).await;
    // Token-Rückgaben des alten NoRisk-Clients (Klartext-Token)
    remove_matching(&meta.join("auth"), |n| n.starts_with("handback.") && n.ends_with(".json")).await;
}

/// Ordnernamen aus der NoRisk-Zeit auf „salami“ umstellen. Läuft beim Start, bevor Profile
/// geladen oder gestartet werden (sonst legt der Launcher die neuen Ordner leer an). Es wird
/// nur umbenannt und verschoben, nie gelöscht – außer leeren Ordnern. In einem eigenen
/// Spielordner (z. B. einer NoRisk-Installation) bleibt alles, wie es ist.
pub fn migrate_profile_folders() {
    // Clip-Infos liegen immer im Launcher-Ordner, nicht im Spielordner
    crate::utils::clip_library::migrate_legacy_details();
    if !uses_own_meta_dir() {
        return;
    }
    let profiles = crate::state::profile_state::default_profile_path();
    migrate_profile_namespace(&profiles);
    migrate_profile_image_dirs(&profiles);
}

/// `profiles/noriskclient/{new,legacy}` → `profiles/salamiclient/{new,legacy}` (gemeinsame
/// Spielordner der Client-Gruppe). `temp/` räumt `trash_utils::reap_temp_profiles` auf.
pub fn migrate_profile_namespace(profiles: &Path) {
    let legacy = profiles.join(crate::branding::LEGACY_PROFILES_NAMESPACE);
    if !is_real_dir(&legacy) {
        return;
    }
    let current = profiles.join(crate::branding::PROFILES_NAMESPACE);
    for name in ["new", "legacy"] {
        let from = legacy.join(name);
        let to = current.join(name);
        if !is_real_dir(&from) {
            continue;
        }
        if to.exists() {
            warn!("[Cleanup] {:?} and {:?} both exist – leaving the old folder in place", from, to);
            continue;
        }
        match std::fs::create_dir_all(&current).and_then(|_| std::fs::rename(&from, &to)) {
            Ok(()) => info!("[Cleanup] moved {:?} -> {:?}", from, to),
            Err(e) => warn!("[Cleanup] could not move {:?} -> {:?}: {}", from, to, e),
        }
    }
    // leere Reste entfernen (remove_dir schlägt bei nicht leeren Ordnern fehl)
    let _ = std::fs::remove_dir(legacy.join("temp"));
    let _ = std::fs::remove_dir(&legacy);
}

/// Profilbild-Ordner `NoRiskClientLauncher/` → `SalamiClient/` in jedem Profil (auch in
/// Gruppen- und Standard-Profilordnern eine Ebene tiefer). Gespeicherte Pfade mit dem alten
/// Präfix löst `path_commands::resolve_profile_image` weiter auf.
pub fn migrate_profile_image_dirs(profiles: &Path) {
    let mut instances = Vec::new();
    for entry in read_dirs(profiles) {
        instances.extend(read_dirs(&entry));
        instances.push(entry);
    }
    for instance in instances {
        let from = instance.join(crate::commands::path_commands::LEGACY_PROFILE_IMAGE_DIR);
        if !is_real_dir(&from) {
            continue;
        }
        let to = instance.join(crate::commands::path_commands::PROFILE_IMAGE_DIR);
        if !to.exists() {
            match std::fs::rename(&from, &to) {
                Ok(()) => info!("[Cleanup] moved {:?} -> {:?}", from, to),
                Err(e) => warn!("[Cleanup] could not move {:?} -> {:?}: {}", from, to, e),
            }
            continue;
        }
        // Beide da: fehlende Bilder hinüberschieben, gleichnamige bleiben im alten Ordner
        for file in std::fs::read_dir(&from).into_iter().flatten().flatten() {
            let target = to.join(file.file_name());
            if !target.exists() {
                if let Err(e) = std::fs::rename(file.path(), &target) {
                    warn!("[Cleanup] could not move {:?} -> {:?}: {}", file.path(), target, e);
                }
            }
        }
        let _ = std::fs::remove_dir(&from);
    }
}

/// Unterordner (keine Verknüpfungen).
fn read_dirs(dir: &Path) -> Vec<std::path::PathBuf> {
    std::fs::read_dir(dir)
        .into_iter()
        .flatten()
        .flatten()
        .filter(|entry| entry.file_type().map(|t| t.is_dir()).unwrap_or(false))
        .map(|entry| entry.path())
        .collect()
}

fn is_real_dir(path: &Path) -> bool {
    std::fs::symlink_metadata(path).map(|m| m.is_dir()).unwrap_or(false)
}

async fn remove_file(path: &Path) {
    if path.is_file() {
        match tokio::fs::remove_file(path).await {
            Ok(()) => info!("[Cleanup] removed {:?}", path),
            Err(e) => warn!("[Cleanup] could not remove {:?}: {}", path, e),
        }
    }
}

async fn remove_dir(path: &Path) {
    // nur echte Ordner – keine Verknüpfungen auflösen
    let is_real_dir = tokio::fs::symlink_metadata(path).await.map(|m| m.is_dir()).unwrap_or(false);
    if is_real_dir {
        match tokio::fs::remove_dir_all(path).await {
            Ok(()) => info!("[Cleanup] removed {:?}", path),
            Err(e) => warn!("[Cleanup] could not remove {:?}: {}", path, e),
        }
    }
}

async fn remove_matching(dir: &Path, matches: impl Fn(&str) -> bool) {
    let Ok(mut entries) = tokio::fs::read_dir(dir).await else { return };
    while let Ok(Some(entry)) = entries.next_entry().await {
        let name = entry.file_name().to_string_lossy().into_owned();
        if matches(&name) {
            remove_file(&entry.path()).await;
        }
    }
}
