//! Befehle für die Cape-Seite des Launchers (siehe `integrations::salami_capes`).

use crate::error::{AppError, CommandError};
use crate::integrations::salami_capes::{self, CapeOverview, CapeReview, CapeView, GalleryCape};
use crate::state::state_manager::State;
use base64::Engine;
use log::{debug, info, warn};
use std::collections::HashMap;
use tauri_plugin_opener::OpenerExt;

type CommandResult<T> = Result<T, CommandError>;

#[tauri::command]
pub async fn salami_capes_overview() -> CommandResult<CapeOverview> {
    Ok(salami_capes::overview(&salami_capes::capes_dir()))
}

/// Eigenes Cape aus einer Bilddatei übernehmen.
#[tauri::command]
pub async fn salami_capes_import(path: String, name: String) -> CommandResult<CapeView> {
    info!("Importing cape from {}", path);
    let bytes = tokio::fs::read(&path).await.map_err(AppError::Io)?;
    Ok(salami_capes::save_cape(&salami_capes::capes_dir(), None, &name, &bytes)?)
}

/// Cape aus dem Editor speichern (`id = None` legt ein neues an).
#[tauri::command]
pub async fn salami_capes_save(id: Option<String>, name: String, png_base64: String) -> CommandResult<CapeView> {
    let data = png_base64.strip_prefix("data:image/png;base64,").unwrap_or(&png_base64);
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data)
        .map_err(|e| AppError::Other(format!("Ungültige Bilddaten: {e}")))?;
    Ok(salami_capes::save_cape(&salami_capes::capes_dir(), id.as_deref(), &name, &bytes)?)
}

#[tauri::command]
pub async fn salami_capes_rename(id: String, name: String) -> CommandResult<()> {
    Ok(salami_capes::rename_cape(&salami_capes::capes_dir(), &id, &name)?)
}

#[tauri::command]
pub async fn salami_capes_delete(id: String) -> CommandResult<()> {
    Ok(salami_capes::delete_cape(&salami_capes::capes_dir(), &id)?)
}

/// `builtin:<name>`, ID eines eigenen Capes oder `None` für kein Cape.
#[tauri::command]
pub async fn salami_capes_select(selection: Option<String>) -> CommandResult<()> {
    Ok(salami_capes::select(&salami_capes::capes_dir(), selection.as_deref())?)
}

/// Prüfstatus eigener Capes auf dem Cape-Server (Schlüssel: `share_id`). Noch nie eingereichte
/// eigene Capes reicht der Launcher dabei mit dem aktiven Account zur Prüfung ein – so landen
/// neue Capes gleich auf der Prüfseite, nicht erst wenn man mit ihnen spielt.
#[tauri::command]
pub async fn salami_capes_review_status(ids: Vec<String>) -> CommandResult<HashMap<String, CapeReview>> {
    let mut status = salami_capes::review_status(&ids).await?;
    if salami_capes::server_url().is_none() || ids.iter().all(|id| status.contains_key(id)) {
        return Ok(status);
    }
    // Ohne Account (oder offline) bleibt es beim Einreichen beim Spielen
    let account = match State::get().await {
        Ok(state) => state.minecraft_account_manager_v2.get_active_account().await,
        Err(e) => Err(e),
    };
    let account = match account {
        Ok(Some(account)) => account,
        Ok(None) => return Ok(status),
        Err(e) => {
            debug!("Capes not submitted, no usable account: {e}");
            return Ok(status);
        }
    };
    for (id, png) in salami_capes::take_unsubmitted(&salami_capes::capes_dir(), &ids, &status) {
        match salami_capes::submit_for_review(account.id, &account.username, &account.access_token, &png).await {
            Ok(review) => {
                info!("Cape {} submitted for review: {}", &id[..12], review.status);
                status.insert(id, review);
            }
            Err(e) => warn!("Cape {} could not be submitted for review: {e}", &id[..12]),
        }
    }
    Ok(status)
}

/// Galerie: von Admins freigegebene Capes anderer Spieler, die jeder selbst tragen kann.
#[tauri::command]
pub async fn salami_capes_gallery() -> CommandResult<Vec<GalleryCape>> {
    let capes = salami_capes::gallery().await?;
    salami_capes::prune_gallery_cache(&salami_capes::capes_dir(), &capes);
    Ok(capes)
}

/// Bild eines Galerie-Capes als Data-URL (wird zwischengespeichert).
#[tauri::command]
pub async fn salami_capes_gallery_image(id: String) -> CommandResult<String> {
    let png = salami_capes::gallery_image(&salami_capes::capes_dir(), &id).await?;
    Ok(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(png)))
}

/// Galerie-Cape zu den eigenen Capes holen. Das Frontend wählt es danach aus.
#[tauri::command]
pub async fn salami_capes_adopt(id: String, name: String) -> CommandResult<CapeView> {
    info!("Adopting gallery cape {}", id.get(..12).unwrap_or(&id));
    Ok(salami_capes::adopt_gallery_cape(&salami_capes::capes_dir(), &id, &name).await?)
}

/// Galerie-Cape melden, das gegen die Cape-Regeln verstößt (`reason`: siehe
/// [`salami_capes::REPORT_REASONS`]). Braucht einen angemeldeten Account – jede Meldung gehört
/// zu einem echten Spieler. → `reported` oder `already_reported`
#[tauri::command]
pub async fn salami_capes_report(id: String, reason: String, note: Option<String>) -> CommandResult<String> {
    let state = State::get().await?;
    let account = state
        .minecraft_account_manager_v2
        .get_active_account()
        .await?
        .ok_or_else(|| AppError::Other("Zum Melden bitte im Launcher mit einem Minecraft-Account anmelden".into()))?;
    let status = salami_capes::report_cape(
        account.id,
        &account.username,
        &account.access_token,
        &id,
        &reason,
        note.as_deref().unwrap_or(""),
    )
    .await?;
    info!("Cape {} reported ({reason}): {status}", id.get(..12).unwrap_or(&id));
    Ok(status)
}

/// Legt die Vorlage in den Download-Ordner und zeigt sie im Explorer.
#[tauri::command]
pub async fn salami_capes_download_template(app_handle: tauri::AppHandle) -> CommandResult<String> {
    let downloads = directories::UserDirs::new()
        .and_then(|dirs| dirs.download_dir().map(|d| d.to_path_buf()))
        .ok_or_else(|| AppError::Other("Download-Ordner nicht gefunden".into()))?;
    let path = downloads.join("salami_cape_vorlage.png");
    tokio::fs::write(&path, salami_capes::TEMPLATE_PNG).await.map_err(AppError::Io)?;
    debug!("Cape template written to {}", path.display());
    let shown = path.display().to_string();
    app_handle
        .opener()
        .reveal_item_in_dir(&path)
        .map_err(|e| AppError::Other(format!("Explorer konnte nicht geöffnet werden: {e}")))?;
    Ok(shown)
}
