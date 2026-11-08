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

/// Reservierter Ordner unter `profiles/`: gemeinsame Spielordner der Client-Gruppe
/// (`new`/`legacy`), Wegwerf-Profile (`temp`) und die Standard-Profile (`<version>`).
pub const PROFILES_NAMESPACE: &str = "salamiclient";
/// Bis 1.6.0 hießen `new`, `legacy` und `temp` noch `noriskclient/…`; beim Start
/// verschiebt `legacy_cleanup::migrate_profile_folders` sie nach [`PROFILES_NAMESPACE`].
pub const LEGACY_PROFILES_NAMESPACE: &str = "noriskclient";

/// URL-Schema für Deep-Links (`salami://…`), muss zu `plugins.deep-link` in `tauri.conf.json` passen.
pub const DEEP_LINK_SCHEME: &str = "salami";

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

/// Eigener Cape-Server (`server/capes/`), damit alle Salami-Spieler die Capes der anderen
/// sehen. Geht beim Spielstart als `-Dsalami.capes.url` an salami-core. Eigene Capes sehen
/// andere erst, wenn ein Admin sie auf der Prüf-Webseite (`/admin/`) freigegeben hat.
/// Läuft unter dem Pfad `/capes` auf derselben Domain wie der Online-Zähler (kein eigener
/// DNS-Eintrag nötig). `None` = Capes sind nur für den Spieler selbst sichtbar.
pub const CAPE_SERVER_URL: Option<&str> = Some("https://salami-client-zaehler.umpinke.de/capes");

/// Sync-Server der Salami-App fürs Handy (Android). Der Launcher meldet dort Spielzeit und
/// Screenshots und erzeugt Kopplungscodes (`integrations::salami_app`). Läuft unter `/app` auf
/// derselben Domain wie der Online-Zähler. `None` = keine Kopplung, nichts wird gesendet.
pub const APP_SERVER_URL: Option<&str> = Some("https://salami-client-zaehler.umpinke.de/app");
/// Download der Salami-App (Link im Dialog „Handy koppeln“).
pub const APP_DOWNLOAD_PAGE: &str = "https://salami-client.umpinke.de/#download";
/// Datenschutz-Hinweise der Website, mit dem Absatz „Salami-App und Launcher“.
pub const APP_PRIVACY_PAGE: &str = "https://salami-client.umpinke.de/#info";

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
/// (`norisk.gg` und alle Subdomains) sind im Salami Client immer gesperrt. Der Launcher
/// selbst spricht nicht mehr mit NoRisk; die Sperre fängt Adressen ab, die z. B. aus
/// importierten Packs oder Profilen stammen. Greift in `config::BlockingResolver`
/// (alle HTTP-Clients) und in `utils::http_client`.
pub fn is_blocked_host(host: Option<&str>) -> bool {
    host.is_some_and(|h| {
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
