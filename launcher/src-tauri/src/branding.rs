//! Zentrale Branding- und Dienste-Konfiguration des Salami Client.
//!
//! Alles, was den Launcher als „Salami Client“ ausweist oder festlegt, mit welchen
//! Servern er spricht, steht hier – statt verstreut im Code. Wer den Fork
//! weiterforkt, muss nur diese Datei anpassen.

use std::sync::atomic::{AtomicBool, Ordering};

/// Anzeigename des Produkts.
pub const PRODUCT_NAME: &str = "Salami Client";

/// Bestandteile für `ProjectDirs` → Datenordner, unter Windows
/// `%APPDATA%\salamiclient\SalamiClient`. Bewusst anders als bei NoRisk, damit
/// sich beide Launcher keine Profile, Accounts oder Caches teilen.
pub const DIRS_QUALIFIER: &str = "dev";
pub const DIRS_ORGANIZATION: &str = "salamiclient";
pub const DIRS_APPLICATION: &str = "SalamiClient";

/// URL-Schema für Deep-Links (`salami://…`), muss zu `plugins.deep-link` in `tauri.conf.json` passen.
pub const DEEP_LINK_SCHEME: &str = "salami";

/// NoRisk-Online-Dienste (NoRisk-Token, Capes, Cosmetics, Friends, News,
/// Werbung, Analytics …). Im Salami Client aus: kein Zugriff auf fremde
/// Infrastruktur und keine Login-Daten an NoRisk. Greift zentral in
/// `utils::http_client` (alle Requests an `*.norisk.gg` werden abgelehnt).
pub const NORISK_SERVICES_ENABLED: bool = false;

/// NoRisk Client im Spiel (Einstellung `norisk_ingame`, Standard aus). Ist sie an,
/// meldet sich der Launcher wie der NoRisk-Launcher mit dem Minecraft-Account bei
/// NoRisk an und lädt den NoRisk-Mod aus [`NORISK_INGAME_PACK`] zusätzlich in jedes
/// Salami-Pack. Die übrigen NoRisk-Dienste im Launcher bleiben aus.
static NORISK_INGAME: AtomicBool = AtomicBool::new(false);

/// NoRisk-Pack, aus dem der NoRisk-Mod (`nrc-*`) samt Abhängigkeiten übernommen wird.
pub const NORISK_INGAME_PACK: &str = "norisk-prod";

/// NoRisk-API-Pfade, die der NoRisk-Mod im Spiel braucht: Login (Token), Pack-Liste und
/// Pack-Assets. Alles andere bei NoRisk bleibt gesperrt.
const NORISK_INGAME_PATHS: &[&str] = &[
    "/api/v1/launcher/auth/",
    "/api/v1/launcher/modpacks-v3",
    "/api/v1/launcher/pack/",
];

pub fn set_norisk_ingame(enabled: bool) {
    NORISK_INGAME.store(enabled, Ordering::Relaxed);
}

pub fn norisk_ingame() -> bool {
    NORISK_INGAME.load(Ordering::Relaxed)
}

/// `true`, wenn der Launcher ein NoRisk-Token holen und mitgeben soll.
pub fn norisk_login_enabled() -> bool {
    NORISK_SERVICES_ENABLED || norisk_ingame()
}

/// Update-Quelle des Launchers. Endet sie auf `.json`, ist es ein statisches
/// Tauri-Manifest – hier `latest.json` des neuesten GitHub-Releases (erstellt von
/// `launcher/scripts/release.mjs`). Sonst ein dynamischer Server `{base}/{target}/…`.
/// Updates müssen mit dem Schlüssel zu `plugins.updater.pubkey` (`tauri.conf.json`)
/// signiert sein. `None` = Updater aus.
pub const UPDATE_BASE_URL: Option<&str> =
    Some("https://github.com/umpinke/salami-client/releases/latest/download/latest.json");
/// Beta-Kanal: Updatedatei im festen GitHub-Release `beta` (eine Vorab-Version, wird von
/// `release.mjs --beta` und bei jedem normalen Release aktualisiert). Fehlt sie, gilt die normale.
pub const UPDATE_BASE_URL_BETA: Option<&str> =
    Some("https://github.com/umpinke/salami-client/releases/download/beta/latest.json");

/// Discord-Anwendung „Salami Client“ (Application ID aus dem Discord Developer Portal).
/// Discord zeigt den Namen dieser Anwendung als „Spielt …“ an. `None` = keine
/// Discord-Anzeige (statt der Anwendung des NoRisk Client).
pub const DISCORD_APP_ID: Option<&str> = None;
/// Name des Rich-Presence-Bilds („Art Asset“) in der Discord-Anwendung.
pub const DISCORD_LARGE_IMAGE: &str = "logo";
/// Knopf unter der Discord-Anzeige: (Beschriftung, Link).
pub const DISCORD_BUTTON: (&str, &str) = ("Download", "https://github.com/umpinke/salami-client/releases/latest");

/// Pack für Profile, die auf unbekannte (z. B. NoRisk-)Packs verweisen – etwa nach dem
/// Import einer `.noriskpack`-Datei mit "norisk-prod".
pub const FALLBACK_PACK_ID: &str = "salami";

/// Optionale Remote-Quellen für Packs/Standardprofile (z. B. GitHub raw).
/// `None` = nur die eingebetteten Dateien aus `packs/`.
pub const PACKS_URL: Option<&str> = None;
pub const VERSIONS_URL: Option<&str> = None;

/// Eingebettete Pack-Definitionen (Format wie NoRisk `modpacks-v3`).
pub const BUNDLED_PACKS_JSON: &str = include_str!("../../../packs/salami-packs.json");
/// Eingebettete Standardprofile (Format wie NoRisk `versions-v3`).
pub const BUNDLED_VERSIONS_JSON: &str = include_str!("../../../packs/salami-versions.json");

/// `true`, wenn ein Request an diesen Host/Pfad blockiert werden soll.
pub fn is_blocked_host(host: Option<&str>, path: &str) -> bool {
    is_blocked(host, path, norisk_ingame())
}

fn is_blocked(host: Option<&str>, path: &str, ingame: bool) -> bool {
    let Some(host) = host.filter(|h| *h == "norisk.gg" || h.ends_with(".norisk.gg")) else {
        return false;
    };
    if NORISK_SERVICES_ENABLED {
        return false;
    }
    let is_api = host == "api.norisk.gg" || host == "api-staging.norisk.gg";
    !(ingame && is_api && NORISK_INGAME_PATHS.iter().any(|p| path.starts_with(p)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_norisk_hosts_only() {
        assert!(is_blocked(Some("api.norisk.gg"), "/api/v1/launcher/auth/validate/v2", false));
        assert!(is_blocked(Some("norisk.gg"), "/", false));
        assert!(!is_blocked(Some("api.modrinth.com"), "/", false));
        assert!(!is_blocked(Some("evilnorisk.gg"), "/", false));
        assert!(!is_blocked(None, "/", false));
    }

    #[test]
    fn norisk_ingame_opens_only_login_and_packs() {
        assert!(!is_blocked(Some("api.norisk.gg"), "/api/v1/launcher/auth/request-server-id", true));
        assert!(!is_blocked(Some("api.norisk.gg"), "/api/v1/launcher/modpacks-v3", true));
        assert!(!is_blocked(Some("api-staging.norisk.gg"), "/api/v1/launcher/pack/norisk-prod", true));
        assert!(is_blocked(Some("api.norisk.gg"), "/api/v1/launcher/notifications", true));
        assert!(is_blocked(Some("api.norisk.gg"), "/api/v1/cosmetics/capes", true));
        assert!(is_blocked(Some("flagsmith-staging.norisk.gg"), "/api/v1/launcher/auth/", true));
    }

    #[test]
    fn bundled_json_parses() {
        let packs: crate::integrations::norisk_packs::NoriskModpacksConfig =
            serde_json::from_str(BUNDLED_PACKS_JSON).expect("packs json");
        assert!(packs.packs.contains_key("salami"));
        let versions: crate::integrations::norisk_versions::NoriskVersionsConfig =
            serde_json::from_str(BUNDLED_VERSIONS_JSON).expect("versions json");
        assert_eq!(versions.profiles.len(), 1);
    }
}
