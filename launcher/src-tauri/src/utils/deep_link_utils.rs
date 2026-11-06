use log::{info, warn};
use tauri::AppHandle;
use url::Url;

/// Handles incoming deep link URLs.
/// Parses the URL scheme and dispatches to the appropriate handler.
/// Salami: Es gibt derzeit keine Ziele; NoRisks Website-Login (`…://auth`) ist entfernt.
pub async fn handle_deep_link(_app_handle: &AppHandle, urls: Vec<Url>) {
    for url in urls {
        info!("[DeepLink] Received URL: {}", url);

        if url.scheme() != crate::branding::DEEP_LINK_SCHEME {
            warn!("[DeepLink] Ignoring URL with unknown scheme: {}", url.scheme());
            continue;
        }

        match url.host_str() {
            Some(host) => {
                warn!("[DeepLink] Unknown deep link host: {}", host);
            }
            None => {
                warn!("[DeepLink] Deep link URL has no host: {}", url);
            }
        }
    }
}
