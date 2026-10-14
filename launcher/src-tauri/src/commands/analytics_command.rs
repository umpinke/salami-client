use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use log::debug;
use std::collections::HashMap;
use tauri::command;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnalyticsEvent {
    #[serde(rename = "event_type")]
    pub event_type: String,
    pub timestamp: DateTime<Utc>,
    #[serde(rename = "session_id")]
    pub session_id: String,
    #[serde(rename = "user_id")]
    pub user_id: String,
    pub properties: Option<HashMap<String, Value>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TrackEventRequest {
    pub events: Vec<AnalyticsEvent>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TrackEventResponse {
    pub success: bool,
    pub message: Option<String>,
}

pub fn tenths(value: f64) -> f64 {
    (value * 10.0).round() / 10.0
}

pub fn megabytes(bytes: u64) -> f64 {
    tenths(bytes as f64 / 1e6)
}

pub fn track(event_type: impl Into<String>, properties: Value) {
    let map = match properties {
        Value::Object(map) => map.into_iter().collect(),
        _ => HashMap::new(),
    };
    track_event(event_type, map);
}

/// Salami: Analytics sind ausgebaut – es werden keine Nutzungsdaten verschickt (früher an
/// NoRisk). Die Aufrufstellen bleiben, das Ereignis landet nur im Debug-Log.
pub fn track_event(event_type: impl Into<String>, _properties: HashMap<String, Value>) {
    debug!("[Analytics] disabled, not sent: {}", event_type.into());
}

#[derive(Debug, Serialize)]
pub struct SystemOsInfo {
    pub os: String,
    pub os_version: String,
    pub arch: String,
}

#[command]
pub fn get_system_os_info() -> SystemOsInfo {
    let os = match std::env::consts::OS {
        "macos" => "macos".to_string(),
        "windows" => "windows".to_string(),
        "linux" => "linux".to_string(),
        other => other.to_string(),
    };
    let os_version = sysinfo::System::os_version().unwrap_or_else(|| "unknown".to_string());
    let arch = std::env::consts::ARCH.to_string();
    SystemOsInfo { os, os_version, arch }
}

#[command]
pub async fn track_analytics_event(event: AnalyticsEvent) -> Result<TrackEventResponse, String> {
    debug!(
        "[Analytics] Frontend event: type={} session={} user={}",
        event.event_type, event.session_id, event.user_id
    );
    Ok(TrackEventResponse {
        success: false,
        message: Some("analytics disabled".to_string()),
    })
}
