use crate::error::Result;
use crate::integrations::norisk_packs::{self, NoriskModSourceDefinition, NoriskModpacksConfig};
use crate::state::profile_state::{
    self, CustomModInfo, Mod, ModLoader, ModSource, NoriskModIdentifier, Profile,
};
use log::{debug, info, trace, warn};
use std::collections::HashMap;
use std::path::PathBuf;
use uuid::Uuid;
use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use tokio::fs;

/// Where a resolved mod comes from. The order is the priority when the same mod turns up twice:
/// the player's own jars beat profile mods, and both beat the mods the Salami pack brings along.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum ModOrigin {
    Pack,
    Profile,
    Local,
}

// --- Struct for resolved mods ---
#[derive(Debug, Clone)]
pub struct TargetMod {
    // Make fields public so mod_downloader can access them
    pub mod_id: String, // Canonical Key (e.g., "modrinth:AANobbMI")
    pub filename: String,
    pub cache_path: PathBuf,
    pub sha1: Option<String>, // Known SHA1 from Modrinth/CurseForge (None for Maven/URL/local)
    pub origin: ModOrigin,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ModResolutionStatus {
    #[default]
    Unknown,
    Included,
    Overridden,
    UserDisabled,
    NoCompatibleVersion,
    GameVersionMismatch,
    LoaderMismatch,
    NoAssociatedLoader,
    UnsupportedSource,
    FilenameUnresolved,
    MissingFromCache,
    ManagedElsewhere,
    NotDelivered,
    PackResolveFailed,
}

impl ModResolutionStatus {
    pub fn as_wire(self) -> &'static str {
        match self {
            Self::Unknown => "unknown",
            Self::Included => "included",
            Self::Overridden => "overridden",
            Self::UserDisabled => "user_disabled",
            Self::NoCompatibleVersion => "no_compatible_version",
            Self::GameVersionMismatch => "game_version_mismatch",
            Self::LoaderMismatch => "loader_mismatch",
            Self::NoAssociatedLoader => "no_associated_loader",
            Self::UnsupportedSource => "unsupported_source",
            Self::FilenameUnresolved => "filename_unresolved",
            Self::MissingFromCache => "missing_from_cache",
            Self::ManagedElsewhere => "managed_elsewhere",
            Self::NotDelivered => "not_delivered",
            Self::PackResolveFailed => "pack_resolve_failed",
        }
    }

    pub fn in_launch_set(self) -> Option<bool> {
        match self {
            Self::Unknown | Self::ManagedElsewhere => None,
            Self::Included | Self::Overridden => Some(true),
            _ => Some(false),
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct ModOutcome {
    pub status: ModResolutionStatus,
    pub canonical_key: Option<String>,
    pub filename: Option<String>,
    pub version: Option<String>,
    pub overridden_by: Option<String>,
}

impl ModOutcome {
    pub fn skipped(status: ModResolutionStatus) -> Self {
        Self {
            status,
            ..Default::default()
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct ModResolutionReport {
    minecraft_version: String,
    loader: String,
    pack_resolve_failed: bool,
    pack: HashMap<String, ModOutcome>,
    profile: HashMap<Uuid, ModOutcome>,
}

impl ModResolutionReport {
    fn new(minecraft_version: &str, loader: &str) -> Self {
        Self {
            minecraft_version: minecraft_version.to_string(),
            loader: loader.to_string(),
            ..Default::default()
        }
    }

    pub fn matches(&self, minecraft_version: &str, loader: &str) -> bool {
        self.minecraft_version == minecraft_version && self.loader == loader
    }

    pub fn pack_resolve_failed(&self) -> bool {
        self.pack_resolve_failed
    }

    pub fn pack_mod(&self, id: &str) -> Option<&ModOutcome> {
        self.pack.get(id)
    }

    pub fn profile_mod(&self, id: &Uuid) -> Option<&ModOutcome> {
        self.profile.get(id)
    }

    fn outcomes_mut(&mut self) -> impl Iterator<Item = &mut ModOutcome> {
        self.pack.values_mut().chain(self.profile.values_mut())
    }

    fn resolve_overrides(&mut self, final_mods: &HashMap<String, TargetMod>) {
        for outcome in self.outcomes_mut() {
            if outcome.status != ModResolutionStatus::Included {
                continue;
            }
            let winner = outcome
                .canonical_key
                .as_ref()
                .and_then(|key| final_mods.get(key))
                .filter(|winner| Some(&winner.filename) != outcome.filename.as_ref());
            if let Some(winner) = winner {
                outcome.status = ModResolutionStatus::Overridden;
                outcome.overridden_by = Some(winner.filename.clone());
                outcome.version = None;
            }
        }
    }

    /// A mod that was resolved but dropped because another jar provides the same Fabric mod.
    fn mark_duplicate(&mut self, dropped: &TargetMod, winner_filename: &str) {
        for outcome in self.outcomes_mut() {
            if outcome.status == ModResolutionStatus::Included
                && outcome.canonical_key.as_deref() == Some(dropped.mod_id.as_str())
                && outcome.filename.as_deref() == Some(dropped.filename.as_str())
            {
                outcome.status = ModResolutionStatus::Overridden;
                outcome.overridden_by = Some(winner_filename.to_string());
                outcome.version = None;
            }
        }
    }

    pub fn mark_all_undelivered(&mut self) {
        for outcome in self.outcomes_mut() {
            if outcome.status.in_launch_set() == Some(true) {
                outcome.status = ModResolutionStatus::NotDelivered;
            }
        }
    }
}

// --- Helper: Get Canonical Key ---
fn get_canonical_key(source: &NoriskModSourceDefinition, mod_id: &str) -> Option<String> {
    match source {
        NoriskModSourceDefinition::Modrinth { project_id, .. } => {
            Some(format!("modrinth:{}", project_id))
        }
        NoriskModSourceDefinition::Url { .. } => Some(format!("url:{}", mod_id)),
        NoriskModSourceDefinition::Maven {
            group_id,
            artifact_id,
            ..
        } => Some(format!("maven:{}:{}", group_id, artifact_id)),
    }
}

fn get_canonical_key_profile(source: &ModSource) -> Option<String> {
    match source {
        ModSource::Modrinth { project_id, .. } => Some(format!("modrinth:{}", project_id)),
        ModSource::CurseForge { project_id, .. } => Some(format!("curseforge:{}", project_id)),
        ModSource::Url { url, .. } => Some(format!("url:{}", url)),
        ModSource::Maven { coordinates, .. } => Some(format!("maven:{}", coordinates)),
        _ => None,
    }
}

fn get_sha1_from_source(source: &ModSource) -> Option<String> {
    match source {
        ModSource::Modrinth { file_hash_sha1, .. } => file_hash_sha1.clone(),
        ModSource::CurseForge { file_hash_sha1, .. } => file_hash_sha1.clone(),
        _ => None,
    }
}

// --- Unified helper function to add a mod to final_mods with all necessary checks ---
#[must_use]
async fn try_add_mod_to_final_list(
    canonical_key: String,
    filename: String,
    mod_cache_dir: &PathBuf,
    final_mods: &mut HashMap<String, TargetMod>,
    mod_type_str: &str,
    sha1: Option<String>, // Known SHA1 hash (Modrinth/CurseForge)
    origin: ModOrigin,
) -> ModResolutionStatus {
    // 1. Check if file exists in cache
    let cache_path = mod_cache_dir.join(&filename);
    if !cache_path.exists() {
        warn!(
            "{} mod '{}' not found in cache at: {:?}. Skipping.",
            mod_type_str, filename, cache_path
        );
        return ModResolutionStatus::MissingFromCache;
    }

    // 2. Add to final mods
    if final_mods.contains_key(&canonical_key) {
        info!(
            "Overriding pack {} mod with key '{}' with version: {}",
            mod_type_str, canonical_key, filename
        );
    } else {
        trace!(
            "Adding {} mod to list: {}",
            mod_type_str, filename
        );
    }
    
    final_mods.insert(
        canonical_key.clone(),
        TargetMod {
            mod_id: canonical_key,
            filename,
            cache_path,
            sha1,
            origin,
        },
    );

    ModResolutionStatus::Included
}

async fn resolve_pack_entry(
    mod_entry: &norisk_packs::NoriskModEntryDefinition,
    pack_id: &str,
    profile: &Profile,
    minecraft_version: &str,
    loader_str: &str,
    mod_cache_dir: &PathBuf,
    final_mods: &mut HashMap<String, TargetMod>,
) -> ModOutcome {
    let mod_name = mod_entry.display_name.as_deref().unwrap_or(&mod_entry.id);

    match ModLoader::from_str(loader_str) {
        Ok(loader_enum) => {
            let identifier = NoriskModIdentifier {
                pack_id: pack_id.to_string(),
                mod_id: mod_entry.id.clone(),
                game_version: minecraft_version.to_string(),
                loader: loader_enum,
            };

            if profile.disabled_norisk_mods_detailed.contains(&identifier) {
                info!(
                    "Skipping pack mod '{}' (ID: {}) because it is disabled for profile '{}' context (MC: {}, Loader: {:?})",
                    mod_name, mod_entry.id, profile.name, minecraft_version, loader_enum
                );
                return ModOutcome::skipped(ModResolutionStatus::UserDisabled);
            }
        }
        Err(_) => {
            warn!("Invalid loader string '{}' during disabled check for pack mod '{}'. Cannot check disabled status.", loader_str, mod_entry.id);
        }
    }

    let target = match mod_entry.target_for(minecraft_version, loader_str) {
        Some(target) => target,
        None => {
            warn!(
                "Pack mod '{}' (ID: {}) has no compatibility entry for {} / {}. Not launched.",
                mod_name, mod_entry.id, minecraft_version, loader_str
            );
            return ModOutcome::skipped(ModResolutionStatus::NoCompatibleVersion);
        }
    };

    let (mod_type_str, effective_source) = match &mod_entry.source {
        NoriskModSourceDefinition::Modrinth { .. } => (
            "pack Modrinth",
            target.source.as_ref().unwrap_or(&mod_entry.source),
        ),
        NoriskModSourceDefinition::Url { .. } => ("pack URL", &mod_entry.source),
        NoriskModSourceDefinition::Maven { .. } => (
            "pack Maven",
            target.source.as_ref().unwrap_or(&mod_entry.source),
        ),
    };

    let mut outcome = ModOutcome {
        version: match effective_source {
            NoriskModSourceDefinition::Maven { .. } => Some(target.identifier.clone()),
            _ => None,
        },
        ..Default::default()
    };

    let canonical_key = match get_canonical_key(effective_source, &mod_entry.id) {
        Some(key) => key,
        None => {
            warn!(
                "Could not build canonical key for pack mod '{}' (ID: {}). Skipping.",
                mod_name, mod_entry.id
            );
            outcome.status = ModResolutionStatus::UnsupportedSource;
            return outcome;
        }
    };
    outcome.canonical_key = Some(canonical_key.clone());

    let filename =
        match norisk_packs::get_norisk_pack_mod_filename(effective_source, target, &mod_entry.id) {
            Ok(filename) => filename,
            Err(e) => {
                warn!(
                    "Could not determine filename for {} mod '{}' (ID: {}): {}. Skipping.",
                    mod_type_str, mod_name, mod_entry.id, e
                );
                outcome.status = ModResolutionStatus::FilenameUnresolved;
                return outcome;
            }
        };
    outcome.filename = Some(filename.clone());

    outcome.status = try_add_mod_to_final_list(
        canonical_key,
        filename,
        mod_cache_dir,
        final_mods,
        mod_type_str,
        None,
        ModOrigin::Pack,
    )
    .await;

    outcome
}

async fn resolve_profile_mod(
    mod_info: &Mod,
    profile: &Profile,
    minecraft_version: &str,
    mod_cache_dir: &PathBuf,
    final_mods: &mut HashMap<String, TargetMod>,
) -> ModOutcome {
    let mod_id_string = mod_info.id.to_string();
    let mod_name = mod_info.display_name.as_deref().unwrap_or(&mod_id_string);

    if !mod_info.enabled {
        debug!("Skipping disabled profile mod: {}", mod_name);
        return ModOutcome::skipped(ModResolutionStatus::UserDisabled);
    }

    if let Some(mod_gv_list) = &mod_info.game_versions {
        let mc_ver = minecraft_version.to_string();
        if !mod_gv_list.is_empty()
            && !mod_gv_list.contains(&mc_ver)
            && !mod_info.force_include_versions.contains(&mc_ver)
        {
            debug!(
                "Skipping profile mod '{}' (intended for MC {:?}, force={:?}) because target version is {}",
                mod_name, mod_gv_list, mod_info.force_include_versions, minecraft_version
            );
            return ModOutcome::skipped(ModResolutionStatus::GameVersionMismatch);
        }
    }

    let profile_loader = profile.loader;
    match mod_info.associated_loader {
        Some(mod_loader) => {
            if !mod_loader.runs_on(profile_loader) {
                debug!(
                    "Skipping profile mod '{}' (intended for loader {:?}) because profile loader is {:?}",
                    mod_name, mod_loader, profile_loader
                );
                return ModOutcome::skipped(ModResolutionStatus::LoaderMismatch);
            }
        }
        None => {
            debug!(
                "Skipping profile mod '{}' because it lacks an associated loader.",
                mod_name
            );
            return ModOutcome::skipped(ModResolutionStatus::NoAssociatedLoader);
        }
    }

    let mod_type_str = match &mod_info.source {
        ModSource::Modrinth { .. } => "profile Modrinth",
        ModSource::CurseForge { .. } => "profile CurseForge",
        ModSource::Url { .. } => "profile URL",
        ModSource::Maven { .. } => "profile Maven",
        ModSource::Local { .. } | ModSource::Embedded { .. } => {
            debug!(
                "Ignoring profile mod of type {:?} during resolution.",
                mod_info.source.clone()
            );
            return ModOutcome::skipped(ModResolutionStatus::ManagedElsewhere);
        }
    };

    let mut outcome = ModOutcome::default();

    let canonical_key = match get_canonical_key_profile(&mod_info.source) {
        Some(key) => key,
        None => {
            warn!(
                "Could not get canonical key for profile mod: {:?}",
                mod_info.source
            );
            outcome.status = ModResolutionStatus::UnsupportedSource;
            return outcome;
        }
    };
    outcome.canonical_key = Some(canonical_key.clone());

    let filename = match profile_state::get_profile_mod_filename(&mod_info.source) {
        Ok(filename) => filename,
        Err(e) => {
            warn!(
                "Could not determine filename for profile mod '{}': {}. Skipping.",
                mod_name, e
            );
            outcome.status = ModResolutionStatus::FilenameUnresolved;
            return outcome;
        }
    };
    outcome.filename = Some(filename.clone());

    outcome.status = try_add_mod_to_final_list(
        canonical_key,
        filename,
        mod_cache_dir,
        final_mods,
        mod_type_str,
        get_sha1_from_source(&mod_info.source),
        ModOrigin::Profile,
    )
    .await;

    outcome
}

// --- Helper function to resolve the final list of mods (Focus on Modrinth) ---
// Renamed loader parameter to loader_str for clarity
pub async fn resolve_target_mods(
    profile: &Profile,
    norisk_config: Option<&NoriskModpacksConfig>,
    custom_mod_infos: Option<&[CustomModInfo]>,
    extra_mods: &[Mod],
    minecraft_version: &str,
    loader_str: &str,
    mod_cache_dir: &PathBuf,
) -> Result<(Vec<TargetMod>, ModResolutionReport)> {
    let mut final_mods: HashMap<String, TargetMod> = HashMap::new(); // Key: Canonical Mod Identifier
    let mut report = ModResolutionReport::new(minecraft_version, loader_str);

    // 1. Process Pack Mods (Only Modrinth)
    let effective_pack_id = profile.effective_norisk_pack_id().await;
    if let (Some(pack_id), Some(config)) = (effective_pack_id.as_ref(), norisk_config) {
        info!("Resolving mods from selected Norisk Pack: '{}'", pack_id);
        match config.get_resolved_pack_definition(pack_id) {
            Ok(pack_definition) => {
                for mod_entry in &pack_definition.mods {
                    let outcome = resolve_pack_entry(
                        mod_entry,
                        pack_id,
                        profile,
                        minecraft_version,
                        loader_str,
                        mod_cache_dir,
                        &mut final_mods,
                    )
                    .await;
                    report.pack.insert(mod_entry.id.clone(), outcome);
                }
            }
            Err(e) => {
                warn!(
                    "Could not resolve Norisk Pack definition for pack ID '{}': {}. Skipping pack mods.",
                    pack_id, e
                );
                report.pack_resolve_failed = true;
            }
        }
    }

    // 2. Process Profile Mods (Only Modrinth for Overrides)
    info!(
        "Resolving manually added/overridden mods for profile: '{}'",
        profile.name
    );
    for mod_info in extra_mods.iter().chain(profile.mods.iter()) {
        let outcome = resolve_profile_mod(
            mod_info,
            profile,
            minecraft_version,
            mod_cache_dir,
            &mut final_mods,
        )
        .await;
        report.profile.insert(mod_info.id, outcome);
    }

    // 3. Process Custom Mods (Add if enabled)
    info!(
        "Resolving custom (local) mods for profile: '{}'",
        profile.name
    );
    if let Some(custom_mods) = custom_mod_infos {
        let mut custom_mods_added = 0;
        for info in custom_mods {
            if info.is_enabled {
                // Create a unique key for the HashMap
                let canonical_key = format!("local:{}", info.filename);

                // Custom mods use direct path, not cache path - no exists() check needed
                let target = TargetMod {
                    mod_id: canonical_key.clone(),
                    filename: info.filename.clone(),
                    cache_path: info.path.clone(),
                    sha1: None, // Local custom mods don't have known SHA1
                    origin: ModOrigin::Local,
                };

                // Use the unique canonical key
                if final_mods.insert(canonical_key.clone(), target).is_none() {
                    debug!(
                        "Adding enabled custom mod to target list: {}",
                        info.filename
                    );
                    custom_mods_added += 1;
                } else {
                    // This should not happen if canonical keys are unique, but log just in case
                    warn!("Custom mod canonical key collision: {}", canonical_key);
                }
            } else {
                debug!("Skipping disabled custom mod: {}", info.filename);
            }
        }
        info!(
            "Added {} enabled custom mods to the target list.",
            custom_mods_added
        );
    } else {
        info!("No custom mod information provided for resolving.");
    }

    report.resolve_overrides(&final_mods);

    let final_target_list: Vec<TargetMod> = final_mods.into_values().collect();
    info!(
        "Resolved {} total target mods for sync (incl. custom & overrides).",
        final_target_list.len()
    );
    debug!("Final target mods for sync: {:?}", final_target_list);
    Ok((final_target_list, report))
}

/// Collects loose jars sitting directly in `<instance>/mods`.
///
/// Fabric normally treats that directory as *the* mods folder, but we point
/// `fabric.modsFolder` at the launcher-managed per-profile subdirectory, and
/// `FabricLoaderImpl.getModsDirectory0()` picks one or the other — it does not merge:
///
/// ```text
/// return directory != null ? Paths.get(directory) : gameDir.resolve("mods");
/// ```
///
/// So anything dropped into `mods/` — a jar the user dragged in, or the `overrides/mods`
/// of an installed .mrpack — silently never loads. Forge/NeoForge do not have that problem:
/// FML scans `<instance>/mods` itself, and our own loader merges `nrc.modsFolder` with
/// `nrc.addMods`. Feeding these jars through addMods gives Fabric the same behaviour.
///
/// Only the top level is scanned, so the managed `nrc-<mc>-<loader>[-<uuid>]` subdirectory
/// (and any other subfolder) is left alone.
pub async fn collect_instance_root_mods(instance_path: &std::path::Path) -> Vec<PathBuf> {
    let mods_dir = instance_path.join("mods");
    let mut out = Vec::new();

    let mut entries = match fs::read_dir(&mods_dir).await {
        Ok(e) => e,
        Err(_) => return out, // No mods dir yet — nothing to add.
    };
    while let Ok(Some(entry)) = entries.next_entry().await {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        // Anything not ending in .jar is skipped, which also covers the `.disabled`
        // convention launchers use to park a mod without deleting it.
        if path
            .extension()
            .and_then(|e| e.to_str())
            .map_or(true, |e| !e.eq_ignore_ascii_case("jar"))
        {
            continue;
        }
        out.push(path);
    }
    out.sort();
    out
}

/// Creates a Fabric addMods meta file that lists one absolute path per line for the provided
/// target mods, followed by any loose jars from `<instance>/mods` (see
/// [`collect_instance_root_mods`]). Returns the absolute path to the created meta file.
pub async fn create_fabric_add_mods_meta(
    profile_id: Uuid,
    minecraft_version: &str,
    target_mods: &[TargetMod],
    extra_jars: &[PathBuf],
) -> crate::error::Result<PathBuf> {
    let runtime_dir = LAUNCHER_DIRECTORY.meta_dir().join("runtime");
    fs::create_dir_all(&runtime_dir).await?;

    let meta_file_path = runtime_dir.join(format!(
        "salami_fabric_mods_{}_{}.txt",
        profile_id, minecraft_version
    ));

    let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
    let mut meta_contents = String::new();
    for p in target_mods
        .iter()
        .map(|tm| tm.cache_path.to_string_lossy().replace("\\", "/"))
        .chain(
            extra_jars
                .iter()
                .map(|p| p.to_string_lossy().replace("\\", "/")),
        )
    {
        // A managed mod could also sit in mods/ — list it only once.
        if !seen.insert(p.clone()) {
            continue;
        }
        meta_contents.push_str(&p);
        meta_contents.push('\n');
    }
    fs::write(&meta_file_path, meta_contents).await?;
    Ok(meta_file_path)
}

/// Creates the meta file and returns the formatted JVM argument string for Fabric addMods
pub async fn build_fabric_add_mods_arg(
    profile_id: Uuid,
    minecraft_version: &str,
    target_mods: &[TargetMod],
    extra_jars: &[PathBuf],
) -> crate::error::Result<String> {
    let meta =
        create_fabric_add_mods_meta(profile_id, minecraft_version, target_mods, extra_jars).await?;
    Ok(format!(
        "-Dfabric.addMods=@{}",
        meta.to_string_lossy().replace("\\", "/")
    ))
}

/// `id` and `version` from a jar's `fabric.mod.json`. None when the jar has none or it cannot
/// be read; such jars are never treated as duplicates.
fn read_fabric_mod_meta(path: &std::path::Path) -> Option<(String, String)> {
    use std::io::Read;
    let file = std::fs::File::open(path).ok()?;
    let mut archive = zip::ZipArchive::new(std::io::BufReader::new(file)).ok()?;
    let mut entry = archive.by_name("fabric.mod.json").ok()?;
    if entry.size() > 1024 * 1024 {
        return None;
    }
    let mut text = String::new();
    entry.read_to_string(&mut text).ok()?;
    // Fabric reads this file leniently and some mods have raw line breaks inside strings,
    // which strict JSON rejects. Outside of strings they are only whitespace.
    let cleaned: String = text
        .trim_start_matches('\u{feff}')
        .chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .collect();
    let json: serde_json::Value = serde_json::from_str(&cleaned).ok()?;
    let id = json.get("id")?.as_str()?.trim();
    if id.is_empty() {
        return None;
    }
    let version = json
        .get("version")
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .trim()
        .to_string();
    Some((id.to_string(), version))
}

/// Orders two mod versions. Semantic versions compare by precedence (build metadata such as
/// `+mc26.3` is ignored); anything else by its numbers, then by text.
fn compare_mod_versions(a: &str, b: &str) -> std::cmp::Ordering {
    if let (Ok(va), Ok(vb)) = (semver::Version::parse(a), semver::Version::parse(b)) {
        return va.cmp_precedence(&vb);
    }
    let numbers = |s: &str| -> Vec<u64> {
        s.split('+')
            .next()
            .unwrap_or("")
            .split(|c: char| !c.is_ascii_digit())
            .filter(|part| !part.is_empty())
            .map(|part| part.parse::<u64>().unwrap_or(u64::MAX))
            .collect()
    };
    numbers(a).cmp(&numbers(b)).then_with(|| a.cmp(b))
}

/// Libraries other mods are compiled against: of two copies, the newer one is loaded whatever its
/// origin, so nothing runs against an older API than it was built for.
const NEWEST_WINS_MOD_IDS: &[&str] = &[
    "fabric-api",
    "fabric-language-kotlin",
    "cloth-config",
    "yet_another_config_lib_v3",
    "architectury",
    "modmenu",
];

/// Fabric refuses to start when two jars bring the same mod in the same version ("Replace mod
/// 'Sodium' and mod 'Sodium' with mod 'Sodium'"), which happens when a mod is installed from both
/// Modrinth and CurseForge, or when a modpack brings its own copy of a mod the Salami pack also
/// adds. Keeps one jar per Fabric mod id: the player's own jars beat profile mods, profile mods
/// beat pack mods, and between equals the newer version wins (the first one on a tie). Libraries in
/// [`NEWEST_WINS_MOD_IDS`] always load in their newest version.
/// Jars without a readable `fabric.mod.json` are always kept.
pub async fn drop_duplicate_fabric_mods(
    target_mods: &[TargetMod],
    extra_jars: &[PathBuf],
    report: &mut ModResolutionReport,
) -> (Vec<TargetMod>, Vec<PathBuf>) {
    let paths: Vec<PathBuf> = target_mods
        .iter()
        .map(|m| m.cache_path.clone())
        .chain(extra_jars.iter().cloned())
        .collect();
    let metas: Vec<Option<(String, String)>> = match tokio::task::spawn_blocking(move || {
        paths.iter().map(|p| read_fabric_mod_meta(p)).collect::<Vec<_>>()
    })
    .await
    {
        Ok(metas) => metas,
        Err(e) => {
            warn!("Could not read mod ids for the duplicate check: {}", e);
            return (target_mods.to_vec(), extra_jars.to_vec());
        }
    };

    let count = target_mods.len();
    let origin_of = |i: usize| {
        if i < count {
            target_mods[i].origin
        } else {
            ModOrigin::Local
        }
    };
    let path_of = |i: usize| -> &std::path::Path {
        if i < count {
            &target_mods[i].cache_path
        } else {
            &extra_jars[i - count]
        }
    };
    let name_of = |i: usize| -> String {
        if i < count {
            target_mods[i].filename.clone()
        } else {
            extra_jars[i - count]
                .file_name()
                .map(|n| n.to_string_lossy().into_owned())
                .unwrap_or_default()
        }
    };

    let mut winners: HashMap<&str, usize> = HashMap::new();
    for (i, meta) in metas.iter().enumerate() {
        let Some((id, version)) = meta else { continue };
        match winners.get(id.as_str()) {
            None => {
                winners.insert(id.as_str(), i);
            }
            Some(&current) => {
                let current_version = metas[current].as_ref().map(|m| m.1.as_str()).unwrap_or("");
                let by_version = || compare_mod_versions(version, current_version);
                let by_origin = || origin_of(i).cmp(&origin_of(current));
                // Libraries: the newest copy, because other mods (and salami-core) are built against
                // a recent API. Other mods: the player's/modpack's own copy, which a modpack may pin
                // on purpose (e.g. Sodium matching its Iris).
                let order = if NEWEST_WINS_MOD_IDS.contains(&id.as_str()) {
                    by_version().then_with(by_origin)
                } else {
                    by_origin().then_with(by_version)
                };
                let better = order == std::cmp::Ordering::Greater;
                if better {
                    winners.insert(id.as_str(), i);
                }
            }
        }
    }

    let mut dropped = vec![false; metas.len()];
    for (i, meta) in metas.iter().enumerate() {
        let Some((id, version)) = meta else { continue };
        let winner = winners[id.as_str()];
        // The same file listed twice is harmless: the addMods file lists every path once.
        if winner == i || path_of(winner) == path_of(i) {
            continue;
        }
        dropped[i] = true;
        let winner_name = name_of(winner);
        let winner_version = metas[winner].as_ref().map(|m| m.1.as_str()).unwrap_or("");
        warn!(
            "[Mods] '{}' is there twice: loading {} ({}), skipping {} ({})",
            id,
            winner_name,
            winner_version,
            name_of(i),
            version
        );
        if i < count {
            report.mark_duplicate(&target_mods[i], &winner_name);
        }
    }

    let kept_targets = target_mods
        .iter()
        .enumerate()
        .filter(|(i, _)| !dropped[*i])
        .map(|(_, m)| m.clone())
        .collect();
    let kept_extra = extra_jars
        .iter()
        .enumerate()
        .filter(|(i, _)| !dropped[count + *i])
        .map(|(_, p)| p.clone())
        .collect();
    (kept_targets, kept_extra)
}

#[cfg(test)]
mod duplicate_tests {
    use super::*;
    use std::io::Write;

    fn jar(dir: &std::path::Path, name: &str, fabric_mod_json: Option<&str>) -> PathBuf {
        let path = dir.join(name);
        let file = std::fs::File::create(&path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default();
        if let Some(json) = fabric_mod_json {
            zip.start_file("fabric.mod.json", options).unwrap();
            zip.write_all(json.as_bytes()).unwrap();
        }
        zip.start_file("x.class", options).unwrap();
        zip.write_all(b"x").unwrap();
        zip.finish().unwrap();
        path
    }

    fn target(path: PathBuf, key: &str, origin: ModOrigin) -> TargetMod {
        TargetMod {
            mod_id: key.to_string(),
            filename: path.file_name().unwrap().to_string_lossy().into_owned(),
            cache_path: path,
            sha1: None,
            origin,
        }
    }

    fn names(mods: &[TargetMod]) -> Vec<String> {
        mods.iter().map(|m| m.filename.clone()).collect()
    }

    #[tokio::test]
    async fn keeps_one_jar_per_mod_id() {
        let dir = std::env::temp_dir().join(format!("salami-dupes-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let sodium = |v: &str| format!(r#"{{"schemaVersion":1,"id":"sodium","version":"{}"}}"#, v);

        let pack_sodium = jar(&dir, "sodium-pack.jar", Some(&sodium("0.9.3-beta.5+mc26.3")));
        let cf_sodium = jar(&dir, "sodium-cf.jar", Some(&sodium("0.9.3-beta.4+mc26.3")));
        let mr_sodium = jar(&dir, "sodium-mr.jar", Some(&sodium("0.9.3-beta.4+mc26.3")));
        let old_cloth = jar(&dir, "cloth-158.jar", Some(r#"{"id":"cloth-config","version":"26.3.158"}"#));
        let new_cloth = jar(&dir, "cloth-159.jar", Some(r#"{"id":"cloth-config","version":"26.3.159"}"#));
        // a raw line break inside a string must not hide the id
        let iris = jar(&dir, "iris.jar", Some("{\"id\":\"iris\",\"version\":\"1.11.7\",\"description\":\"a\nb\"}"));
        let library = jar(&dir, "library.jar", None);
        let pack_api = jar(&dir, "fabric-api-pack.jar", Some(r#"{"id":"fabric-api","version":"0.166.2+26.3"}"#));
        let old_api = jar(&dir, "fabric-api-old.jar", Some(r#"{"id":"fabric-api","version":"0.160.0+26.3"}"#));

        let targets = vec![
            target(pack_sodium, "modrinth:AANobbMI", ModOrigin::Pack),
            target(cf_sodium, "curseforge:394468", ModOrigin::Profile),
            target(mr_sodium, "modrinth:AANobbMI-profile", ModOrigin::Profile),
            target(old_cloth, "curseforge:348521", ModOrigin::Profile),
            target(new_cloth, "modrinth:9s6osm5g", ModOrigin::Profile),
            target(iris.clone(), "modrinth:YL57xq9U", ModOrigin::Profile),
            target(library.clone(), "url:lib", ModOrigin::Profile),
            target(pack_api, "modrinth:P7dR8mSH", ModOrigin::Pack),
            target(old_api, "curseforge:306612", ModOrigin::Profile),
        ];
        let loose_iris = jar(&dir, "iris-loose.jar", Some(r#"{"id":"iris","version":"1.11.6"}"#));

        let mut report = ModResolutionReport::default();
        let (kept, extra) =
            drop_duplicate_fabric_mods(&targets, &[loose_iris.clone()], &mut report).await;

        // profile beats pack even with an older version; on a tie the first one stays
        // newer version wins between profile mods; the player's own jar beats the profile
        // a library loads in its newest version, even from the pack
        assert_eq!(names(&kept), vec!["sodium-cf.jar", "cloth-159.jar", "library.jar", "fabric-api-pack.jar"]);
        assert_eq!(extra, vec![loose_iris]);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn versions_compare_sensibly() {
        use std::cmp::Ordering::*;
        assert_eq!(compare_mod_versions("26.3.159", "26.3.158"), Greater);
        assert_eq!(compare_mod_versions("0.9.3-beta.5+mc26.3", "0.9.3-beta.4+mc26.3"), Greater);
        assert_eq!(compare_mod_versions("0.9.3+mc26.3", "0.9.3-beta.5+mc26.3"), Greater);
        assert_eq!(compare_mod_versions("3.9.7+26.3-fabric", "3.9.7+26.3-neoforge"), Equal);
        assert_eq!(compare_mod_versions("mc26.3-1.10", "mc26.3-1.9"), Greater);
    }
}




