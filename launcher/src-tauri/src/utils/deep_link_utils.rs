use log::{info, warn};
use serde::Serialize;
use tauri::AppHandle;
use url::Url;

#[derive(Clone, Serialize)]
pub struct AuthBridgeRequest {
    pub session_id: String,
    pub username: String,
}

#[derive(Clone, Serialize)]
pub struct AuthBridgeResult {
    pub success: bool,
    pub message: String,
}

/// Handles incoming deep link URLs.
/// Parses the URL scheme and dispatches to the appropriate handler.
pub async fn handle_deep_link(_app_handle: &AppHandle, urls: Vec<Url>) {
    for url in urls {
        info!("[DeepLink] Received URL: {}", url);

        if url.scheme() != crate::branding::DEEP_LINK_SCHEME {
            warn!("[DeepLink] Ignoring URL with unknown scheme: {}", url.scheme());
            continue;
        }

        match url.host_str() {
            Some("auth") => {
                // Salami: Der Website-Login war NoRisks Konto-Brücke – gibt es nicht mehr.
                warn!("[DeepLink] Ignoring auth link (not supported in {})", crate::branding::PRODUCT_NAME);
            }
            Some(host) => {
                warn!("[DeepLink] Unknown deep link host: {}", host);
            }
            None => {
                warn!("[DeepLink] Deep link URL has no host: {}", url);
            }
        }
    }
}
