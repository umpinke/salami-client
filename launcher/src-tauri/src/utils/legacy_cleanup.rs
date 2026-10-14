//! Aufräumen von Resten aus der Zeit als NoRisk-Fork: Zwischenspeicher, die der Launcher
//! nicht mehr nutzt oder unter neuem Namen neu aufbaut. Spielordner (Welten, Wegpunkte,
//! Einstellungen) werden nicht angefasst.

use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use log::{info, warn};
use std::path::Path;

/// Läuft beim Start im Hintergrund; Fehler werden nur protokolliert.
pub async fn remove_norisk_leftovers() {
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
