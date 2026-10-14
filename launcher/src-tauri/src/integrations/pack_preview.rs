use crate::error::{AppError, Result};
use crate::integrations::curseforge::CurseForgeManifest;
use crate::integrations::mrpack::{
    ModrinthIndex, FABRIC_LOADER_DEPENDENCY, FORGE_DEPENDENCY, MINECRAFT_DEPENDENCY,
    NEOFORGE_DEPENDENCY, QUILT_LOADER_DEPENDENCY,
};
use crate::state::profile_state::{ModSource, Profile};
use crate::utils::import_safety::{mod_label, parse_untrusted_profile, ImportSecurityReport};
use async_zip::tokio::read::seek::ZipFileReader;
use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use tokio::fs::File;
use tokio::io::BufReader;

const MODPACK_GROUP: &str = "MODPACKS";

const MAX_TOTAL_BUNDLED_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const HASH_CHUNK_BYTES: usize = 64 * 1024;

type PackZip = ZipFileReader<BufReader<File>>;

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum PackFormat {
    Noriskpack,
    Mrpack,
    Curseforge,
}

impl PackFormat {
    fn from_extension(extension: &str) -> Option<Self> {
        match extension {
            "salamipack" | "noriskpack" => Some(Self::Noriskpack),
            "mrpack" => Some(Self::Mrpack),
            "zip" => Some(Self::Curseforge),
            _ => None,
        }
    }
}

pub use crate::integrations::provenance::{
    ExecutableContentReport, ProvenanceReport, UnknownContent, UnknownReason,
};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ImportPackPreview {
    pub file_path: String,
    pub file_name: String,
    pub file_size: u64,
    pub pack_type: PackFormat,
    pub profile_name: Option<String>,
    pub group: Option<String>,
    pub game_version: Option<String>,
    pub loader: Option<String>,
    pub loader_version: Option<String>,
    pub mod_count: usize,
    pub override_file_count: usize,
    pub security: ImportSecurityReport,
    pub provenance: ProvenanceReport,
    pub norisk_pack: NoriskPackOffer,
    pub executable_content: ExecutableContentReport,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct NoriskPackOffer {
    pub pack_id: String,
    pub preselected: bool,
}

impl Default for NoriskPackOffer {
    fn default() -> Self {
        Self {
            pack_id: DEFAULT_NORISK_PACK.to_string(),
            preselected: false,
        }
    }
}

struct PackDetails {
    declared_norisk_pack: Option<String>,
    profile_name: Option<String>,
    group: Option<String>,
    game_version: Option<String>,
    loader: Option<String>,
    loader_version: Option<String>,
    mod_count: usize,
    override_file_count: usize,
    security: ImportSecurityReport,
    provenance: ProvenanceReport,
    executable_content: ExecutableContentReport,
}

impl Default for PackDetails {
    fn default() -> Self {
        Self {
            declared_norisk_pack: None,
            profile_name: None,
            group: None,
            game_version: None,
            loader: None,
            loader_version: None,
            mod_count: 0,
            override_file_count: 0,
            security: ImportSecurityReport::default(),
            provenance: ProvenanceReport::default(),
            executable_content: ExecutableContentReport::default(),
        }
    }
}

pub async fn preview_pack(pack_path: &Path) -> Result<ImportPackPreview> {
    let extension = pack_path
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_lowercase())
        .unwrap_or_default();

    let pack_type = PackFormat::from_extension(&extension).ok_or_else(|| {
        AppError::Other(format!("Unsupported modpack extension: .{}", extension))
    })?;

    preview_pack_as(pack_path, pack_type).await
}

pub async fn preview_pack_as(pack_path: &Path, pack_type: PackFormat) -> Result<ImportPackPreview> {
    let metadata = tokio::fs::metadata(pack_path).await.map_err(AppError::Io)?;
    let mut zip = open_archive(pack_path).await?;
    let details = match pack_type {
        PackFormat::Noriskpack => read_noriskpack(&mut zip, pack_path).await?,
        PackFormat::Mrpack => read_mrpack(&mut zip).await?,
        PackFormat::Curseforge => read_curseforge(&mut zip).await?,
    };

    let preview = ImportPackPreview {
        file_path: pack_path.display().to_string(),
        file_name: pack_path
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| pack_path.display().to_string()),
        file_size: metadata.len(),
        pack_type,
        profile_name: details.profile_name,
        group: details.group,
        game_version: details.game_version,
        loader: details.loader,
        loader_version: details.loader_version,
        mod_count: details.mod_count,
        override_file_count: details.override_file_count,
        security: details.security,
        provenance: details.provenance,
        norisk_pack: NoriskPackOffer {
            pack_id: DEFAULT_NORISK_PACK.to_string(),
            preselected: details.declared_norisk_pack.is_some(),
        },
        executable_content: details.executable_content,
    };

    info!(
        "Previewed pack '{}': {} mods, {} override files, {} unknown, clean={}",
        preview.file_name,
        preview.mod_count,
        preview.override_file_count,
        preview.provenance.unknown.len(),
        preview.security.is_clean()
    );

    Ok(preview)
}

/// Salami: Sperre vor jedem Pack-Import (Datei, Doppelklick, Modrinth/CurseForge). Enthält das
/// Pack Programmdateien, die nicht per Hash als bekannte Plattform-Mods erkannt wurden (oder
/// Skripte/native Dateien), muss der Nutzer das in einem nativen Dialog erlauben – den kann
/// weder ein präpariertes Pack noch ein Skript in der Oberfläche umgehen.
pub async fn ensure_pack_content_allowed(pack_path: &Path, pack_type: PackFormat) -> Result<()> {
    let preview = preview_pack_as(pack_path, pack_type).await?;
    let findings = consent_findings(&preview);
    if findings.is_empty() {
        return Ok(());
    }

    let app = crate::state::state_manager::State::get()
        .await?
        .event_state
        .app_handle()
        .ok_or_else(|| AppError::Other("Cannot ask for confirmation".to_string()))?;

    const MAX_LINES: usize = 15;
    let mut listed: Vec<String> = findings.iter().take(MAX_LINES).cloned().collect();
    if findings.len() > MAX_LINES {
        listed.push(format!("... +{}", findings.len() - MAX_LINES));
    }
    let message = [
        format!("{}", preview.file_name),
        String::new(),
        "Dieses Modpack enthält Dateien, die Code ausführen können und nicht als bekannte Mods geprüft werden konnten:".to_string(),
        "This modpack contains files that can run code and could not be verified as known mods:".to_string(),
        String::new(),
        listed.join("\n"),
        String::new(),
        "Nur erlauben, wenn du der Quelle vertraust.".to_string(),
        "Only allow this if you trust the source.".to_string(),
    ]
    .join("\n");

    if crate::utils::launch_guard::confirm_dialog(&*app, message).await {
        info!("User allowed unverified content in pack '{}'", preview.file_name);
        Ok(())
    } else {
        Err(AppError::Other(
            "Import cancelled: the pack contains unverified files that can run code".to_string(),
        ))
    }
}

fn consent_findings(preview: &ImportPackPreview) -> Vec<String> {
    let mut findings: Vec<String> = preview
        .provenance
        .unknown
        .iter()
        .map(|item| {
            let reason = match item.reason {
                UnknownReason::NoHash => "ohne Prüfsumme / no checksum",
                UnknownReason::NotOnPlatform => "nicht auf Modrinth / not on Modrinth",
                UnknownReason::BundledFile => "mitgeliefert, unbekannt / bundled, unknown",
                UnknownReason::LocalSource => "lokale Datei / local file",
            };
            format!("{} ({})", item.name, reason)
        })
        .collect();
    let exec = &preview.executable_content;
    findings.extend(exec.scripts.iter().map(|n| format!("{} (Skript / script)", n)));
    findings.extend(exec.natives.iter().map(|n| format!("{} (Programm / executable)", n)));
    let hidden = (exec.script_count + exec.native_count).saturating_sub(exec.scripts.len() + exec.natives.len());
    if hidden > 0 {
        findings.push(format!("+{} weitere / more", hidden));
    }
    if preview.provenance.incomplete {
        findings.push("Nicht alle Dateien konnten geprüft werden / Not every file could be checked".to_string());
    }
    findings
}

pub async fn preview_pack_at(path: impl Into<PathBuf>) -> Result<ImportPackPreview> {
    let path = path.into();
    if !path.exists() {
        return Err(AppError::Other(format!(
            "File not found at path: {}",
            path.display()
        )));
    }

    preview_pack(&path).await
}

async fn read_noriskpack(zip: &mut PackZip, pack_path: &Path) -> Result<PackDetails> {
    let raw = read_zip_entry(zip, &["profile.json"]).await?;
    let (profile, security) = parse_untrusted_profile(&raw)?;

    let mut provenance = ProvenanceReport::default();
    collect_profile_provenance(&profile, &mut provenance).await;
    collect_bundled_provenance(zip, &mut provenance, &["overrides/"]).await;

    Ok(PackDetails {
        declared_norisk_pack: profile.selected_norisk_pack_id.clone(),
        profile_name: pack_path
            .file_stem()
            .and_then(|s| s.to_str())
            .map(str::to_string)
            .or_else(|| Some(profile.name.clone())),
        group: profile.group.clone(),
        game_version: Some(profile.game_version.clone()),
        loader: Some(format!("{:?}", profile.loader).to_lowercase()),
        loader_version: profile.loader_version.clone(),
        mod_count: profile.mods.len(),
        override_file_count: count_entries_under(zip, &["overrides/"]),
        executable_content: scan_executable_content(zip, &["overrides/"]),
        security,
        provenance,
    })
}

async fn read_mrpack(zip: &mut PackZip) -> Result<PackDetails> {
    let raw = read_zip_entry(zip, &["modrinth.index.json"]).await?;
    let index: ModrinthIndex = serde_json::from_str(&raw).map_err(AppError::Json)?;

    let mut loader = None;
    let mut loader_version = None;
    for (key, label) in [
        (FABRIC_LOADER_DEPENDENCY, "fabric"),
        (QUILT_LOADER_DEPENDENCY, "quilt"),
        (NEOFORGE_DEPENDENCY, "neoforge"),
        (FORGE_DEPENDENCY, "forge"),
    ] {
        if let Some(version) = index.dependencies.get(key) {
            loader = Some(label.to_string());
            loader_version = Some(version.clone());
            break;
        }
    }

    let mod_files: Vec<_> = index.files.iter().filter(|f| is_mod_path(&f.path)).collect();

    let mut provenance = ProvenanceReport::default();
    let mut hashed = Vec::new();
    for file in &mod_files {
        let name = base_name(&file.path);
        match file.hashes.get("sha1") {
            Some(sha1) if !sha1.is_empty() => hashed.push((name, sha1.to_lowercase())),
            _ => provenance.push_unknown(name, UnknownReason::NoHash),
        }
    }
    crate::integrations::provenance::classify_by_modrinth_hash(hashed, UnknownReason::NotOnPlatform, &mut provenance).await;
    collect_bundled_provenance(zip, &mut provenance, &["overrides/", "client-overrides/"]).await;

    let mut security = ImportSecurityReport::default();
    security.third_party_download_hosts = collect_third_party_hosts(&index);

    // Salami: Dateien außerhalb von mods/ lädt der Launcher aus dem Manifest herunter (z. B. von
    // GitHub) – Programme und Skripte darunter brauchen genauso eine Zustimmung wie im Archiv.
    let mut executable_content = scan_executable_content(zip, &["overrides/", "client-overrides/"]);
    executable_content.merge(crate::integrations::provenance::classify_executable_entries(
        index
            .files
            .iter()
            .filter(|file| !is_mod_path(&file.path))
            .map(|file| file.path.clone()),
        &[""],
    ));

    Ok(PackDetails {
        security,
        profile_name: Some(index.name.clone()),
        group: Some(MODPACK_GROUP.to_string()),
        game_version: index.dependencies.get(MINECRAFT_DEPENDENCY).cloned(),
        loader,
        loader_version,
        mod_count: mod_files.len(),
        override_file_count: count_entries_under(zip, &["overrides/", "client-overrides/"]),
        executable_content,
        provenance,
        ..Default::default()
    })
}

async fn read_curseforge(zip: &mut PackZip) -> Result<PackDetails> {
    let raw = read_zip_entry(zip, &["manifest.json"]).await?;
    let manifest: CurseForgeManifest = serde_json::from_str(&raw).map_err(AppError::Json)?;

    let mut loader = None;
    let mut loader_version = None;
    if let Some(entry) = manifest.minecraft.mod_loaders.first() {
        loader = Some(
            crate::integrations::curseforge::determine_loader_from_curseforge_string(&entry.id)
                .as_str()
                .to_string(),
        );
        loader_version = crate::integrations::curseforge::extract_loader_version(
            &entry.id,
            Some(&manifest.minecraft.version),
        );
    }

    // Salami: geprüfter, kanonischer Ordner – dieselbe Regel wie beim Entpacken
    let overrides_dir =
        crate::utils::import_safety::curseforge_overrides_prefix(manifest.overrides.as_deref())?;

    let mut provenance = ProvenanceReport::default();
    collect_bundled_provenance(zip, &mut provenance, &[overrides_dir.as_str()]).await;

    Ok(PackDetails {
        profile_name: Some(manifest.name.clone()),
        group: Some(MODPACK_GROUP.to_string()),
        game_version: Some(manifest.minecraft.version.clone()),
        loader,
        loader_version,
        mod_count: manifest.files.len(),
        override_file_count: count_entries_under(zip, &[overrides_dir.as_str()]),
        executable_content: scan_executable_content(zip, &[overrides_dir.as_str()]),
        provenance,
        ..Default::default()
    })
}

const DEFAULT_NORISK_PACK: &str = "norisk-prod";

fn scan_executable_content(zip: &PackZip, override_prefixes: &[&str]) -> ExecutableContentReport {
    let names = zip
        .file()
        .entries()
        .iter()
        .filter_map(|entry| entry.filename().as_str().ok().map(|name| name.to_string()));

    crate::integrations::provenance::classify_executable_entries(names, override_prefixes)
}

fn collect_third_party_hosts(index: &ModrinthIndex) -> Vec<String> {
    let mut hosts: Vec<String> = index
        .files
        .iter()
        .filter(|file| !is_mod_path(&file.path))
        .flat_map(|file| file.downloads.iter())
        .filter(|url| !crate::integrations::modrinth::is_whitelisted_modpack_url(url))
        .filter_map(|url| {
            url::Url::parse(url)
                .ok()
                .and_then(|parsed| parsed.host_str().map(|host| host.to_ascii_lowercase()))
        })
        .collect();
    hosts.sort();
    hosts.dedup();
    hosts
}

async fn collect_profile_provenance(profile: &Profile, report: &mut ProvenanceReport) {
    let mut modrinth_hashes: Vec<(String, String)> = Vec::new();
    let mut curseforge_prints: Vec<(String, u64)> = Vec::new();

    for mod_info in &profile.mods {
        let label = mod_label(mod_info);
        match &mod_info.source {
            ModSource::Modrinth { file_hash_sha1, .. } => match file_hash_sha1 {
                Some(hash) => modrinth_hashes.push((label, hash.to_lowercase())),
                None => report.push_unknown(label, UnknownReason::NoHash),
            },
            ModSource::CurseForge {
                file_fingerprint, ..
            } => match file_fingerprint {
                Some(print) => curseforge_prints.push((label, *print)),
                None => report.push_unknown(label, UnknownReason::NoHash),
            },
            ModSource::Local { .. } | ModSource::Url { .. } | ModSource::Maven { .. } => {
                report.push_unknown(label, UnknownReason::LocalSource);
            }
            ModSource::Embedded { .. } => {}
        }
    }

    crate::integrations::provenance::classify_by_modrinth_hash(modrinth_hashes, UnknownReason::NotOnPlatform, report).await;
    crate::integrations::provenance::classify_by_curseforge_fingerprint(curseforge_prints, report).await;
}

async fn collect_bundled_provenance(
    zip: &mut PackZip,
    report: &mut ProvenanceReport,
    override_prefixes: &[&str],
) {
    let bundled = match hash_bundled_mod_jars(zip, override_prefixes).await {
        Ok(bundled) => bundled,
        Err(e) => {
            warn!("Failed to hash bundled jars: {}", e);
            report.incomplete = true;
            return;
        }
    };

    if bundled.truncated {
        report.incomplete = true;
    }
    crate::integrations::provenance::classify_by_modrinth_hash(bundled.hashes, UnknownReason::BundledFile, report).await;
}

struct BundledJars {
    hashes: Vec<(String, String)>,
    truncated: bool,
}

async fn hash_bundled_mod_jars(zip: &mut PackZip, override_prefixes: &[&str]) -> Result<BundledJars> {
    // Salami: kanonische Namen (wie beim Entpacken) und die echten Override-Ordner des Packs
    let mods_prefixes: Vec<String> = override_prefixes
        .iter()
        .map(|prefix| format!("{}mods/", prefix.to_ascii_lowercase()))
        .collect();
    let candidates: Vec<(usize, String)> = zip
        .file()
        .entries()
        .iter()
        .enumerate()
        .filter_map(|(index, entry)| {
            let name = entry.filename().as_str().ok()?;
            let lower = crate::utils::import_safety::canonical_pack_key(name)?;
            let in_mods_dir = mods_prefixes.iter().any(|prefix| lower.starts_with(prefix.as_str()));
            // Salami: auch abgeschaltete Mods (später aktivierbar) und .zip-Mods (alte Forge-Versionen)
            let loadable = lower.ends_with(".jar") || lower.ends_with(".jar.disabled") || lower.ends_with(".zip");
            if in_mods_dir && loadable {
                Some((index, base_name(name)))
            } else {
                None
            }
        })
        .collect();

    let mut hashes = Vec::new();
    let mut truncated = false;
    let mut total_bytes: u64 = 0;

    for (index, name) in candidates {
        if total_bytes >= MAX_TOTAL_BUNDLED_BYTES {
            warn!("Bundled jar budget exhausted before '{}'", name);
            truncated = true;
            break;
        }

        let reader = match zip.reader_with_entry(index).await {
            Ok(reader) => reader,
            Err(e) => {
                warn!("Cannot read bundled jar {}: {}", name, e);
                truncated = true;
                continue;
            }
        };

        match stream_sha1(reader, MAX_TOTAL_BUNDLED_BYTES - total_bytes).await {
            Ok((HashOutcome::Complete(hash), read_bytes)) => {
                total_bytes = total_bytes.saturating_add(read_bytes);
                hashes.push((name, hash));
            }
            Ok((HashOutcome::BudgetExceeded, _)) => {
                warn!("Bundled jar '{}' exceeds the decompression budget", name);
                truncated = true;
                break;
            }
            Err(e) => {
                warn!("Cannot hash bundled jar {}: {}", name, e);
                truncated = true;
            }
        }
    }

    Ok(BundledJars { hashes, truncated })
}

pub enum HashOutcome {
    Complete(String),
    BudgetExceeded,
}

pub async fn stream_sha1<R>(reader: R, byte_budget: u64) -> Result<(HashOutcome, u64)>
where
    R: futures_lite::io::AsyncRead + Unpin,
{
    use sha1::{Digest, Sha1};
    use tokio::io::AsyncReadExt;
    use tokio_util::compat::FuturesAsyncReadCompatExt;

    let mut reader = reader.compat();
    let mut hasher = Sha1::new();
    let mut buffer = vec![0u8; HASH_CHUNK_BYTES];
    let mut read_total: u64 = 0;

    loop {
        let read = reader
            .read(&mut buffer)
            .await
            .map_err(|e| AppError::Other(format!("Failed to read zip entry: {}", e)))?;
        if read == 0 {
            break;
        }
        read_total = read_total.saturating_add(read as u64);
        if read_total > byte_budget {
            return Ok((HashOutcome::BudgetExceeded, read_total));
        }
        hasher.update(&buffer[..read]);
    }

    Ok((
        HashOutcome::Complete(format!("{:x}", hasher.finalize())),
        read_total,
    ))
}

async fn open_archive(pack_path: &Path) -> Result<PackZip> {
    let file = File::open(pack_path).await.map_err(AppError::Io)?;
    ZipFileReader::with_tokio(BufReader::new(file))
        .await
        .map_err(|e| AppError::Other(format!("Failed to read modpack archive: {}", e)))
}

async fn read_zip_entry(zip: &mut PackZip, candidates: &[&str]) -> Result<String> {
    let index = zip
        .file()
        .entries()
        .iter()
        .position(|entry| {
            entry
                .filename()
                .as_str()
                .map_or(false, |name| candidates.contains(&name))
        })
        .ok_or_else(|| {
            AppError::Other(format!(
                "Archive does not contain {}",
                candidates.join(" or ")
            ))
        })?;

    let mut reader = zip
        .reader_with_entry(index)
        .await
        .map_err(|e| AppError::Other(format!("Failed to open manifest entry: {}", e)))?;

    let mut buffer = Vec::new();
    reader
        .read_to_end_checked(&mut buffer)
        .await
        .map_err(|e| AppError::Other(format!("Failed to read manifest entry: {}", e)))?;

    String::from_utf8(buffer)
        .map_err(|e| AppError::Other(format!("Manifest is not valid UTF-8: {}", e)))
}

fn count_entries_under(zip: &PackZip, prefixes: &[&str]) -> usize {
    zip.file()
        .entries()
        .iter()
        .filter(|entry| {
            entry.filename().as_str().map_or(false, |name| {
                !name.ends_with('/')
                    && prefixes
                        .iter()
                        .any(|prefix| crate::utils::import_safety::canonical_path_under(name, prefix).is_some())
            })
        })
        .count()
}

fn is_mod_path(path: &str) -> bool {
    crate::integrations::mrpack::is_manifest_mod_path(path)
}

fn base_name(path: &str) -> String {
    path.replace('\\', "/")
        .rsplit('/')
        .next()
        .unwrap_or(path)
        .to_string()
}
