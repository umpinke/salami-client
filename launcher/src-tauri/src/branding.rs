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
/// Discord-Anzeige.
pub const DISCORD_APP_ID: Option<&str> = Some("1559842146469814293");
/// Name des Rich-Presence-Bilds („Art Asset“) in der Discord-Anwendung.
pub const DISCORD_LARGE_IMAGE: &str = "logo";
/// Knopf unter der Discord-Anzeige: (Beschriftung, Link).
pub const DISCORD_BUTTON: (&str, &str) = ("Download", "https://github.com/umpinke/salami-client/releases/latest");

/// Eigener Online-Zähler (`server/online-counter/`), z. B. `Some("https://online.example.de")`.
/// Der Launcher meldet sich dort alle 30 s anonym (zufällige ID pro Start) und zeigt im
/// Spielen-Tab, wie viele Launcher gerade offen sind. `None` = Zähler und Anzeige aus.
pub const ONLINE_COUNTER_URL: Option<&str> = Some("https://salami-client-zaehler.umpinke.de");

/// Pack für Profile, die auf unbekannte Packs verweisen (z. B. aus importierten Profilen
/// anderer Launcher).
pub const FALLBACK_PACK_ID: &str = "salami";

/// Optionale Remote-Quellen für Packs/Standardprofile (z. B. GitHub raw).
/// `None` = nur die eingebetteten Dateien aus `packs/`.
pub const PACKS_URL: Option<&str> = None;
pub const VERSIONS_URL: Option<&str> = None;

/// Eingebettete Pack-Definitionen (Format wie NoRisk `modpacks-v3`).
pub const BUNDLED_PACKS_JSON: &str = include_str!("../../../packs/salami-packs.json");
/// Eingebettete Standardprofile (Format wie NoRisk `versions-v3`).
pub const BUNDLED_VERSIONS_JSON: &str = include_str!("../../../packs/salami-versions.json");

/// `true`, wenn ein Request an diesen Host blockiert werden soll: NoRisk-Server
/// (`norisk.gg` und alle Subdomains) sind im Salami Client immer gesperrt.
pub fn is_blocked_host(host: Option<&str>) -> bool {
    !NORISK_SERVICES_ENABLED
        && host.is_some_and(|h| {
            let h = h.trim_end_matches('.').to_ascii_lowercase();
            h == "norisk.gg" || h.ends_with(".norisk.gg")
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_norisk_hosts_only() {
        assert!(is_blocked_host(Some("api.norisk.gg")));
        assert!(is_blocked_host(Some("norisk.gg")));
        assert!(is_blocked_host(Some("cdn.norisk.gg")));
        assert!(is_blocked_host(Some("API.NoRisk.gg.")));
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
