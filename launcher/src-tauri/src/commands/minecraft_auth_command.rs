use crate::commands::oauth_error_html::ERROR_HTML;
use crate::commands::oauth_success_html::SUCCESS_HTML;
use crate::error::{AppError, CommandError};
use crate::minecraft::minecraft_auth::Credentials;
use crate::state::event_state::{EventPayload, EventType};
use crate::state::state_manager::State;
use crate::utils::updater_utils;
use chrono::{Duration, Utc};
use log::{error, info};
use tauri::plugin::TauriPlugin;
use tauri::Manager;
use tauri::{Runtime, UserAttentionType};
use tauri_plugin_opener::OpenerExt;
use uuid::Uuid;

/// Account-Daten für die Oberfläche – ohne Token. Die Login-Token bleiben im Backend, damit
/// selbst ein eingeschleustes Skript in der Oberfläche sie nicht auslesen kann.
#[derive(serde::Serialize, Clone, Debug)]
pub struct AccountInfo {
    pub id: Uuid,
    pub username: String,
    pub minecraft_username: String,
    pub active: bool,
    pub expires_at: chrono::DateTime<Utc>,
    pub ignore_child_protection_warning: bool,
}

impl From<&Credentials> for AccountInfo {
    fn from(c: &Credentials) -> Self {
        Self {
            id: c.id,
            username: c.username.clone(),
            minecraft_username: c.username.clone(),
            active: c.active,
            expires_at: c.expires,
            ignore_child_protection_warning: c.ignore_child_protection_warning,
        }
    }
}

//TODO das wäre geiler aber habs noch nicht hinbekommen
//Error during login: minecraft_auth.begin_login not allowed. Plugin not found
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::<R>::new("minecraft_auth")
        .invoke_handler(tauri::generate_handler![
            begin_login,
            cancel_login,
            remove_account,
            get_active_account,
            set_active_account,
            get_accounts
        ])
        .build()
}

/// Begin the Minecraft login flow
/// Returns a URL that the user needs to visit to sign in
#[tauri::command]
pub async fn begin_login<R: Runtime>(
    app: tauri::AppHandle<R>,
) -> Result<Option<AccountInfo>, CommandError> {
    Ok(begin_login_inner(app).await?.as_ref().map(AccountInfo::from))
}

async fn begin_login_inner<R: Runtime>(
    app: tauri::AppHandle<R>,
) -> Result<Option<Credentials>, CommandError> {
    let state = State::get().await?;
    let config = state.config_manager.get_config().await;
    let use_browser_based_login = updater_utils::is_flatpak() || config.use_browser_based_login;

    if use_browser_based_login {
        // Flatpak: Use external browser with local HTTP server
        info!("[Login] Using external browser flow (Flatpak detected)");

        // Salami: Port sofort belegen und halten (kein Wettlauf zwischen Prüfen und Binden) –
        // auf 127.0.0.1 und, falls vorhanden, [::1], damit kein anderes Programm unter
        // „localhost“ die Login-Antwort abfängt.
        let (port, listeners) = crate::minecraft::auth::minecraft_auth::bind_oauth_listeners(25585)
            .await
            .map_err(CommandError::from)?;

        let redirect_uri = format!("http://localhost:{}/callback", port);
        info!("[Login] Using redirect URI: {}", redirect_uri);

        // Emit login started event
        let login_event_id = Uuid::new_v4();
        state.emit_event(EventPayload {
            event_id: login_event_id,
            event_type: EventType::AccountLoginStarted,
            target_id: None,
            message: "Starting browser-based login process".to_string(),
            progress: Some(0.0),
            error: None,
        }).await?;

        // Start the direct OAuth2 flow (for localhost redirect)
        let flow = State::get()
            .await?
            .minecraft_account_manager_v2
            .login_begin_direct_oauth(&redirect_uri)
            .await?;

        // Use embedded HTML templates (works on all platforms including Flatpak)
        let success_html = SUCCESS_HTML.to_string();
        let error_html = ERROR_HTML.to_string();

        // Start the OAuth callback server (accepts only callbacks carrying this flow's state)
        let (server_handle, mut code_rx) = crate::minecraft::auth::minecraft_auth::start_oauth_callback_server(
            listeners,
            flow.state.clone(),
            success_html,
            error_html,
        )
        .await
        .map_err(|e| CommandError::from(AppError::Other(format!("Failed to start OAuth server: {}", e))))?;

        // Store the server handle in state for cancellation
        {
            let mut handle_guard = state.login_server_handle.lock().await;
            *handle_guard = Some(server_handle);
        }

        // Emit waiting for browser event
        state.emit_event(EventPayload {
            event_id: login_event_id,
            event_type: EventType::AccountLoginWaitingForBrowser,
            target_id: None,
            message: "Opening browser window for authentication".to_string(),
            progress: Some(10.0),
            error: None,
        }).await?;

        // Open the browser with the authorization URL
        info!("[Login] Opening browser with URL: {}", flow.authorize_url);
        app.opener()
            .open_url(&flow.authorize_url, None::<&str>)
            .map_err(|e| CommandError::from(AppError::Other(format!("Failed to open browser: {}", e))))?;

        // Wait for the callback (with timeout)
        let start = Utc::now();
        let timeout = Duration::seconds(600); // 10 minutes

        loop {
            if (Utc::now() - start) >= timeout {
                info!("[Login] Timeout waiting for OAuth callback");
                // Abort server via state
                if let Some(handle) = state.login_server_handle.lock().await.take() {
                    handle.abort();
                }
                let error_msg = "Login timeout: No response from browser after 10 minutes".to_string();
                state.emit_event(EventPayload {
                    event_id: login_event_id,
                    event_type: EventType::Error,
                    target_id: None,
                    message: error_msg.clone(),
                    progress: None,
                    error: Some(error_msg),
                }).await?;
                return Ok(None);
            }

            // Check if we received the code
            match code_rx.try_recv() {
                Ok(Ok(code)) => {
                    info!("[Login] Received authorization code");
                    // Abort server via state
                    if let Some(handle) = state.login_server_handle.lock().await.take() {
                        handle.abort();
                    }
                    
                    // Emit exchanging token event
                    state.emit_event(EventPayload {
                        event_id: login_event_id,
                        event_type: EventType::AccountLoginExchangingToken,
                        target_id: None,
                        message: "Exchanging authorization code for access token".to_string(),
                        progress: Some(30.0),
                        error: None,
                    }).await?;
                    
                    // Complete the direct OAuth2 login flow with the code
                    match State::get()
                        .await?
                        .minecraft_account_manager_v2
                        .login_finish_direct_oauth_with_events(&code, flow, login_event_id)
                        .await
                    {
                        Ok(account) => {
                            // Emit completed event
                            state.emit_event(EventPayload {
                                event_id: login_event_id,
                                event_type: EventType::AccountLoginCompleted,
                                target_id: None,
                                message: format!("Login completed successfully for {}", account.username),
                                progress: Some(100.0),
                                error: None,
                            }).await?;

                            return Ok(Some(account));
                        }
                        Err(e) => {
                            error!("[Login] Error during login flow: {:?}", e);
                            let error_msg = format!("Login failed: {}", e);
                            state.emit_event(EventPayload {
                                event_id: login_event_id,
                                event_type: EventType::Error,
                                target_id: None,
                                message: error_msg.clone(),
                                progress: None,
                                error: Some(error_msg),
                            }).await?;
                            return Err(CommandError::from(e));
                        }
                    }
                }
                Ok(Err(e)) => {
                    error!("[Login] OAuth callback error: {:?}", e);
                    // Abort server via state
                    if let Some(handle) = state.login_server_handle.lock().await.take() {
                        handle.abort();
                    }
                    let error_msg = format!("OAuth callback error: {}", e);
                    state.emit_event(EventPayload {
                        event_id: login_event_id,
                        event_type: EventType::Error,
                        target_id: None,
                        message: error_msg.clone(),
                        progress: None,
                        error: Some(error_msg),
                    }).await?;
                    return Err(CommandError::from(e));
                }
                Err(tokio::sync::oneshot::error::TryRecvError::Empty) => {
                    // Still waiting, continue
                }
                Err(tokio::sync::oneshot::error::TryRecvError::Closed) => {
                    error!("[Login] OAuth callback channel closed unexpectedly");
                    // Clear the server handle
                    {
                        let mut handle_guard = state.login_server_handle.lock().await;
                        *handle_guard = None;
                    }
                    let error_msg = "Login connection closed unexpectedly".to_string();
                    state.emit_event(EventPayload {
                        event_id: login_event_id,
                        event_type: EventType::Error,
                        target_id: None,
                        message: error_msg.clone(),
                        progress: None,
                        error: Some(error_msg),
                    }).await?;
                    return Ok(None);
                }
            }

            tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        }
    } else {
        // Non-Flatpak: Use Tauri webview window (existing behavior)
        info!("[Login] Using Tauri webview flow (non-Flatpak)");

        let flow = State::get()
            .await?
            .minecraft_account_manager_v2
            .login_begin(None)
            .await?;

        // Close any existing sign-in window
        if let Some(window) = app.get_webview_window("signin") {
            window.close().map_err(|e| AppError::Other(e.to_string()))?;
        }

        // Create a new window for the sign-in process
        let window =
            tauri::WebviewWindowBuilder::new(
                &app,
                "signin",
                tauri::WebviewUrl::External(flow.redirect_uri.parse().map_err(|_| {
                    AppError::AccountError("Error parsing auth redirect URL".to_string())
                })?),
            )
            .title("Sign into Minecraft")
            .always_on_top(true)
            .center()
            .build()
            .map_err(|e| AppError::Other(e.to_string()))?;

        window
            .request_user_attention(Some(UserAttentionType::Critical))
            .map_err(|e| AppError::Other(e.to_string()))?;

        let start = Utc::now();

        // Wait for the user to complete the login (10 minutes = 600 seconds)
        while (Utc::now() - start) < Duration::seconds(600) {
            if window.title().is_err() {
                // User closed the window, cancelling flow
                window.close().map_err(|e| AppError::Other(e.to_string()))?;
                return Ok(None);
            }

            if let Ok(url) = window.url() {
                if url
                    .as_str()
                    .starts_with("https://login.live.com/oauth20_desktop.srf")
                {
                    if let Some((_, code)) = url.query_pairs().find(|x| x.0 == "code") {
                        window.close().map_err(|e| AppError::Other(e.to_string()))?;

                        // Complete the login flow with the code
                        let account = State::get()
                            .await?
                            .minecraft_account_manager_v2
                            .login_finish(&code, flow)
                            .await?;


                        return Ok(Some(account));
                    }
                }
            }

            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
        }

        window.close().map_err(|e| AppError::Other(e.to_string()))?;
        Ok(None)
    }
}

/// Remove a Minecraft account
#[tauri::command]
pub async fn remove_account(account_id: Uuid) -> Result<(), CommandError> {
    let state = State::get().await?;
    state
        .minecraft_account_manager_v2
        .remove_account(account_id)
        .await?;
    state
        .content_cache
        .forget_player(&account_id)
        .await;
    Ok(())
}

/// Cancel an ongoing browser-based login
#[tauri::command]
pub async fn cancel_login() -> Result<(), CommandError> {
    let state = State::get().await?;
    let mut handle_guard = state.login_server_handle.lock().await;
    
    if let Some(handle) = handle_guard.take() {
        info!("[Login] Cancelling browser-based login");
        handle.abort();
        Ok(())
    } else {
        Err(CommandError::from(AppError::Other("No active login to cancel".to_string())))
    }
}

/// Check if the application is running in a Flatpak environment
#[tauri::command]
pub fn is_flatpak() -> bool {
    updater_utils::is_flatpak()
}

/// Get the currently active Minecraft account
#[tauri::command]
pub async fn get_active_account() -> Result<Option<AccountInfo>, CommandError> {
    let state = State::get().await?;
    let account = state
        .minecraft_account_manager_v2
        .get_active_account()
        .await?;
    Ok(account.as_ref().map(AccountInfo::from))
}

/// Set the active Minecraft account
#[tauri::command]
pub async fn set_active_account(account_id: Uuid) -> Result<(), CommandError> {
    let state = State::get().await?;
    state
        .minecraft_account_manager_v2
        .set_active_account(account_id)
        .await?;

    tokio::spawn(async move {
        if let Ok(state) = State::get().await {
            match state.active_skin_manager.reconcile(&account_id.to_string()).await {
                Ok(changed) => {
                    if changed {
                        let _ = state.event_state.skin_changed(Some(account_id)).await;
                    }
                }
                Err(e) => log::warn!("Skin reconcile after account switch failed: {}", e),
            }
        }
    });

    Ok(())
}

/// Get all Minecraft accounts
#[tauri::command]
pub async fn get_accounts() -> Result<Vec<AccountInfo>, CommandError> {
    let state = State::get().await?;
    let accounts = state
        .minecraft_account_manager_v2
        .get_all_accounts()
        .await?;
    Ok(accounts.iter().map(AccountInfo::from).collect())
}
