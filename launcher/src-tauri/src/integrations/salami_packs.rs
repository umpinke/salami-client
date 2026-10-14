//! Quelle für Pack-Definitionen und Standardprofile des Salami Client.
//!
//! Ersetzt die NoRisk-Endpunkte `launcher/modpacks-v3` und `launcher/versions-v3`:
//! Ist in [`crate::branding`] eine Remote-URL gesetzt, wird sie ohne Token
//! abgefragt; schlägt das fehl (oder ist keine gesetzt), gelten die
//! eingebetteten Dateien aus `packs/`. Der Launcher funktioniert damit auch
//! komplett offline.

use crate::branding;
use crate::config::HTTP_CLIENT;
use crate::error::{AppError, Result};
use crate::integrations::norisk_packs::NoriskModpacksConfig;
use crate::integrations::norisk_versions::NoriskVersionsConfig;
use log::{info, warn};
use serde::de::DeserializeOwned;

/// Lädt die Pack-Definitionen (Remote, sonst eingebettet).
pub async fn load_packs() -> Result<NoriskModpacksConfig> {
    load(branding::PACKS_URL, branding::BUNDLED_PACKS_JSON, "packs").await
}

/// Lädt die Standardprofile (Remote, sonst eingebettet).
pub async fn load_versions() -> Result<NoriskVersionsConfig> {
    load(branding::VERSIONS_URL, branding::BUNDLED_VERSIONS_JSON, "versions").await
}

async fn load<T: DeserializeOwned>(remote: Option<&str>, bundled: &str, what: &str) -> Result<T> {
    if let Some(url) = remote {
        match fetch(url).await {
            Ok(value) => {
                info!("[Salami] Loaded {} from {}", what, url);
                return Ok(value);
            }
            Err(e) => warn!("[Salami] Could not load {} from {} ({}), using bundled copy", what, url, e),
        }
    }
    serde_json::from_str(bundled)
        .map_err(|e| AppError::ParseError(format!("Bundled Salami {} are invalid: {}", what, e)))
}

async fn fetch<T: DeserializeOwned>(url: &str) -> Result<T> {
    let response = HTTP_CLIENT
        .get(url)
        .send()
        .await
        .map_err(|e| AppError::RequestError(e.to_string()))?
        .error_for_status()
        .map_err(|e| AppError::RequestError(e.to_string()))?;
    response
        .json::<T>()
        .await
        .map_err(|e| AppError::ParseError(e.to_string()))
}
