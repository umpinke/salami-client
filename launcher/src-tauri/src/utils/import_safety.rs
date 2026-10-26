
use crate::error::{AppError, Result};
use crate::state::profile_state::{
    ImageSource, Mod, ModSource, Profile, ProfileBanner, ProfileSettings,
};
use log::warn;
use serde::{Deserialize, Serialize};
use std::path::{Component, Path, PathBuf};

const ALLOWED_CONTENT_EXTENSIONS: &[&str] = &["jar", "zip"];

pub const MODRINTH_HOSTS: &[&str] = &["cdn.modrinth.com"];
pub const CURSEFORGE_HOSTS: &[&str] = &[
    "edge.forgecdn.net",
    "mediafilez.forgecdn.net",
    "media.forgecdn.net",
];

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct RejectedMod {
    pub display_name: String,
    pub reason: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct ImportSecurityReport {
    pub stripped_java_path: Option<String>,
    pub stripped_jvm_args: Option<String>,
    pub stripped_game_args: Vec<String>,
    pub stripped_quick_play_path: Option<String>,
    pub stripped_loader_version: Option<String>,
    pub rejected_mods: Vec<RejectedMod>,
    pub third_party_download_hosts: Vec<String>,
    pub unverified_mod_count: usize,
    pub stripped_profile_flags: Vec<String>,
}

impl ImportSecurityReport {
    pub fn is_clean(&self) -> bool {
        self.stripped_java_path.is_none()
            && self.stripped_jvm_args.is_none()
            && self.stripped_game_args.is_empty()
            && self.stripped_quick_play_path.is_none()
            && self.stripped_loader_version.is_none()
            && self.rejected_mods.is_empty()
            && self.third_party_download_hosts.is_empty()
            && self.unverified_mod_count == 0
            && self.stripped_profile_flags.is_empty()
    }

    pub fn has_critical_findings(&self) -> bool {
        self.stripped_java_path.is_some()
            || self.stripped_jvm_args.is_some()
            || !self.stripped_game_args.is_empty()
            || !self.rejected_mods.is_empty()
    }
}

pub fn parse_untrusted_profile(json: &str) -> Result<(Profile, ImportSecurityReport)> {
    let mut profile: Profile = serde_json::from_str(json).map_err(AppError::Json)?;
    // Salami: die Version wird Teil von Ordnernamen (und von Lösch-Pfaden bei Reparaturen)
    require_version_like(&profile.game_version)?;
    if let Some(loader_version) = profile.loader_version.as_deref() {
        require_version_like(loader_version)?;
    }
    let report = sanitize_imported_profile(&mut profile);
    Ok((profile, report))
}

pub fn safe_relative_path(path: &str) -> Result<PathBuf> {
    let reject = |why: &str| -> AppError {
        AppError::Other(format!("Unsafe relative path '{}': {}", path, why))
    };

    let mut normalized = PathBuf::new();
    for segment in path.split(['/', '\\']) {
        if segment.is_empty() {
            continue;
        }
        if segment.contains('\0') {
            return Err(reject("contains a NUL byte"));
        }

        let mut components = Path::new(segment).components();
        match (components.next(), components.next()) {
            (Some(Component::Normal(_)), None) => {}
            _ => return Err(reject("contains a segment that is not a plain name")),
        }
        // Salami: auch `:` (NTFS-Streams), Punkt/Leerzeichen am Ende und reservierte Namen ablehnen
        if safe_file_component(segment).is_err() {
            return Err(reject("contains a name that is not valid on Windows"));
        }

        normalized.push(segment);
    }

    if normalized.as_os_str().is_empty() {
        return Err(reject("resolves to no path segments"));
    }

    Ok(normalized)
}

/// Salami: Kanonischer relativer Pfad für Dateien aus Packs (Zip-Einträge, Manifest-Pfade).
/// Lehnt ab statt zu bereinigen, damit Vorschau, Prüfung und Entpacken garantiert dieselbe Datei
/// meinen (sonst würde z. B. aus `evil.ja:r` beim Entpacken still `evil.jar`): kein Wurzel- oder
/// Laufwerkspfad, kein `..`, kein `:`, keine Steuerzeichen, kein Punkt/Leerzeichen am Ende, keine
/// reservierten Windows-Namen. `\` gilt als Trenner; leere und `.`-Segmente fallen weg.
pub fn canonical_pack_path(path: &str) -> Result<PathBuf> {
    let reject = |why: &str| -> AppError {
        AppError::Other(format!("Unsafe pack path '{}': {}", path, why))
    };
    if path.starts_with('/') || path.starts_with('\\') {
        return Err(reject("is absolute"));
    }

    let mut canonical = PathBuf::new();
    for segment in path.split(['/', '\\']) {
        if segment.is_empty() || segment == "." {
            continue;
        }
        if segment == ".." {
            return Err(reject("contains '..'"));
        }
        if segment.contains(':') {
            return Err(reject("contains ':'"));
        }
        if safe_file_component(segment).is_err() {
            return Err(reject("contains a name that is not valid on Windows"));
        }
        canonical.push(segment);
    }

    if canonical.as_os_str().is_empty() {
        return Err(reject("resolves to no path segments"));
    }
    Ok(canonical)
}

/// Salami: `overrides` aus einem CurseForge-Manifest als kanonischer Präfix (`ordner/`, klein).
/// Nur ein schlichter relativer Ordner – `.`, leer, `..`, Laufwerke usw. werden abgelehnt. Vorschau
/// (Prüfung der Mod-Dateien) und Entpacken nutzen genau diesen Wert, damit ein Pack nicht mit
/// z. B. `"overrides": "."` Dateien an der Prüfung vorbei in `mods/` legen kann.
pub fn curseforge_overrides_prefix(raw: Option<&str>) -> Result<String> {
    let raw = raw.unwrap_or("overrides");
    let key = canonical_pack_key(raw).ok_or_else(|| {
        AppError::Other(format!("Invalid overrides folder '{}' in manifest.json", raw))
    })?;
    Ok(format!("{}/", key))
}

/// Salami: Kanonischer Pfad eines Archiv-Eintrags unterhalb von `prefix` (aus
/// [`curseforge_overrides_prefix`] oder z. B. `"overrides/"`); `None`, wenn der Eintrag nicht darunter
/// liegt, nur der Ordner selbst ist oder einen unsicheren Namen hat.
pub fn canonical_path_under(entry: &str, prefix: &str) -> Option<PathBuf> {
    let canonical = canonical_pack_path(entry).ok()?;
    let depth = prefix.trim_end_matches('/').split('/').count();
    let parts: Vec<Component> = canonical.components().collect();
    if parts.len() <= depth {
        return None;
    }
    let head: Vec<String> = parts[..depth]
        .iter()
        .map(|c| c.as_os_str().to_string_lossy().to_ascii_lowercase())
        .collect();
    if format!("{}/", head.join("/")) != prefix.to_ascii_lowercase() {
        return None;
    }
    Some(parts[depth..].iter().collect())
}

/// Liegt ein kanonischer Pack-Pfad im `mods`-Ordner? Groß-/Kleinschreibung egal (Windows).
pub fn is_in_mods_dir(path: &Path) -> bool {
    path.components()
        .next()
        .map_or(false, |c| c.as_os_str().to_string_lossy().eq_ignore_ascii_case("mods"))
}

/// Kanonischer Pfad als `/`-getrennter Kleinbuchstaben-Text (für Präfix-Vergleiche).
pub fn canonical_pack_key(path: &str) -> Option<String> {
    let canonical = canonical_pack_path(path).ok()?;
    let parts: Vec<String> = canonical
        .components()
        .map(|c| c.as_os_str().to_string_lossy().to_ascii_lowercase())
        .collect();
    Some(parts.join("/"))
}

pub fn safe_file_component(name: &str) -> Result<String> {
    let reject = |why: &str| -> AppError {
        AppError::Other(format!("Unsafe file name '{}': {}", name, why))
    };

    if name.is_empty() {
        return Err(reject("empty"));
    }
    if name.contains('/') || name.contains('\\') {
        return Err(reject("contains a path separator"));
    }
    if name.contains('\0') {
        return Err(reject("contains a NUL byte"));
    }

    let mut components = Path::new(name).components();
    match (components.next(), components.next()) {
        (Some(Component::Normal(_)), None) => {}
        _ => return Err(reject("is not a single plain path component")),
    }

    let cleaned = sanitize_filename::sanitize_with_options(
        name,
        sanitize_filename::Options {
            windows: true,
            truncate: true,
            replacement: "",
        },
    );
    if cleaned != name {
        return Err(reject("contains characters that are not valid in a file name"));
    }

    Ok(cleaned)
}

pub fn sanitize_settings(
    claimed: ProfileSettings,
    report: &mut ImportSecurityReport,
) -> ProfileSettings {
    if claimed.use_custom_java_path || claimed.java_path.is_some() {
        report.stripped_java_path = claimed
            .java_path
            .clone()
            .or_else(|| Some("<enabled without a path>".to_string()));
    }
    if let Some(args) = claimed
        .custom_jvm_args
        .as_ref()
        .filter(|a| !a.trim().is_empty())
    {
        report.stripped_jvm_args = Some(args.clone());
    }
    if !claimed.extra_game_args.is_empty() {
        report.stripped_game_args = claimed.extra_game_args.clone();
    }
    if let Some(path) = claimed
        .quick_play_path
        .as_ref()
        .filter(|p| !p.trim().is_empty())
    {
        report.stripped_quick_play_path = Some(path.clone());
    }

    let mut settings = ProfileSettings {
        memory: claimed.memory,
        resolution: claimed.resolution,
        fullscreen: claimed.fullscreen,
        use_overwrite_loader_version: claimed.use_overwrite_loader_version,
        ..ProfileSettings::default()
    };

    match claimed.overwrite_loader_version.as_deref() {
        Some(v) if !is_version_like(v) => {
            report.stripped_loader_version = Some(v.to_string());
            settings.use_overwrite_loader_version = false;
        }
        other => settings.overwrite_loader_version = other.map(str::to_string),
    }
    settings.overwrite_loader_versions = claimed
        .overwrite_loader_versions
        .into_iter()
        .filter(|(_, v)| is_version_like(v))
        .collect();

    settings
}

pub fn sanitize_imported_profile(profile: &mut Profile) -> ImportSecurityReport {
    let mut report = ImportSecurityReport::default();

    let claimed = std::mem::take(&mut profile.settings);
    profile.settings = sanitize_settings(claimed, &mut report);

    let mut kept: Vec<Mod> = Vec::with_capacity(profile.mods.len());
    for mod_info in std::mem::take(&mut profile.mods) {
        let label = mod_label(&mod_info);
        match inspect_mod(&mod_info, &mut report) {
            Ok(()) => kept.push(mod_info),
            Err(reason) => {
                warn!(
                    "Dropping mod '{}' from imported pack: {}",
                    label, reason
                );
                report.rejected_mods.push(RejectedMod {
                    display_name: label,
                    reason,
                });
            }
        }
    }
    profile.mods = kept;

    strip_profile_flags(profile, &mut report);

    report.third_party_download_hosts.sort();
    report.third_party_download_hosts.dedup();

    report
}

pub(crate) fn strip_profile_flags(profile: &mut Profile, report: &mut ImportSecurityReport) {
    if profile.is_standard_version {
        profile.is_standard_version = false;
        report
            .stripped_profile_flags
            .push("is_standard_version".to_string());
    }
    if profile.use_shared_minecraft_folder {
        profile.use_shared_minecraft_folder = false;
        report
            .stripped_profile_flags
            .push("use_shared_minecraft_folder".to_string());
    }
    if profile.preferred_account_id.take().is_some() {
        report
            .stripped_profile_flags
            .push("preferred_account_id".to_string());
    }
    if !std::mem::take(&mut profile.sync_pack_ids).is_empty() {
        report
            .stripped_profile_flags
            .push("sync_pack_ids".to_string());
    }
    profile.playtime_seconds = 0;

    if strip_unsafe_banner(&mut profile.banner) {
        report.stripped_profile_flags.push("banner".to_string());
    }
    if strip_unsafe_banner(&mut profile.background) {
        report.stripped_profile_flags.push("background".to_string());
    }
}

fn strip_unsafe_banner(banner: &mut Option<ProfileBanner>) -> bool {
    let keep = match banner.as_ref().map(|b| &b.source) {
        None => return false,
        Some(ImageSource::Base64 { .. }) => true,
        Some(ImageSource::Url { url }) => url.starts_with("https://"),
        Some(ImageSource::RelativeProfile { path }) => is_safe_relative_path(path),
        Some(ImageSource::AbsolutePath { .. }) | Some(ImageSource::RelativePath { .. }) => false,
    };

    if !keep {
        *banner = None;
    }
    !keep
}

fn is_safe_relative_path(path: &str) -> bool {
    !path.is_empty()
        && !path.contains('\\')
        && Path::new(path)
            .components()
            .all(|component| matches!(component, Component::Normal(_)))
}

pub fn mod_label(mod_info: &Mod) -> String {
    if let Some(name) = &mod_info.display_name {
        if !name.trim().is_empty() {
            return name.clone();
        }
    }
    match &mod_info.source {
        ModSource::Modrinth { file_name, .. }
        | ModSource::CurseForge { file_name, .. }
        | ModSource::Local { file_name } => file_name.clone(),
        ModSource::Url { file_name, url } => file_name.clone().unwrap_or_else(|| url.clone()),
        ModSource::Maven { coordinates, .. } => coordinates.clone(),
        ModSource::Embedded { name } => name.clone(),
    }
}

fn raw_file_name(source: &ModSource) -> Option<&str> {
    match source {
        ModSource::Modrinth { file_name, .. }
        | ModSource::CurseForge { file_name, .. }
        | ModSource::Local { file_name } => Some(file_name.as_str()),
        ModSource::Url { file_name, .. } => file_name.as_deref(),
        ModSource::Maven { .. } | ModSource::Embedded { .. } => None,
    }
}

fn inspect_mod(mod_info: &Mod, report: &mut ImportSecurityReport) -> std::result::Result<(), String> {
    if let Some(name) = raw_file_name(&mod_info.source) {
        check_content_file_name(name)?;
    }
    if let Some(override_name) = mod_info.file_name_override.as_deref() {
        check_content_file_name(override_name)?;
    }

    match &mod_info.source {
        ModSource::Modrinth {
            download_url,
            file_hash_sha1,
            ..
        } => {
            require_host(download_url, MODRINTH_HOSTS, "Modrinth")?;
            if file_hash_sha1.is_none() {
                report.unverified_mod_count += 1;
            }
        }
        ModSource::CurseForge {
            download_url,
            file_hash_sha1,
            ..
        } => {
            // Leer: der Autor gibt die Datei nur über die CurseForge-Webseite heraus; der Spieler
            // lädt sie selbst und der Launcher prüft sie per Prüfsumme (curseforge_manual)
            if !download_url.is_empty() {
                require_host(download_url, CURSEFORGE_HOSTS, "CurseForge")?;
            }
            if file_hash_sha1.is_none() {
                report.unverified_mod_count += 1;
            }
        }
        ModSource::Url { url, .. } => {
            let host = require_https(url)?;
            report.unverified_mod_count += 1;
            report.third_party_download_hosts.push(host);
        }
        ModSource::Maven {
            coordinates,
            repository_url,
        } => {
            if !coordinates
                .split(':')
                .all(|seg| !seg.is_empty() && seg.chars().all(is_coordinate_char))
            {
                return Err(format!("Maven coordinates '{}' are malformed", coordinates));
            }
            if let Some(repo) = repository_url {
                let host = require_https(repo)?;
                report.third_party_download_hosts.push(host);
            }
        }
        ModSource::Local { .. } | ModSource::Embedded { .. } => {}
    }

    Ok(())
}

/// Salami: Inhalte und Modpack-Archive nur per https von den CDNs von Modrinth/CurseForge laden.
pub fn require_platform_download(url: &str) -> Result<()> {
    if require_host(url, MODRINTH_HOSTS, "Modrinth").is_ok()
        || require_host(url, CURSEFORGE_HOSTS, "CurseForge").is_ok()
    {
        return Ok(());
    }
    Err(AppError::Other(format!(
        "Refusing download from an unexpected host: {}",
        url
    )))
}

pub fn check_content_file_name(name: &str) -> std::result::Result<(), String> {
    let cleaned = safe_file_component(name).map_err(|e| e.to_string())?;
    let extension = std::path::Path::new(&cleaned)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();

    if !ALLOWED_CONTENT_EXTENSIONS.contains(&extension.as_str()) {
        return Err(format!(
            "'{}' is not a .jar or .zip file",
            cleaned
        ));
    }
    Ok(())
}

fn require_https(url: &str) -> std::result::Result<String, String> {
    let parsed = url::Url::parse(url).map_err(|_| format!("'{}' is not a valid URL", url))?;
    if parsed.scheme() != "https" {
        return Err(format!("'{}' does not use https", url));
    }
    parsed
        .host_str()
        .map(|h| h.to_ascii_lowercase())
        .ok_or_else(|| format!("'{}' has no host", url))
}

pub fn require_host(
    url: &str,
    allowed: &[&str],
    platform: &str,
) -> std::result::Result<(), String> {
    let host = require_https(url)?;
    if allowed.iter().any(|a| host == *a) {
        Ok(())
    } else {
        Err(format!(
            "{} mod claims to download from '{}', which is not a {} CDN",
            platform, host, platform
        ))
    }
}

fn is_coordinate_char(c: char) -> bool {
    c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-' | '+')
}

/// Salami: Archiv-Eintrag sicher an einen Zielordner hängen – nur normale Pfadteile, kein
/// absoluter Pfad, kein Laufwerk, kein `..` (Zip-Slip). `None` heißt: Eintrag überspringen.
pub fn safe_archive_join(base: &Path, entry: impl AsRef<Path>) -> Option<PathBuf> {
    let mut joined = base.to_path_buf();
    let mut any = false;
    for component in entry.as_ref().components() {
        match component {
            Component::Normal(part) => {
                joined.push(part);
                any = true;
            }
            Component::CurDir => {}
            _ => return None,
        }
    }
    any.then_some(joined)
}

/// Salami: Prüft, dass ein relativer Symlink-Ziel innerhalb von `root` bleibt, wenn er im Ordner
/// `link_parent` (relativ zu `root`) liegt.
pub fn relative_link_stays_inside(link_parent_in_root: &Path, link_target: &Path) -> bool {
    let mut depth: i64 = 0;
    for component in link_parent_in_root.components() {
        match component {
            Component::Normal(_) => depth += 1,
            Component::CurDir => {}
            _ => return false,
        }
    }
    for component in link_target.components() {
        match component {
            Component::Normal(_) => depth += 1,
            Component::CurDir => {}
            Component::ParentDir => {
                depth -= 1;
                if depth < 0 {
                    return false;
                }
            }
            _ => return false,
        }
    }
    true
}

pub fn require_version_like(value: &str) -> Result<()> {
    if is_version_like(value) {
        Ok(())
    } else {
        Err(AppError::Other(format!("Invalid version: {:?}", value)))
    }
}

/// Salami: Versionsangaben (Minecraft, Loader) werden Teil von Ordnernamen und Download-Adressen.
/// Erlaubt ist alles, was echte Versionen enthalten – auch „0.19.3 (stable)“, wie Profil-Assistent
/// und Launcher Fabric/Quilt-Versionen speichern, oder „3D Shareware v1.34“ –, aber nichts, was
/// einen Pfad oder eine Adresse verbiegen kann: keine Trenner, kein `..`, keine unter Windows
/// verbotenen Zeichen, keine URL-Sonderzeichen, kein Punkt/Leerzeichen am Anfang oder Ende.
pub fn is_version_like(value: &str) -> bool {
    let edge = |c: char| c == '.' || c == ' ';
    !value.is_empty()
        && value.len() <= 64
        && !value.starts_with(edge)
        && !value.ends_with(edge)
        && !value.contains("..")
        && value.chars().all(|c| {
            !c.is_control()
                && !matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' | '%' | '#' | '&' | '=')
        })
}

#[cfg(test)]
mod canonical_pack_path_tests {
    use super::*;

    #[test]
    fn curseforge_overrides_must_be_a_plain_folder() {
        assert_eq!(curseforge_overrides_prefix(None).unwrap(), "overrides/");
        assert_eq!(curseforge_overrides_prefix(Some("Overrides/")).unwrap(), "overrides/");
        for bad in [".", "", "./", "..", "/", "C:", "a/../b"] {
            assert!(curseforge_overrides_prefix(Some(bad)).is_err(), "{bad}");
        }
        let prefix = curseforge_overrides_prefix(Some("overrides")).unwrap();
        let jar = PathBuf::from("mods").join("a.jar");
        assert_eq!(canonical_path_under("overrides/mods/a.jar", &prefix), Some(jar.clone()));
        assert_eq!(canonical_path_under("./OVERRIDES//mods/a.jar", &prefix), Some(jar));
        assert_eq!(canonical_path_under("overrides/", &prefix), None);
        assert_eq!(canonical_path_under("mods/a.jar", &prefix), None);
        assert_eq!(canonical_path_under("overrides/mods/evil.ja:r", &prefix), None);
        let nested = curseforge_overrides_prefix(Some("pack/overrides")).unwrap();
        assert_eq!(canonical_path_under("pack/overrides/config/x.json", &nested), Some(PathBuf::from("config").join("x.json")));
        assert_eq!(canonical_path_under("pack/config/x.json", &nested), None);
    }

    #[test]
    fn rejects_instead_of_stripping() {
        assert_eq!(canonical_pack_path("mods/a.jar").unwrap(), PathBuf::from("mods").join("a.jar"));
        assert_eq!(canonical_pack_path("./mods//a.jar").unwrap(), PathBuf::from("mods").join("a.jar"));
        assert_eq!(canonical_pack_path("Mods\\a.jar").unwrap(), PathBuf::from("Mods").join("a.jar"));
        for bad in [
            "/mods/a.jar",
            "\\mods\\a.jar",
            "C:/x/a.jar",
            "mods/../a.jar",
            "mods/evil.ja:r",
            "mods/evil.jar.",
            "mods/evil.jar ",
            "mods/CON",
            "mods/a\u{1}.jar",
            "",
            "./",
        ] {
            assert!(canonical_pack_path(bad).is_err(), "accepted {bad:?}");
        }
    }

    #[test]
    fn mods_dir_is_case_insensitive() {
        assert!(is_in_mods_dir(&canonical_pack_path("MODS/x.jar").unwrap()));
        assert!(is_in_mods_dir(&canonical_pack_path("./mods/x.jar").unwrap()));
        assert!(!is_in_mods_dir(&canonical_pack_path("config/mods/x.jar").unwrap()));
        assert_eq!(canonical_pack_key("Overrides\\Mods/X.JAR").as_deref(), Some("overrides/mods/x.jar"));
    }

    #[test]
    fn archive_join_blocks_zip_slip() {
        let base = Path::new("base");
        assert_eq!(safe_archive_join(base, "a/b.txt"), Some(base.join("a").join("b.txt")));
        assert_eq!(safe_archive_join(base, "./a"), Some(base.join("a")));
        assert!(safe_archive_join(base, "../x").is_none());
        assert!(safe_archive_join(base, "a/../../x").is_none());
        assert!(safe_archive_join(base, "/etc/passwd").is_none());
        assert!(safe_archive_join(base, "").is_none());
        #[cfg(windows)]
        {
            assert!(safe_archive_join(base, "..\\x.jar").is_none());
            assert!(safe_archive_join(base, "C:\\x.jar").is_none());
        }
        assert!(relative_link_stays_inside(Path::new("legal/java.base"), Path::new("../java.base/LICENSE")));
        assert!(!relative_link_stays_inside(Path::new("legal"), Path::new("../../etc/passwd")));
        assert!(!relative_link_stays_inside(Path::new("a"), Path::new("/etc/passwd")));
        assert!(require_version_like("1.21.11").is_ok());
        assert!(require_version_like("../../x").is_err());
        assert!(require_version_like("..").is_err());
        assert!(require_version_like("1.20.1-47.2.0").is_ok());
        assert!(require_version_like("0.16.10+build.1").is_ok());
        // so speichern Profil-Assistent und Launcher Fabric/Quilt-Versionen
        assert!(require_version_like("0.19.3 (stable)").is_ok());
        assert!(require_version_like("1.21.11-pre1").is_ok());
        assert!(require_version_like("3D Shareware v1.34").is_ok());
        for bad in ["a/b", "a\\b", "..", "../x", "C:x", "x?y", "x%2e", "x#y", " x", "x.", ""] {
            assert!(require_version_like(bad).is_err(), "{bad:?}");
        }
    }

    #[test]
    fn sync_paths_reject_windows_aliases() {
        assert!(safe_relative_path("config/a.txt").is_ok());
        assert!(safe_relative_path("config/a.txt:stream").is_err());
        assert!(safe_relative_path("mods.").is_err());
    }
}
