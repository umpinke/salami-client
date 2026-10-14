use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use crate::minecraft::downloads::mod_resolver::TargetMod;
use crate::minecraft::launch::launch_summary::DownloadStats;
use crate::state::profile_state::{self, ModSource, Profile};
use crate::utils::download_utils::{DownloadConfig, DownloadUtils};
use futures::stream::{iter, StreamExt};
use log::{debug, error, info, trace, warn};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::Arc;
use tokio::fs::{self, read_dir};
use tokio::io::AsyncWriteExt;

const DEFAULT_CONCURRENT_MOD_DOWNLOADS: usize = 4;
const HASH_CONCURRENCY: usize = 8;
const MOD_CACHE_DIR_NAME: &str = "mod_cache";
/// Liste der vom Launcher in `<instanz>/mods` kopierten Forge/NeoForge-Mods.
pub const FORGE_MANAGED_LIST: &str = ".salami-managed-mods.json";

/// Nur ein Dateiname, kein Pfad (schützt vor `..`/Unterordnern aus einer manipulierten Liste).
fn is_plain_file_name(name: &str) -> bool {
    !name.is_empty() && name != "." && name != ".." && !name.contains(['/', '\\', ':'])
}

pub struct ModDownloadService {
    concurrent_downloads: usize,
    stats: Option<Arc<DownloadStats>>,
}

impl ModDownloadService {
    pub fn new() -> Self {
        Self {
            concurrent_downloads: DEFAULT_CONCURRENT_MOD_DOWNLOADS,
            stats: None,
        }
    }

    pub fn with_concurrency(concurrent_downloads: usize) -> Self {
        Self {
            concurrent_downloads,
            stats: None,
        }
    }

    pub fn with_stats(mut self, stats: Arc<DownloadStats>) -> Self {
        self.stats = Some(stats);
        self
    }

    /// Downloads all enabled mods into the central mod cache.
    /// Creates the cache directory if it doesn't exist.
    /// Verifies SHA1 hashes for Modrinth downloads if available.
    pub async fn download_mods_to_cache(&self, profile: &Profile) -> Result<()> {
        info!(
            "Checking/Downloading mods to cache for profile: '{}' (Concurrency: {})",
            profile.name, self.concurrent_downloads
        );

        self.download_mod_list_to_cache(&profile.mods).await
    }

    pub async fn download_mod_list_to_cache(&self, mods: &[profile_state::Mod]) -> Result<()> {
        if mods.is_empty() {
            return Ok(());
        }

        let mod_cache_dir = LAUNCHER_DIRECTORY.meta_dir().join(MOD_CACHE_DIR_NAME);
        if !mod_cache_dir.exists() {
            info!("Creating mod cache directory: {:?}", mod_cache_dir);
            fs::create_dir_all(&mod_cache_dir).await?;
        }

        let mut download_futures = Vec::new();

        for mod_info in mods.iter() {
            if !mod_info.enabled {
                trace!("Skipping disabled mod: {:?}", mod_info.display_name);
                continue;
            }

            let display_name_opt = mod_info.display_name.clone();
            let cache_dir_clone = mod_cache_dir.clone();
            let source_clone = mod_info.source.clone();
            let stats = self.stats.clone();

            let filename_result = profile_state::get_profile_mod_filename(&mod_info.source);

            download_futures.push(async move {
                let filename = match filename_result {
                    Ok(fname) => fname,
                    Err(e) => {
                        error!(
                            "Skipping download for mod '{}': {}",
                            display_name_opt.as_deref().unwrap_or("?"),
                            e
                        );
                        return Err(e);
                    }
                };
                let display_name = display_name_opt.as_deref().unwrap_or(&filename);
                let target_path = cache_dir_clone.join(&filename);

                match source_clone {
                    ModSource::Modrinth {
                        download_url,
                        file_hash_sha1,
                        ..
                    } => {
                        trace!(
                            "Preparing Modrinth mod for cache: {} ({})",
                            display_name, filename
                        );
                        Self::download_and_verify_file(
                            &download_url,
                            &target_path,
                            file_hash_sha1.as_deref(),
                            stats,
                        )
                        .await
                        .map_err(|e| {
                            error!("Failed cache mod {}: {}", display_name, e);
                            e
                        })
                    }
                    ModSource::CurseForge {
                        download_url,
                        file_hash_sha1,
                        ..
                    } => {
                        trace!(
                            "Preparing CurseForge mod for cache: {} ({})",
                            display_name, filename
                        );
                        Self::download_and_verify_file(
                            &download_url,
                            &target_path,
                            file_hash_sha1.as_deref(),
                            stats,
                        )
                        .await
                        .map_err(|e| {
                            error!("Failed cache mod {}: {}", display_name, e);
                            e
                        })
                    }
                    ModSource::Url { url, .. } => {
                        trace!("Preparing URL mod for cache: {} ({})", display_name, filename);
                        Self::download_and_verify_file(&url, &target_path, None, stats)
                            .await
                            .map_err(|e| {
                                error!("Failed cache mod {}: {}", display_name, e);
                                e
                            })
                    }
                    ModSource::Local { file_name } => {
                        trace!("Skipping local mod (cache check): {}", file_name);
                        Ok(())
                    }
                    ModSource::Maven { .. } => {
                        warn!(
                            "Skipping Maven mod source (cache check - not implemented): {:?}",
                            display_name_opt
                        );
                        Ok(())
                    }
                    ModSource::Embedded { name } => {
                        debug!("Skipping embedded mod (cache check): {}", name);
                        Ok(())
                    }
                }
            });
        }

        let task_count = download_futures.len();
        debug!("Executing {} mod cache tasks...", task_count);
        let results: Vec<Result<()>> = iter(download_futures)
            .buffer_unordered(self.concurrent_downloads)
            .collect()
            .await;

        let mut errors = Vec::new();
        for result in results {
            if let Err(e) = result {
                errors.push(e);
            }
        }

        if errors.is_empty() {
            info!("Mod cache ready: {} mods", task_count);
            Ok(())
        } else {
            error!(
                "Mod cache check/download process completed with {} errors",
                errors.len()
            );
            Err(errors.remove(0))
        }
    }

    /// Synchronizes mods from the central cache to the profile's actual game directory mods folder.
    /// Takes the resolved list of target mods to sync.
    pub async fn sync_mods_to_profile(
        &self,
        target_mods: &[TargetMod],
        profile_mods_dir: &PathBuf,
    ) -> Result<()> {
        let profile_name = "Target Profile";
        info!(
            "Syncing resolved mods to profile mods directory '{:?}' for '{}'...",
            profile_mods_dir, profile_name
        );

        if !profile_mods_dir.exists() {
            debug!("Creating profile mods directory: {:?}", profile_mods_dir);
            fs::create_dir_all(&profile_mods_dir).await?;
        }

        let required_mods: HashMap<String, PathBuf> = target_mods
            .iter()
            .map(|tm| (tm.filename.clone(), tm.cache_path.clone()))
            .collect();
        let required_filenames: HashSet<String> = required_mods.keys().cloned().collect();

        debug!("Required mods for sync: {:?}", required_filenames);

        let mut valid_existing_filenames = HashSet::new();
        if profile_mods_dir.exists() {
            let mut dir_entries = read_dir(&profile_mods_dir).await?;
            while let Some(entry) = dir_entries.next_entry().await? {
                let path = entry.path();
                if path.is_file() {
                    if let Some(filename) = path.file_name().and_then(|n| n.to_str()) {
                        // Check if file is valid by comparing with cache version
                        if let Some(cache_path) = required_mods.get(filename) {
                            if Self::is_file_valid(&path, cache_path).await {
                                valid_existing_filenames.insert(filename.to_string());
                            } else {
                                info!("Found corrupt/invalid mod file, will replace: {}", filename);
                            }
                        } else {
                            // File not in required_mods, will be removed anyway
                            valid_existing_filenames.insert(filename.to_string());
                        }
                    }
                }
            }
        }
        debug!(
            "Valid existing mods in profile directory: {:?}",
            valid_existing_filenames
        );

        let mods_to_remove: HashSet<String> = valid_existing_filenames
            .difference(&required_filenames)
            .cloned()
            .collect();
        let mods_to_add: HashSet<String> = required_filenames
            .difference(&valid_existing_filenames)
            .cloned()
            .collect();

        for filename in &mods_to_remove {
            let target_path = profile_mods_dir.join(filename);
            info!("Moving mod to trash from '{}': {}", profile_name, filename);
            crate::utils::trash_utils::move_path_to_trash(&target_path, Some("managed_mods")).await?;
        }

        for filename in &mods_to_add {
            if let Some(cache_path) = required_mods.get(filename) {
                let target_path = profile_mods_dir.join(filename);
                info!("Copying mod to '{}': {}", profile_name, filename);
                Self::robust_copy_file(cache_path, &target_path).await.map_err(|e| {
                    error!(
                        "Failed to copy {:?} to {:?}: {}",
                        cache_path, target_path, e
                    );
                    e
                })?;
            } else {
                error!(
                    "Cache path not found for required mod '{}'! This indicates an internal error.",
                    filename
                );
                return Err(AppError::Other(format!(
                    "Cache path not found for required mod '{}'",
                    filename
                )));
            }
        }

        info!(
            "Mod sync completed for '{}' -> {:?}",
            profile_name, profile_mods_dir
        );
        Ok(())
    }

    /// Forge/NeoForge laden Mods nur aus `<instanz>/mods`. Kopiert die vom Launcher verwalteten
    /// Mods (`target_mods`) und die eigenen Mods des Profils (`*.jar` in `local_dir`) dorthin.
    /// Die Liste [`FORGE_MANAGED_LIST`] merkt sich, welche Dateien vom Launcher stammen: nur diese
    /// werden beim nächsten Start ersetzt oder entfernt – selbst in `mods/` gelegte Mods bleiben.
    pub async fn sync_forge_mods(
        &self,
        target_mods: &[TargetMod],
        local_dir: &std::path::Path,
        root_mods_dir: &std::path::Path,
    ) -> Result<()> {
        fs::create_dir_all(root_mods_dir).await?;

        let mut wanted: Vec<(String, PathBuf)> = target_mods
            .iter()
            .map(|tm| (tm.filename.clone(), tm.cache_path.clone()))
            .collect();
        if local_dir != root_mods_dir && local_dir.is_dir() {
            let mut entries = read_dir(local_dir).await?;
            while let Some(entry) = entries.next_entry().await? {
                let path = entry.path();
                let is_jar = path.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar"));
                if path.is_file() && is_jar {
                    if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                        wanted.push((name.to_string(), path.clone()));
                    }
                }
            }
        }
        let mut seen = HashSet::new();
        wanted.retain(|(name, _)| seen.insert(name.clone()));

        let list_path = root_mods_dir.join(FORGE_MANAGED_LIST);
        let previous: HashSet<String> = match fs::read_to_string(&list_path).await {
            Ok(text) => serde_json::from_str::<Vec<String>>(&text).unwrap_or_default().into_iter().collect(),
            Err(_) => HashSet::new(),
        };
        let wanted_names: HashSet<&String> = wanted.iter().map(|(n, _)| n).collect();

        for name in previous.iter().filter(|n| !wanted_names.contains(n)) {
            let path = root_mods_dir.join(name);
            if is_plain_file_name(name) && path.is_file() {
                // Salami: nicht endgültig löschen – die Liste liegt in der Instanz und könnte
                // manipuliert sein; der Papierkorb hält die Datei wiederherstellbar.
                info!("Removing previously managed Forge mod: {}", name);
                crate::utils::trash_utils::move_path_to_trash(&path, Some("forge-mods")).await?;
            }
        }

        let mut managed = Vec::new();
        for (name, source) in &wanted {
            if !is_plain_file_name(name) {
                warn!("Skipping mod with unexpected file name: {:?}", name);
                continue;
            }
            let target = root_mods_dir.join(name);
            if target.exists() {
                if !previous.contains(name) {
                    debug!("Keeping user-placed mod in mods/: {}", name);
                    continue;
                }
                if Self::is_file_valid(&target, source).await {
                    managed.push(name.clone());
                    continue;
                }
            }
            Self::robust_copy_file(source, &target).await?;
            managed.push(name.clone());
        }

        managed.sort();
        fs::write(&list_path, serde_json::to_string_pretty(&managed)?).await?;
        info!("Forge mods synced to {:?}: {} managed", root_mods_dir, managed.len());
        Ok(())
    }

    /// Removes managed mods from the profile mods directory.
    /// Uses SHA1 matching when available (Modrinth/CurseForge), falls back to filename for mods
    /// without a known hash (Maven/URL/local). User-added mods are left untouched.
    pub async fn clean_managed_mods(
        &self,
        target_mods: &[TargetMod],
        profile_mods_dir: &PathBuf,
    ) -> Result<()> {
        if !profile_mods_dir.exists() {
            return Ok(());
        }

        // Split target mods into SHA1-known and filename-only
        let known_hashes: HashSet<String> = target_mods
            .iter()
            .filter_map(|tm| tm.sha1.as_ref().map(|h| h.to_lowercase()))
            .collect();
        let filename_only: HashSet<String> = target_mods
            .iter()
            .filter(|tm| tm.sha1.is_none())
            .map(|tm| tm.filename.clone())
            .collect();

        // Collect all files in mods/
        let mut files: Vec<PathBuf> = Vec::new();
        let mut dir_entries = read_dir(&profile_mods_dir).await?;
        while let Some(entry) = dir_entries.next_entry().await? {
            let path = entry.path();
            if path.is_file() {
                files.push(path);
            }
        }

        if files.is_empty() {
            return Ok(());
        }

        // Compute SHA1 of all files in mods/ with bounded parallelism
        let hashed_files: Vec<(PathBuf, Option<String>)> = iter(files)
            .map(|path| async move {
                let hash = crate::utils::hash_utils::calculate_sha1_from_file(&path).await.ok();
                (path, hash)
            })
            .buffer_unordered(HASH_CONCURRENCY)
            .collect()
            .await;

        // Determine which files to remove
        let mut removed = 0;
        for (path, hash) in hashed_files {
            let filename = match path.file_name().and_then(|n| n.to_str()) {
                Some(name) => name.to_string(),
                None => continue,
            };

            let is_managed = if let Some(ref h) = hash {
                // SHA1 match: catches renamed files too
                if known_hashes.contains(&h.to_lowercase()) {
                    true
                } else {
                    // No SHA1 match, try filename for mods without known hash
                    filename_only.contains(&filename)
                }
            } else {
                // Hash computation failed, fall back to filename
                filename_only.contains(&filename)
            };

            if is_managed {
                info!("Moving managed mod to trash: {}", filename);
                crate::utils::trash_utils::move_path_to_trash(&path, Some("managed_mods")).await?;
                removed += 1;
            }
        }

        info!("Cleaned {} managed mods from mods/", removed);
        Ok(())
    }

    /// Downloads a file from a URL to a target path, optionally verifying its SHA1 hash.
    async fn download_and_verify_file(
        url: &str,
        target_path: &PathBuf,
        expected_sha1: Option<&str>,
        stats: Option<Arc<DownloadStats>>,
    ) -> Result<()> {
        // Use the new centralized download utility with SHA1 verification
        let mut config = DownloadConfig::new()
            .with_streaming(true)  // Mods can be large files
            .with_retries(3)      // Built-in retry logic for network issues
            .with_stats(stats);

        // Add SHA1 verification if provided
        if let Some(sha1) = expected_sha1 {
            config = config.with_sha1(sha1);
        }

        DownloadUtils::download_file(url, target_path, config).await
    }

    /// fix for https://github.com/NoRiskClient/issues/issues/1487
    /// Robust file copy operation with explicit disk sync to prevent corruption
    /// Fixes issue where JAR files appear complete but are actually corrupt due to unflushed buffers
    async fn robust_copy_file(source_path: &PathBuf, target_path: &PathBuf) -> Result<()> {
        
        debug!("Starting robust copy: {:?} -> {:?}", source_path, target_path);
        
        // Read the entire source file into memory
        let source_data = fs::read(source_path).await.map_err(|e| {
            AppError::Io(e)
        })?;
        
        // Create parent directories if they don't exist
        if let Some(parent) = target_path.parent() {
            fs::create_dir_all(parent).await.map_err(|e| {
                AppError::Io(e)
            })?;
        }
        
        // Create target file and write data
        let mut target_file = fs::File::create(target_path).await.map_err(|e| {
            AppError::Io(e)
        })?;
        
        target_file.write_all(&source_data).await.map_err(|e| {
            AppError::Io(e)
        })?;
        
        // CRITICAL: Ensure file is fully written to disk - prevents corruption
        target_file.sync_all().await.map_err(|e| {
            AppError::Io(e)
        })?;
        
        // Explicitly close the file handle
        drop(target_file);
        
        debug!("Robust copy completed: {} bytes", source_data.len());
        Ok(())
    }

    /// fix for https://github.com/NoRiskClient/issues/issues/1487
    /// Validates if a file in the profile directory is valid by comparing with cache version
    /// Checks file size and basic ZIP header for JAR files to detect corruption
    async fn is_file_valid(profile_file: &PathBuf, cache_file: &PathBuf) -> bool {
        // Check if both files exist
        if !profile_file.exists() || !cache_file.exists() {
            debug!("File validation failed: one or both files don't exist");
            return false;
        }

        // Compare file sizes - quick corruption detection
        match (fs::metadata(profile_file).await, fs::metadata(cache_file).await) {
            (Ok(profile_meta), Ok(cache_meta)) => {
                if profile_meta.len() != cache_meta.len() {
                    debug!(
                        "File size mismatch: profile={} vs cache={} for {:?}",
                        profile_meta.len(), cache_meta.len(), profile_file
                    );
                    return false;
                }
            }
            _ => {
                debug!("Failed to read file metadata for validation: {:?}", profile_file);
                return false;
            }
        }

        // For JAR files, check ZIP integrity (header + end record) to detect corruption
        if let Some(extension) = profile_file.extension() {
            if extension == "jar" {
                if !DownloadUtils::is_zip_file_complete(profile_file).await {
                    debug!("JAR file failed ZIP integrity check: {:?}", profile_file);
                    return false;
                }
            }
        }

        debug!("File validation passed: {:?}", profile_file);
        true
    }

}

#[cfg(test)]
mod forge_sync_tests {
    use super::*;

    fn target(dir: &std::path::Path, name: &str, content: &str) -> TargetMod {
        let cache_path = dir.join(name);
        std::fs::write(&cache_path, content).unwrap();
        TargetMod { mod_id: name.to_string(), filename: name.to_string(), cache_path, sha1: None }
    }

    #[tokio::test]
    async fn forge_sync_copies_tracks_and_keeps_user_mods() {
        let base = std::env::temp_dir().join(format!("salami-forge-sync-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        let cache = base.join("cache");
        let local = base.join("mods").join("salami-1.20.1-forge");
        let root = base.join("mods");
        std::fs::create_dir_all(&cache).unwrap();
        std::fs::create_dir_all(&local).unwrap();
        std::fs::write(root.join("user-mod.jar"), "mine").unwrap();
        std::fs::write(local.join("local-mod.jar"), "local").unwrap();
        std::fs::write(local.join("off-mod.jar.disabled"), "off").unwrap();

        let service = ModDownloadService::with_concurrency(1);
        let a = target(&cache, "a.jar", "A");
        let b = target(&cache, "b.jar", "B");
        service.sync_forge_mods(&[a.clone(), b], &local, &root).await.unwrap();
        assert_eq!(std::fs::read_to_string(root.join("a.jar")).unwrap(), "A");
        assert_eq!(std::fs::read_to_string(root.join("b.jar")).unwrap(), "B");
        assert_eq!(std::fs::read_to_string(root.join("local-mod.jar")).unwrap(), "local");
        assert!(!root.join("off-mod.jar.disabled").exists());

        // b.jar fällt weg → wird entfernt; die selbst abgelegte Mod bleibt
        service.sync_forge_mods(&[a], &local, &root).await.unwrap();
        assert!(!root.join("b.jar").exists());
        assert!(root.join("a.jar").exists());
        assert_eq!(std::fs::read_to_string(root.join("user-mod.jar")).unwrap(), "mine");

        // eine manipulierte Liste kann nichts außerhalb von mods/ löschen
        std::fs::write(base.join("outside.jar"), "x").unwrap();
        std::fs::write(root.join(FORGE_MANAGED_LIST), r#"["../outside.jar"]"#).unwrap();
        service.sync_forge_mods(&[], &local, &root).await.unwrap();
        assert!(base.join("outside.jar").exists());
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn plain_file_names_only() {
        assert!(is_plain_file_name("sodium-0.6.jar"));
        assert!(!is_plain_file_name("../x.jar"));
        assert!(!is_plain_file_name("sub/x.jar"));
        assert!(!is_plain_file_name("C:x.jar"));
        assert!(!is_plain_file_name(".."));
    }
}
