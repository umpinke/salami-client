use crate::error::AppError;
use crate::utils::deep_link_utils::AuthBridgeResult;

/// Tauri command called by the frontend after user confirms the auth bridge request.
#[tauri::command]
pub async fn confirm_auth_bridge(_session_id: String) -> Result<AuthBridgeResult, crate::error::CommandError> {
    // Salami: keine Konto-Brücke zu NoRisk
    Err(AppError::Other("Website login is not supported".to_string()).into())
}

