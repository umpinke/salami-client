//! CurseForge-Dateien, die der Autor nur über die CurseForge-Webseite herausgibt (die API
//! liefert dann `downloadUrl: null`). Laut den CurseForge-Regeln für API-Keys darf der
//! Launcher sie nicht selbst laden – auch nicht über einen selbst gebauten CDN-Link.
//!
//! Stattdessen lädt der Spieler sie im Browser von CurseForge; sie landen im Download-Ordner.
//! Der Launcher sucht sie dort und übernimmt sie nur, wenn SHA1 bzw. CurseForge-Fingerabdruck
//! genau passen. Was fehlt, zeigt er im Fenster „Dateien selbst herunterladen“ mit Links an
//! (`EventType::CurseforgeManualDownloads`).

use crate::state::event_state::{EventPayload, EventType};
use crate::state::state_manager::State;
use crate::utils::hash_utils;
use log::{debug, info, warn};
use serde::Serialize;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};
use tokio::fs;

/// Ältere Dateien im Download-Ordner werden ohne passenden Namen nicht durchsucht.
const SCAN_MAX_AGE: Duration = Duration::from_secs(14 * 24 * 60 * 60);
const SCAN_MAX_FILES: usize = 60;
const SCAN_MAX_BYTES: u64 = 512 * 1024 * 1024;

/// Eine Datei, die der Spieler selbst von CurseForge laden muss.
#[derive(Debug, Clone)]
pub struct ManualFile {
    pub name: String,
    pub project_id: u32,
    pub file_id: u32,
    pub file_name: String,
    pub sha1: Option<String>,
    pub fingerprint: Option<u64>,
    /// Wohin die Datei soll: Mod-Cache oder z. B. `<Profil>/resourcepacks/<Datei>`.
    pub target: PathBuf,
    /// `None` = Mod (der Launcher übernimmt sie beim Start), sonst Unterordner wie `resourcepacks`.
    pub folder: Option<String>,
}

#[derive(Serialize)]
struct ManualDownloadEntry {
    name: String,
    file_name: String,
    url: String,
    /// `None` = Mod; sonst Unterordner wie `resourcepacks` …
    folder: Option<String>,
    /// … und dessen voller Pfad im Profil („Ordner öffnen“).
    folder_path: Option<String>,
}

#[derive(Serialize)]
struct ManualDownloadsPayload {
    files: Vec<ManualDownloadEntry>,
}

/// Passt `path` genau zu `file`? Ohne Prüfsumme wird nie etwas übernommen.
pub async fn file_matches(path: &Path, file: &ManualFile) -> bool {
    if let Some(sha1) = &file.sha1 {
        return hash_utils::calculate_sha1_from_file(path)
            .await
            .is_ok_and(|hash| hash.eq_ignore_ascii_case(sha1));
    }
    if let Some(fingerprint) = file.fingerprint {
        return hash_utils::calculate_curseforge_fingerprint(path)
            .await
            .is_ok_and(|actual| actual == fingerprint);
    }
    false
}

/// Namen, unter denen ein Browser die Datei speichert: wie bei CurseForge, mit Leerzeichen
/// statt `+` und mit Zähler bei doppelten Downloads (`name (1).jar`).
fn name_variants(file_name: &str) -> Vec<String> {
    let mut bases = vec![file_name.to_string()];
    let spaced = file_name.replace('+', " ");
    if spaced != file_name {
        bases.push(spaced);
    }
    let mut names = bases.clone();
    for base in &bases {
        let (stem, ext) = match base.rfind('.') {
            Some(dot) => (&base[..dot], &base[dot..]),
            None => (base.as_str(), ""),
        };
        for n in 1..=5 {
            names.push(format!("{stem} ({n}){ext}"));
        }
    }
    names
}

fn downloads_dir() -> Option<PathBuf> {
    directories::UserDirs::new().and_then(|dirs| dirs.download_dir().map(Path::to_path_buf))
}

/// Neueste .jar/.zip-Dateien im Download-Ordner (für Dateien, die der Browser umbenannt hat).
async fn recent_candidates(dir: &Path) -> Vec<PathBuf> {
    let Ok(mut entries) = fs::read_dir(dir).await else {
        return Vec::new();
    };
    let now = SystemTime::now();
    let mut found: Vec<(SystemTime, PathBuf)> = Vec::new();
    while let Ok(Some(entry)) = entries.next_entry().await {
        let path = entry.path();
        let is_archive = path
            .extension()
            .and_then(|e| e.to_str())
            .is_some_and(|e| e.eq_ignore_ascii_case("jar") || e.eq_ignore_ascii_case("zip"));
        let Ok(meta) = entry.metadata().await else {
            continue;
        };
        let Ok(modified) = meta.modified() else {
            continue;
        };
        let recent = now.duration_since(modified).map(|age| age <= SCAN_MAX_AGE).unwrap_or(true);
        if is_archive && meta.is_file() && meta.len() <= SCAN_MAX_BYTES && recent {
            found.push((modified, path));
        }
    }
    found.sort_by(|a, b| b.0.cmp(&a.0));
    found.into_iter().take(SCAN_MAX_FILES).map(|(_, path)| path).collect()
}

async fn copy_into_place(source: &Path, target: &Path) -> std::io::Result<()> {
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).await?;
    }
    let part = target.with_extension("salami-part");
    fs::copy(source, &part).await?;
    fs::rename(&part, target).await
}

/// Prüfsummen einer Datei im Download-Ordner, jeweils höchstens einmal berechnet.
#[derive(Default)]
struct Hashes {
    sha1: Option<Option<String>>,
    fingerprint: Option<Option<u64>>,
}

async fn matches_cached(path: &Path, file: &ManualFile, cache: &mut HashMap<PathBuf, Hashes>) -> bool {
    let hashes = cache.entry(path.to_path_buf()).or_default();
    if let Some(expected) = &file.sha1 {
        if hashes.sha1.is_none() {
            hashes.sha1 = Some(hash_utils::calculate_sha1_from_file(path).await.ok());
        }
        return hashes.sha1.as_ref().and_then(|h| h.as_deref()).is_some_and(|h| h.eq_ignore_ascii_case(expected));
    }
    if let Some(expected) = file.fingerprint {
        if hashes.fingerprint.is_none() {
            hashes.fingerprint = Some(hash_utils::calculate_curseforge_fingerprint(path).await.ok());
        }
        return hashes.fingerprint.flatten() == Some(expected);
    }
    false
}

/// Sucht die Dateien im Download-Ordner, kopiert passende nach `target` und gibt die
/// zurück, die weiterhin fehlen.
pub async fn pick_up_from_downloads(files: Vec<ManualFile>) -> Vec<ManualFile> {
    if files.is_empty() {
        return files;
    }
    let Some(dir) = downloads_dir() else {
        return files;
    };
    let recent = recent_candidates(&dir).await;
    let mut cache: HashMap<PathBuf, Hashes> = HashMap::new();
    let mut missing = Vec::new();

    'files: for file in files {
        // erst die Namen, unter denen der Browser sie speichert, dann die neuesten Downloads
        let mut candidates: Vec<PathBuf> = name_variants(&file.file_name)
            .into_iter()
            .map(|name| dir.join(name))
            .filter(|path| path.is_file())
            .collect();
        for path in &recent {
            if !candidates.contains(path) {
                candidates.push(path.clone());
            }
        }
        for path in candidates {
            if !matches_cached(&path, &file, &mut cache).await {
                continue;
            }
            match copy_into_place(&path, &file.target).await {
                Ok(()) => {
                    info!("Took '{}' from the downloads folder: {:?}", file.name, path);
                    continue 'files;
                }
                Err(e) => warn!("Could not copy {:?} to {:?}: {}", path, file.target, e),
            }
        }
        debug!("'{}' ({}) not found in the downloads folder", file.name, file.file_name);
        missing.push(file);
    }
    missing
}

/// Download-Seite jeder Datei auf CurseForge (`<Mod-Seite>/files/<Datei-ID>`).
async fn file_pages(files: &[ManualFile]) -> HashMap<u32, String> {
    let mut project_ids: Vec<u32> = files.iter().map(|f| f.project_id).collect();
    project_ids.sort_unstable();
    project_ids.dedup();
    match crate::integrations::curseforge::get_mods_by_ids(project_ids, None).await {
        Ok(response) => response
            .data
            .into_iter()
            .map(|m| (m.id, m.links.website_url.trim_end_matches('/').to_string()))
            .collect(),
        Err(e) => {
            warn!("Could not look up CurseForge pages for manual downloads: {}", e);
            HashMap::new()
        }
    }
}

/// Zeigt im Launcher das Fenster mit den Dateien, die der Spieler selbst laden muss.
pub async fn announce_missing(files: &[ManualFile]) {
    if files.is_empty() {
        return;
    }
    let pages = file_pages(files).await;
    let mut entries = Vec::new();
    for file in files {
        // Zielordner für Ressourcenpakete & Co. schon anlegen, damit „Ordner öffnen“ klappt
        let folder_path = match (&file.folder, file.target.parent()) {
            (Some(_), Some(parent)) => {
                let _ = fs::create_dir_all(parent).await;
                Some(parent.display().to_string())
            }
            _ => None,
        };
        entries.push(ManualDownloadEntry {
            name: file.name.clone(),
            file_name: file.file_name.clone(),
            url: match pages.get(&file.project_id) {
                Some(page) if page.starts_with("https://www.curseforge.com/") => {
                    format!("{page}/files/{}", file.file_id)
                }
                _ => format!("https://www.curseforge.com/projects/{}", file.project_id),
            },
            folder: file.folder.clone(),
            folder_path,
        });
    }
    let payload = ManualDownloadsPayload { files: entries };
    let message = match serde_json::to_string(&payload) {
        Ok(json) => json,
        Err(e) => {
            warn!("Could not serialize manual download list: {}", e);
            return;
        }
    };
    let Ok(state) = State::get().await else {
        return;
    };
    let event = EventPayload {
        event_id: uuid::Uuid::new_v4(),
        event_type: EventType::CurseforgeManualDownloads,
        target_id: None,
        message,
        progress: None,
        error: None,
    };
    if let Err(e) = state.emit_event(event).await {
        warn!("Could not show the manual download list: {}", e);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn browser_names_are_covered() {
        let names = name_variants("Better+Lanterns+v1.2(mc-1.20.1).zip");
        assert!(names.contains(&"Better+Lanterns+v1.2(mc-1.20.1).zip".to_string()));
        assert!(names.contains(&"Better Lanterns v1.2(mc-1.20.1).zip".to_string()));
        assert!(names.contains(&"Better Lanterns v1.2(mc-1.20.1) (2).zip".to_string()));
        let jar = name_variants("skinlayers3d-forge-1.11.4-mc1.20.1.jar");
        assert!(jar.contains(&"skinlayers3d-forge-1.11.4-mc1.20.1 (1).jar".to_string()));
    }

    #[tokio::test]
    async fn only_files_with_matching_checksum_are_taken() {
        let dir = std::env::temp_dir().join(format!("salami-cf-manual-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let good = dir.join("mod.jar");
        std::fs::write(&good, b"richtiger Inhalt").unwrap();
        let sha1 = hash_utils::calculate_sha1_from_file(&good).await.unwrap();
        let file = |sha1: Option<String>| ManualFile {
            name: "Mod".into(),
            project_id: 1,
            file_id: 2,
            file_name: "mod.jar".into(),
            sha1,
            fingerprint: None,
            target: dir.join("cache").join("mod.jar"),
            folder: None,
        };
        assert!(file_matches(&good, &file(Some(sha1.to_uppercase()))).await);
        assert!(!file_matches(&good, &file(Some("0".repeat(40)))).await);
        assert!(!file_matches(&good, &file(None)).await); // ohne Prüfsumme nie

        copy_into_place(&good, &file(None).target).await.unwrap();
        assert_eq!(std::fs::read(dir.join("cache").join("mod.jar")).unwrap(), b"richtiger Inhalt");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
