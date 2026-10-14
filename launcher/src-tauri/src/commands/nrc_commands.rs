use crate::error::{AppError, CommandError};
use crate::minecraft::api::norisk_api::{CrashlogDto, NoRiskApi};
use crate::minecraft::api::wordpress_api::{BlogPost, WordPressApi};
use crate::state::state_manager::State;
use log::info;
use log::{debug, error};
use tauri::AppHandle;
use crate::utils::updater_utils;

/// Fetches news and changelog posts from the WordPress API.
///
/// # Returns
///
/// * `Result<Vec<BlogPost>, CommandError>` - A vector of blog posts or an error.
#[tauri::command]
pub async fn get_news_and_changelogs_command() -> Result<Vec<BlogPost>, CommandError> {
    info!("Executing get_news_and_changelogs_command");
    Ok(WordPressApi::get_news_and_changelogs().await?)
}

#[tauri::command]
pub async fn check_crash_log_command(
    payload: CrashlogDto,
) -> Result<serde_json::Value, CommandError> {
    debug!(
        "Executing check_crash_log_command with payload: {:?}",
        payload
    );
    let state = State::get().await?;
    let is_experimental = state.config_manager.is_experimental_mode().await;

    let selected_account_arc = state
        .minecraft_account_manager_v2
        .get_active_account()
        .await?
        .ok_or(AppError::AccountError(
            "No active account found for checking crash log.".to_string(),
        ))?;

    let norisk_creds = &selected_account_arc.norisk_credentials;
    let token = norisk_creds.get_token_for_mode(is_experimental)?;

    let result = NoRiskApi::check_crash_log(
        &token,
        &payload,
        &selected_account_arc.id.to_string(),
        is_experimental,
    )
    .await?;
    Ok(result)
}

#[tauri::command]
pub async fn log_message_command(level: String, message: String) -> Result<(), CommandError> {
    match level.to_lowercase().as_str() {
        "debug" => debug!("[Frontend] {}", message),
        "info" => info!("[Frontend] {}", message),
        "warn" => log::warn!("[Frontend] {}", message),
        "error" => error!("[Frontend] {}", message),
        _ => info!("[Frontend] {}", message),
    }
    Ok(())
}

#[tauri::command]
pub async fn check_update_available_command(app: AppHandle) -> Result<Option<crate::utils::updater_utils::UpdateInfo>, CommandError> {
    debug!("Executing check_update_available_command");

    let state = State::get().await?;
    let config = state.config_manager.get_config().await;
    let is_beta_channel = config.check_beta_channel;

    debug!("Using beta channel setting from config: {}", is_beta_channel);
    Ok(updater_utils::check_update_available(&app, is_beta_channel).await?)
}

#[tauri::command]
pub async fn download_and_install_update_command(app: AppHandle) -> Result<(), CommandError> {
    debug!("Executing download_and_install_update_command");

    let state = State::get().await?;
    let config = state.config_manager.get_config().await;
    let is_beta_channel = config.check_beta_channel;

    debug!("Using beta channel setting from config: {}", is_beta_channel);
    updater_utils::download_and_install_update(&app, is_beta_channel).await?;
    Ok(())
}

