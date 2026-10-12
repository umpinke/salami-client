//! Zentrale Branding- und Dienste-Konfiguration des Salami Client.
//!
//! Alles, was den Launcher als „Salami Client“ ausweist oder festlegt, mit welchen
//! Servern er spricht, steht hier – statt verstreut im Code. Wer den Fork
//! weiterforkt, muss nur diese Datei anpassen.

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

/// Basis-URL des Update-Servers (Tauri-Updater-Manifest), ohne `/{target}/…`.
/// `None` = Updater aus, bis ein eigener Endpoint **und** ein eigener
/// Signaturschlüssel (`plugins.updater.pubkey` in `tauri.conf.json`) existieren.
pub const UPDATE_BASE_URL: Option<&str> = None;
pub const UPDATE_BASE_URL_BETA: Option<&str> = None;

/// Optionale Remote-Quellen für Packs/Standardprofile (z. B. GitHub raw).
/// `None` = nur die eingebetteten Dateien aus `packs/`.
pub const PACKS_URL: Option<&str> = None;
pub const VERSIONS_URL: Option<&str> = None;

/// Eingebettete Pack-Definitionen (Format wie NoRisk `modpacks-v3`).
pub const BUNDLED_PACKS_JSON: &str = include_str!("../../../packs/salami-packs.json");
/// Eingebettete Standardprofile (Format wie NoRisk `versions-v3`).
pub const BUNDLED_VERSIONS_JSON: &str = include_str!("../../../packs/salami-versions.json");

/// `true`, wenn Requests an diesen Host blockiert werden sollen.
pub fn is_blocked_host(host: Option<&str>) -> bool {
    !NORISK_SERVICES_ENABLED
        && host.is_some_and(|h| h == "norisk.gg" || h.ends_with(".norisk.gg"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_norisk_hosts_only() {
        assert!(is_blocked_host(Some("api.norisk.gg")));
        assert!(is_blocked_host(Some("norisk.gg")));
        assert!(!is_blocked_host(Some("api.modrinth.com")));
        assert!(!is_blocked_host(Some("evilnorisk.gg")));
        assert!(!is_blocked_host(None));
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
