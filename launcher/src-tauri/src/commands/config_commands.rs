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
    if let Some(dir) = launch_guard::changed_game_directory(
        current.custom_game_directory.as_deref(),
        config.custom_game_directory.as_deref(),
    ) {
        risky.push(format!("Spielordner / Game folder: {dir}"));
    }
    if !risky.is_empty() && !launch_guard::confirm(&app, &risky).await {
        warn!("[Config] Launch hook / JVM / game folder change was not confirmed, keeping previous values");
        config.hooks = current.hooks.clone();
        config.global_custom_jvm_args = current.global_custom_jvm_args.clone();
        config.custom_game_directory = current.custom_game_directory.clone();
    }

    // Salami: neuer Clip-Ordner nur, wenn der Nutzer ihn im Datei-Dialog gewählt hat oder ihn
    // nativ bestätigt – dort legt der Launcher Dateien ab, listet, lädt hoch und löscht sie.
    if let Err(e) = guard_clip_output_dir(&app, &current, &mut config).await {
        warn!("[Config] Clip folder change refused, keeping the previous one: {}", e.message);
        config.clips.output_dir = current.clips.output_dir.clone();
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
    state.apply_io_limit(config.concurrent_io_limit);

    // Salami: bestätigter Spiel-/Clip-Ordner darf in der Oberfläche angezeigt werden
    crate::utils::asset_scope::allow_config_dirs(&app, &config);

    // Return the updated config
    Ok(config)
}

/// Salami: Clip-Ordner aus der Oberfläche prüfen (siehe `set_launcher_config`).
async fn guard_clip_output_dir(
    app: &AppHandle,
    current: &LauncherConfig,
    config: &mut LauncherConfig,
) -> Result<()> {
    let new_dir = config
        .clips
        .output_dir
        .clone()
        .filter(|dir| !dir.as_os_str().is_empty());
    config.clips.output_dir = new_dir.clone();
    let Some(dir) = new_dir else {
        return Ok(()); // zurück zum Standardordner ist immer erlaubt
    };
    if current.clips.output_dir.as_deref() == Some(dir.as_path()) {
        return Ok(());
    }
    if !dir.is_absolute() {
        return Err(AppError::Other(format!("Clip folder must be an absolute path: {}", dir.display())).into());
    }
    if dir.exists() && !dir.is_dir() {
        return Err(AppError::Other(format!("Clip folder is not a folder: {}", dir.display())).into());
    }
    crate::utils::user_paths::require_user_choice(
        app,
        &dir,
        "Der Launcher soll Clips künftig in diesem Ordner speichern (und dort Clips auflisten, hochladen und löschen):\nThe launcher will save clips to this folder (and list, upload and delete clips there):",
    )
    .await?;
    Ok(())
}

#[command]
pub fn get_app_version(app_handle: AppHandle) -> Result<String> {
    Ok(app_handle.package_info().version.to_string())
}
