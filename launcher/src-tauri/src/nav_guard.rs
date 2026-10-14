//! Navigationssperre: Launcher-Fenster dürfen nur die eigene, lokale Oberfläche anzeigen.
//! Wird ein Fenster auf eine fremde Seite umgeleitet (z. B. durch einen präparierten Link
//! in einer Mod-Beschreibung), blockt Tauri das hier. Externe Links öffnen weiterhin im
//! Browser über das Opener-Plugin. Ausnahme: das Microsoft-Login-Fenster („signin“) darf
//! Microsoft-Seiten anzeigen.

use log::warn;
use tauri::plugin::{Builder, TauriPlugin};
use tauri::{Runtime, Url};

/// Fenster für den Microsoft-Login (siehe commands/minecraft_auth_command.rs).
const SIGNIN_WINDOW: &str = "signin";

/// Domains, die das Login-Fenster anzeigen darf (inkl. Subdomains).
const SIGNIN_DOMAINS: &[&str] = &[
    "live.com",
    "microsoft.com",
    "microsoftonline.com",
    "xboxlive.com",
    "xbox.com",
    "minecraft.net",
    "aka.ms",
    "github.com", // „Mit GitHub anmelden“ bei Microsoft-Konten
];

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("salami-nav-guard")
        .on_navigation(|webview, url| {
            let allowed = is_allowed(webview.label(), url);
            if !allowed {
                warn!("[NavGuard] Blocked navigation of window '{}' to {}", webview.label(), url);
            }
            allowed
        })
        .build()
}

pub fn is_allowed(window_label: &str, url: &Url) -> bool {
    if is_local(url) {
        return true;
    }
    window_label == SIGNIN_WINDOW && is_signin_url(url)
}

fn is_local(url: &Url) -> bool {
    match url.scheme() {
        "tauri" | "asset" => true,
        "about" => url.as_str() == "about:blank",
        "http" | "https" => match url.host_str() {
            Some("tauri.localhost") | Some("asset.localhost") | Some("ipc.localhost") => true,
            // `tauri dev` lädt die Oberfläche vom Vite-Server
            Some("localhost") | Some("127.0.0.1") => cfg!(debug_assertions),
            _ => false,
        },
        _ => false,
    }
}

fn is_signin_url(url: &Url) -> bool {
    if url.scheme() != "https" {
        return false;
    }
    let Some(host) = url.host_str() else { return false };
    let host = host.to_ascii_lowercase();
    SIGNIN_DOMAINS
        .iter()
        .any(|domain| host == *domain || host.ends_with(&format!(".{domain}")))
}

#[cfg(test)]
mod tests {
    use super::is_allowed;
    use tauri::Url;

    fn url(s: &str) -> Url {
        Url::parse(s).unwrap()
    }

    #[test]
    fn main_window_stays_local() {
        assert!(is_allowed("main", &url("http://tauri.localhost/index.html")));
        assert!(is_allowed("main", &url("tauri://localhost/#/play")));
        assert!(!is_allowed("main", &url("https://evil.example/")));
        assert!(!is_allowed("main", &url("https://login.live.com/")));
        assert!(!is_allowed("main", &url("file:///C:/Users/x/AppData/Roaming/salamiclient/SalamiClient/accounts.json")));
        assert!(!is_allowed("main", &url("javascript:alert(1)")));
    }

    #[test]
    fn signin_window_only_microsoft() {
        assert!(is_allowed("signin", &url("https://login.live.com/oauth20_authorize.srf?x=1")));
        assert!(is_allowed("signin", &url("https://login.microsoftonline.com/consumers")));
        assert!(!is_allowed("signin", &url("https://evil-live.com/")));
        assert!(!is_allowed("signin", &url("http://login.live.com/")));
        assert!(!is_allowed("signin", &url("https://live.com.evil.example/")));
    }
}
