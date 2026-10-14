use crate::error::{AppError, CommandError};
use crate::state::{config_state::LauncherConfig, State};
use crate::utils::launch_guard;
use log::warn;
use tauri::command;
use tauri::AppHandle;

type Result<T> = std::result::Result<T, CommandError>;

#[command]
pub async fn get_launcher_config() -> Result<LauncherConfig> {
    let state = State::get().await?;
    let config = state.config_manager.get_config().await;
    Ok(config)
}

#[command]
pub async fn set_launcher_config(app: AppHandle, mut config: LauncherConfig) -> Result<LauncherConfig> {
    let state = State::get().await?;

    // Salami: Start-Befehle und code-ladende JVM-Optionen nur nach nativer Bestätigung
    let current = state.config_manager.get_config().await;
    let mut risky = launch_guard::changed_hooks(&current.hooks, &config.hooks);
    let new_jvm = launch_guard::new_dangerous_jvm_args(
        current.global_custom_jvm_args.as_deref(),
        config.global_custom_jvm_args.as_deref(),
    );
    if !new_jvm.is_empty() {
        risky.push(format!("JVM: {}", new_jvm.join(" ")));
    }
    if !risky.is_empty() && !launch_guard::confirm(&app, &risky).await {
        warn!("[Config] Launch hook / JVM change was not confirmed, keeping previous values");
        config.hooks = current.hooks.clone();
        config.global_custom_jvm_args = current.global_custom_jvm_args.clone();
    }

    // Validate concurrent downloads value
    if config.concurrent_downloads == 0 || config.concurrent_downloads > 10 {
        return Err(CommandError::from(AppError::Other(format!(
            "Concurrent downloads must be between 1 and 10 (got: {})",
            config.concurrent_downloads
        ))));
    }

    // Set the entire configuration
    state.config_manager.set_config(config.clone()).await?;

    // Return the updated config
    Ok(config)
}

#[command]
pub fn get_app_version(app_handle: AppHandle) -> Result<String> {
    Ok(app_handle.package_info().version.to_string())
}
