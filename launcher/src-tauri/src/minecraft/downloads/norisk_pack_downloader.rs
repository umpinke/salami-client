use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use crate::integrations::norisk_packs::{self, NoriskModSourceDefinition, NoriskModpacksConfig};
use crate::minecraft::launch::launch_summary::DownloadStats;
use crate::utils::download_utils::{DownloadConfig, DownloadUtils};
use futures::stream::{iter, StreamExt};
use log::{error, info, trace, warn};
use std::path::PathBuf;
use std::sync::Arc;
use tokio::fs;

const DEFAULT_CONCURRENT_MOD_DOWNLOADS: usize = 4;
const MOD_CACHE_DIR_NAME: &str = "mod_cache"; // Reuse the same cache directory
const MODRINTH_MAVEN_URL: &str = "https://api.modrinth.com/maven"; // Modrinth Maven repo

#[derive(Clone)]
pub struct NoriskPackDownloadService {
    concurrent_downloads: usize,
    stats: Option<Arc<DownloadStats>>,
}

impl NoriskPackDownloadService {
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

    /// Downloads mods specified in a Norisk pack definition to the central mod cache.
    /// Requires the pack config, the ID of the pack to download, the target Minecraft version, and loader.
    pub async fn download_pack_mods_to_cache(
        &self,
        config: &NoriskModpacksConfig,
        pack_id: &str,
        minecraft_version: &str,
        loader: &str,
    ) -> Result<()> {
        info!(
            "Checking/Downloading Norisk Pack mods to cache. Pack ID: '{}', MC: {}, Loader: {} (Concurrency: {})",
            pack_id, minecraft_version, loader, self.concurrent_downloads
        );

        let mod_cache_dir = LAUNCHER_DIRECTORY.meta_dir().join(MOD_CACHE_DIR_NAME);
        if !mod_cache_dir.exists() {
            info!("Creating mod cache directory: {:?}", mod_cache_dir);
            fs::create_dir_all(&mod_cache_dir).await?;
        }

        let pack_definition = config.get_resolved_pack_definition(pack_id)?;

        let mut download_futures = Vec::new();

        for mod_entry in &pack_definition.mods {
            let compatibility_target = match mod_entry
                .compatibility
                .get(minecraft_version)
                .and_then(|loader_map| loader_map.get(loader))
            {
                Some(target) => target.clone(),
                None => {
                    warn!(
                        "No compatible version found for mod '{}' (ID: {}) for MC {} / Loader {}. Skipping.",
                        mod_entry.display_name.as_deref().unwrap_or(&mod_entry.id),
                        mod_entry.id,
                        minecraft_version,
                        loader
                    );
                    continue;
                }
            };

            let cache_dir_clone = mod_cache_dir.clone();
            let source = mod_entry.source.clone();
            let mod_id = mod_entry.id.clone();
            let display_name_opt = mod_entry.display_name.clone();
            let target_clone = compatibility_target.clone();
            let stats = self.stats.clone();

            download_futures.push(async move {
                let display_name = display_name_opt.unwrap_or_else(|| mod_id.clone());
                let identifier = target_clone.identifier.clone(); // Keep identifier for version/URL

                // --- Proceed with download logic using derived/provided filename & identifier ---
                // Use source override from target if available, otherwise use the original source
                let effective_source = target_clone.source.as_ref().unwrap_or(&source);
                // Use the identifier from target (already cloned above as `identifier`)
                let effective_identifier = identifier;

                // --- Determine filename using the effective source ---
                let filename_result =
                    norisk_packs::get_norisk_pack_mod_filename(effective_source, &target_clone, &mod_id);

                // Check if filename retrieval was successful
                let filename = match filename_result {
                    Ok(fname) => fname,
                    Err(e) => {
                        // Log the error and skip this mod download
                        error!("Skipping download for mod '{}': {}", display_name, e);
                        return Err(e); // Return error to the future stream
                    }
                };

                let target_path = cache_dir_clone.join(&filename);

                match effective_source {
                    NoriskModSourceDefinition::Modrinth {
                        project_id: _project_id,
                        project_slug,
                    } => {
                        let group_id = "maven.modrinth".to_string();
                        let artifact_id = project_slug;
                        let version = effective_identifier;

                        Self::download_maven_mod(
                            MODRINTH_MAVEN_URL.to_string(),
                            group_id,
                            artifact_id.clone(),
                            version,
                            filename,
                            target_path,
                            None,
                            stats,
                        )
                        .await
                        .map_err(|e| {
                            error!("Failed to download Modrinth mod '{}': {}", display_name, e);
                            e
                        })?;

                        Ok(())
                    }
                    NoriskModSourceDefinition::Maven {
                        repository_ref,
                        group_id,
                        artifact_id,
                    } => {
                        // Get the repository URL
                        let repo_url = config
                            .repositories
                            .get(repository_ref)
                            .ok_or_else(|| {
                                AppError::Download(format!(
                                    "Repository reference '{}' not found for mod '{}'",
                                    repository_ref, display_name
                                ))
                            })?
                            .trim_end_matches('/')
                            .to_string();

                        Self::download_maven_mod(
                            repo_url,
                            group_id.clone(),
                            artifact_id.clone(),
                            effective_identifier,
                            filename,
                            target_path,
                            None,
                            stats,
                        )
                        .await
                        .map_err(|e| {
                            error!("Failed to download Maven mod '{}': {}", display_name, e);
                            e
                        })?;

                        Ok(())
                    }
                    NoriskModSourceDefinition::Url => {
                        // Salami: "file:<pfad>" = lokale Datei (relativer Pfad = relativ zum
                        // Launcher-Datenordner). Wird bei jedem Start neu kopiert, damit
                        // frische Dev-Builds von salami-core sofort im Spiel sind.
                        if let Some(local) = effective_identifier.strip_prefix("file:") {
                            let source_path = local_pack_mod_source(local);
                            info!("Copying local pack mod '{}' from {:?}", display_name, source_path);
                            if let Some(parent) = target_path.parent() {
                                fs::create_dir_all(parent).await?;
                            }
                            fs::copy(&source_path, &target_path).await.map_err(|e| {
                                AppError::Download(format!(
                                    "Local mod '{}' not found at {:?}: {} (build it with `gradlew installDev` in mod/, or reinstall the launcher)",
                                    display_name, source_path, e
                                ))
                            })?;
                            return Ok(());
                        }
                        // For URL mods, use the identifier as direct URL
                        trace!(
                            "Downloading URL mod for cache: {} ({}) from {}",
                            display_name, filename, effective_identifier
                        );

                        Self::download_and_verify_file(&effective_identifier, &target_path, None, stats).await
                            .map_err(|e| {
                                error!("Failed to download URL mod '{}': {}", display_name, e);
                                e
                            })?;

                        Ok(())
                    }
                }
            });
        }

        info!(
            "Executing {} Norisk pack mod cache tasks for pack '{}'...",
            download_futures.len(),
            pack_id
        );
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
            info!(
                "Norisk pack mod cache check/download process completed successfully for pack: '{}'",
                pack_id
            );
            Ok(())
        } else {
            error!(
                "Norisk pack mod cache check/download process completed with {} errors for pack: '{}'",
                errors.len(), pack_id
            );
            Err(errors.remove(0))
        }
    }

    /// Helper function to download a mod from a Maven repository.
    async fn download_maven_mod(
        repo_url: String,
        group_id: String,
        artifact_id: String,
        version: String,
        filename: String,
        target_path: PathBuf,
        expected_sha1: Option<&str>,
        stats: Option<Arc<DownloadStats>>,
    ) -> Result<()> {
        let group_path = group_id.replace('.', "/");
        let artifact_path = format!("{}/{}/{}/{}", group_path, artifact_id, version, filename);
        let download_url = format!("{}/{}", repo_url, artifact_path);

        trace!(
            "Preparing Maven mod for cache: {} (Group: {}, Artifact: {}, Version: {}) from {}",
            filename, group_id, artifact_id, version, repo_url
        );

        Self::download_and_verify_file(&download_url, &target_path, expected_sha1, stats).await
    }

    /// Downloads a file from a URL to a target path, optionally verifying its SHA1 hash.
    async fn download_and_verify_file(
        url: &str,
        target_path: &PathBuf,
        expected_sha1: Option<&str>,
        stats: Option<Arc<DownloadStats>>,
    ) -> Result<()> {
        // Explicit hash wins; else best-effort Maven `.sha1` sidecar (None = ZIP heuristic fallback).
        let resolved_sha1 = match expected_sha1 {
            Some(hash) => Some(hash.to_string()),
            None => DownloadUtils::try_fetch_sha1_sidecar(url).await,
        };

        let mut config = DownloadConfig::new()
            .with_streaming(true)  // Use streaming for potentially large mod files
            .with_retries(3)
            .with_stats(stats);

        if let Some(hash) = resolved_sha1 {
            config = config.with_sha1(hash);
        }

        DownloadUtils::download_file(url, target_path, config).await
    }


}

/// Ordner neben der Launcher-Exe, in den der Installer mitgelieferte Mods legt
/// (`bundle.resources` in tauri.release-local.json).
const BUNDLED_MODS_DIR: &str = "mods";

/// Salami: Quelle einer "file:"-Packmod.
/// 1. der angegebene Pfad – relativ zum Launcher-Datenordner (`dev/salami-core.jar` von
///    `gradlew installDev`) oder absolut (`file:///C:/x.jar` bzw. `file:///home/x.jar`)
/// 2. die mit dem Installer ausgelieferte Kopie `<exe-Ordner>/mods/<dateiname>`
/// Gibt es beide, gewinnt die neuere – so verdeckt weder ein alter Dev-Build ein
/// Launcher-Update noch ein altes Update einen frischen Dev-Build.
fn local_pack_mod_source(local: &str) -> PathBuf {
    let raw = local.strip_prefix("//").unwrap_or(local);
    let raw = if cfg!(windows) { raw.trim_start_matches('/') } else { raw };
    let p = PathBuf::from(raw);
    let explicit = if p.is_absolute() { p } else { LAUNCHER_DIRECTORY.root_dir().join(p) };
    let bundled = std::env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(|dir| dir.join(BUNDLED_MODS_DIR)))
        .zip(explicit.file_name())
        .map(|(dir, name)| dir.join(name));
    newest_source(explicit, bundled)
}

fn newest_source(explicit: PathBuf, bundled: Option<PathBuf>) -> PathBuf {
    let modified = |p: &PathBuf| std::fs::metadata(p).and_then(|m| m.modified()).ok();
    let Some(bundled) = bundled else {
        return explicit;
    };
    match (modified(&explicit), modified(&bundled)) {
        (Some(e), Some(b)) if b > e => bundled,
        (None, Some(_)) => bundled,
        _ => explicit,
    }
}

#[cfg(test)]
mod tests {
    use super::newest_source;
    use std::fs::File;
    use std::path::PathBuf;
    use std::time::{Duration, SystemTime};

    fn file_with_age(dir: &PathBuf, name: &str, age_secs: u64) -> PathBuf {
        let path = dir.join(name);
        let file = File::create(&path).unwrap();
        file.set_modified(SystemTime::now() - Duration::from_secs(age_secs)).unwrap();
        path
    }

    fn temp_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("salami-pack-src-{}-{}", tag, std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn newer_bundled_jar_wins_over_stale_dev_build() {
        let dir = temp_dir("bundled-newer");
        let dev = file_with_age(&dir, "dev.jar", 3600);
        let bundled = file_with_age(&dir, "bundled.jar", 10);
        assert_eq!(newest_source(dev, Some(bundled.clone())), bundled);
    }

    #[test]
    fn newer_dev_build_wins_over_bundled_jar() {
        let dir = temp_dir("dev-newer");
        let dev = file_with_age(&dir, "dev.jar", 10);
        let bundled = file_with_age(&dir, "bundled.jar", 3600);
        assert_eq!(newest_source(dev.clone(), Some(bundled)), dev);
    }

    #[test]
    fn falls_back_to_whichever_exists() {
        let dir = temp_dir("fallback");
        let missing = dir.join("missing.jar");
        let bundled = file_with_age(&dir, "bundled.jar", 10);
        assert_eq!(newest_source(missing.clone(), Some(bundled.clone())), bundled);
        assert_eq!(newest_source(bundled.clone(), Some(missing.clone())), bundled);
        // nichts da: der angegebene Pfad, damit die Fehlermeldung ihn nennt
        assert_eq!(newest_source(missing.clone(), None), missing);
    }
}

// Note: Syncing logic (like `sync_mods_to_profile` from ModDownloadService)
// is not included here as it depends on a specific Profile's mod list,
// not directly on the Norisk Pack definition. Syncing would still use
// ModDownloadService after the Profile's mod list has been potentially
// updated based on a selected Norisk Pack.
