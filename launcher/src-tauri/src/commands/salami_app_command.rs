//! Befehle für die Kopplung mit der Salami-App fürs Handy (siehe `integrations::salami_app`).

use crate::error::{AppError, CommandError};
use crate::integrations::salami_app::{self, AppStatus, Device, PairCode};
use crate::state::state_manager::State;
use log::info;
use uuid::Uuid;

type CommandResult<T> = Result<T, CommandError>;

/// Aktives Minecraft-Konto – Kopplung und Geräte gehören immer zu einem Konto.
async fn active_account() -> Result<(Uuid, String), AppError> {
    let state = State::get().await?;
    let account = state
        .minecraft_account_manager_v2
        .get_active_account()
        .await?
        .ok_or_else(|| AppError::Other("Bitte im Launcher mit einem Minecraft-Konto anmelden".into()))?;
    Ok((account.id, account.username))
}

#[tauri::command]
pub async fn salami_app_status() -> CommandResult<AppStatus> {
    Ok(salami_app::status().await)
}

/// Neuer Kopplungscode (6 Ziffern, 10 min) samt QR-Code. Holt danach im Hintergrund alles
/// nach, damit die App nach dem Koppeln nicht leer ist.
#[tauri::command]
pub async fn salami_app_pair_code() -> CommandResult<PairCode> {
    let (account, name) = active_account().await?;
    let code = salami_app::pair_code(account).await?;
    info!("[Salami-App] pairing code created for {}", name);
    tokio::spawn(salami_app::sync_everything());
    Ok(code)
}

#[tauri::command]
pub async fn salami_app_devices() -> CommandResult<Vec<Device>> {
    let (account, _) = active_account().await?;
    Ok(salami_app::devices(account).await?)
}

#[tauri::command]
pub async fn salami_app_remove_device(id: String) -> CommandResult<()> {
    let (account, _) = active_account().await?;
    salami_app::remove_device(account, &id).await?;
    info!("[Salami-App] device removed");
    Ok(())
}

/// „Daten löschen“: alles auf dem Sync-Server löschen und den Sync ausschalten, damit nicht
/// gleich wieder Daten entstehen. → Anzahl der Konten, deren Daten gelöscht wurden
#[tauri::command]
pub async fn salami_app_delete_data() -> CommandResult<usize> {
    let active = active_account().await.ok().map(|(id, _)| id);
    let deleted = salami_app::delete_all_data(active).await?;
    let state = State::get().await?;
    let mut config = state.config_manager.get_config().await;
    if config.salami_app_sync {
        config.salami_app_sync = false;
        state.config_manager.set_config(config).await?;
    }
    Ok(deleted)
}
