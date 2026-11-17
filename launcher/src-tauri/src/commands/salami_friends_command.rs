//! Befehle für den Freunde-Reiter des Launchers (siehe `integrations::salami_friends`).
//! Antworten des Servers gehen unverändert als JSON an die Oberfläche.

use crate::error::CommandError;
use crate::integrations::salami_friends::{self, FriendsState};
use log::info;
use serde_json::Value;
use std::path::Path;

type CommandResult<T> = Result<T, CommandError>;

#[tauri::command]
pub async fn salami_friends_state() -> CommandResult<FriendsState> {
    Ok(salami_friends::state().await)
}

/// Freunde ein- oder ausschalten. Ein: anmelden und online gehen; aus: Verbindung trennen.
#[tauri::command]
pub async fn salami_friends_set_enabled(enabled: bool) -> CommandResult<FriendsState> {
    info!("Friends {}", if enabled { "enabled" } else { "disabled" });
    salami_friends::set_enabled(enabled)?;
    Ok(salami_friends::state().await)
}

/// Live-Verbindung neu aufbauen, z. B. nach einem Account-Wechsel.
#[tauri::command]
pub async fn salami_friends_reconnect() -> CommandResult<()> {
    if salami_friends::state().await.enabled {
        salami_friends::connect();
    }
    Ok(())
}

/// Freunde (mit Online-Status), offene Anfragen und Ungelesen-Zähler.
#[tauri::command]
pub async fn salami_friends_overview() -> CommandResult<Value> {
    Ok(salami_friends::overview().await?)
}

/// Freundesanfrage per Minecraft-Name.
#[tauri::command]
pub async fn salami_friends_request(name: String) -> CommandResult<Value> {
    Ok(salami_friends::request(&name).await?)
}

/// `action`: `accept`, `deny` (auch: eigene Anfrage zurückziehen) oder `remove`.
#[tauri::command]
pub async fn salami_friends_respond(action: String, uuid: String) -> CommandResult<Value> {
    Ok(salami_friends::respond(&action, &uuid).await?)
}

/// Verlauf mit einem Freund; setzt den Ungelesen-Zähler zurück.
#[tauri::command]
pub async fn salami_friends_messages(with: String) -> CommandResult<Value> {
    Ok(salami_friends::messages(&with).await?)
}

#[tauri::command]
pub async fn salami_friends_send(to: String, text: String) -> CommandResult<Value> {
    Ok(salami_friends::send(&to, &text).await?)
}

#[tauri::command]
pub async fn salami_friends_read(with: String) -> CommandResult<()> {
    salami_friends::mark_read(&with).await?;
    Ok(())
}

/// Bild oder Clip von der Festplatte an einen Freund schicken.
#[tauri::command]
pub async fn salami_friends_send_file(to: String, path: String) -> CommandResult<Value> {
    Ok(salami_friends::send_file(&to, Path::new(&path)).await?)
}

/// Bild oder Clip aus einem Gespräch laden. → Pfad der Datei im Launcher-Ordner
#[tauri::command]
pub async fn salami_friends_media(id: String, mime: String) -> CommandResult<String> {
    Ok(salami_friends::media_file(&id, &mime).await?.display().to_string())
}
