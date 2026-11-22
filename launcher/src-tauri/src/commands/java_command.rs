use crate::error::CommandError;
use crate::utils::java_detector::{
    detect_java_installations, find_best_java_for_minecraft, get_java_info, invalidate_java_cache,
    JavaInstallation,
};
use log::info;
use std::path::{Path, PathBuf};

/// Salami: `java -version` startet das Programm unter diesem Pfad. Aus der Oberfläche nur Pfade
/// ohne Rückfrage: erkannte Java-Installationen, vom Launcher geladenes Java (Launcher-Ordner),
/// das schon bestätigte Java eines Profils oder eine Auswahl im Datei-Dialog. Alles andere fragt
/// nativ nach; Netzwerkpfade (UNC/WebDAV) nie – von dort liefe ein fremdes Programm.
async fn require_trusted_java(app: &tauri::AppHandle, path: &Path) -> Result<(), CommandError> {
    crate::utils::path_guard::reject_network_path(path)?;
    if is_known_java(path).await {
        return Ok(());
    }
    crate::utils::user_paths::require_user_choice(
        app,
        path,
        "Der Launcher soll dieses Programm als Java starten (java -version):\nThe launcher will run this program as Java (java -version):",
    )
    .await?;
    Ok(())
}

async fn is_known_java(path: &Path) -> bool {
    let same = |a: &Path, b: &Path| crate::sync::paths::same_location(a, b);
    // vom Launcher geladenes Java liegt in den Launcher-Ordnern
    if crate::utils::path_guard::ensure_allowed(path).is_ok() {
        return true;
    }
    if let Ok(found) = detect_java_installations().await {
        if found.iter().any(|java| {
            let candidate = Path::new(&java.path);
            same(candidate, path) || candidate.parent().and_then(Path::parent).is_some_and(|home| same(home, path))
        }) {
            return true;
        }
    }
    if let Ok(state) = crate::state::state_manager::State::get().await {
        if let Ok(profiles) = state.profile_manager.list_profiles().await {
            return profiles.iter().any(|profile| {
                profile.settings.use_custom_java_path
                    && profile
                        .settings
                        .java_path
                        .as_deref()
                        .is_some_and(|stored| same(Path::new(stored), path))
            });
        }
    }
    false
}

/// Detects all Java installations on the system
#[tauri::command]
pub async fn detect_java_installations_command() -> Result<Vec<JavaInstallation>, CommandError> {
    info!("Command: Detecting Java installations");
    Ok(detect_java_installations().await?)
}

/// Gets information about a Java installation at the given path
#[tauri::command]
pub async fn get_java_info_command(
    app: tauri::AppHandle,
    path: String,
) -> Result<JavaInstallation, CommandError> {
    info!("Command: Getting Java info for path: {}", path);
    let java_path = PathBuf::from(path);
    require_trusted_java(&app, &java_path).await?;
    Ok(get_java_info(&java_path).await?)
}

/// Finds the best Java installation for the given Minecraft version
#[tauri::command]
pub async fn find_best_java_for_minecraft_command(
    minecraft_version: String,
) -> Result<Option<JavaInstallation>, CommandError> {
    info!(
        "Command: Finding best Java for Minecraft version: {}",
        minecraft_version
    );
    Ok(find_best_java_for_minecraft(&minecraft_version).await?)
}

/// Invalidates the Java installation cache, forcing a fresh scan on the next query
#[tauri::command]
pub async fn invalidate_java_cache_command() -> Result<(), CommandError> {
    info!("Command: Invalidating Java cache");
    invalidate_java_cache().await;
    Ok(())
}

/// Checks if a custom Java path exists and is valid
#[tauri::command]
pub async fn validate_java_path_command(
    app: tauri::AppHandle,
    path: String,
) -> Result<bool, CommandError> {
    info!("Command: Validating Java path: {}", path);
    let java_path = PathBuf::from(path);

    // Salami: Netzwerkpfade nicht einmal ansehen (schon das würde den fremden Rechner kontaktieren)
    if crate::utils::path_guard::is_network_path(&java_path) {
        info!("Java path is a network path, refusing: {}", java_path.display());
        return Ok(false);
    }

    // First check if the path exists
    if !java_path.exists() {
        info!("Java path does not exist: {}", java_path.display());
        return Ok(false);
    }

    // Salami: nur bekanntes/gewähltes Java starten, sonst nativ nachfragen
    require_trusted_java(&app, &java_path).await?;

    // Then try to get info about it (will run java -version)
    match get_java_info(&java_path).await {
        Ok(_) => {
            info!("Java path is valid: {}", java_path.display());
            Ok(true)
        }
        Err(e) => {
            info!(
                "Java path is invalid: {} (Error: {})",
                java_path.display(),
                e
            );
            Ok(false)
        }
    }
}
