//! Salami: Online-Zähler – wie viele Salami-Launcher gerade offen sind.
//!
//! Solange der Launcher läuft, meldet er sich alle 30 s beim Zähl-Server
//! (`branding::ONLINE_COUNTER_URL`, Server-Code in `server/online-counter/`) und
//! bekommt die aktuelle Zahl zurück. Gesendet wird nur eine zufällige ID, die bei
//! jedem Start neu entsteht – kein Account, kein Name. Beim Beenden meldet sich der
//! Launcher ab; klappt das nicht, läuft er auf dem Server nach 90 s ab.

use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::time::Duration;

use log::{debug, info};
use once_cell::sync::Lazy;
use serde::Deserialize;
use tauri::{AppHandle, Emitter};

use crate::config::HTTP_CLIENT;

/// Event an die Oberfläche, Payload `number | null` (null = Zahl unbekannt).
pub const ONLINE_COUNT_EVENT: &str = "salami-online-count";

const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);
const REQUEST_TIMEOUT: Duration = Duration::from_secs(10);
/// So lange darf das Abmelden das Schließen höchstens aufhalten.
const LEAVE_TIMEOUT: Duration = Duration::from_millis(800);
/// Erst nach so vielen Fehlschlägen in Folge verschwindet die Anzeige (kein Flackern bei Aussetzern).
const FAILURES_BEFORE_UNKNOWN: u32 = 2;

static SESSION_ID: Lazy<String> = Lazy::new(|| uuid::Uuid::new_v4().to_string());
/// Letzte bekannte Zahl, -1 = unbekannt.
static ONLINE: AtomicI64 = AtomicI64::new(-1);
static STARTED: AtomicBool = AtomicBool::new(false);

#[derive(Deserialize)]
struct OnlineResponse {
    online: u32,
}

/// Server-Adresse. In Debug-Builds überschreibbar per `SALAMI_ONLINE_URL` (zum Testen gegen einen lokalen Zähler).
fn base_url() -> Option<String> {
    #[cfg(debug_assertions)]
    if let Ok(url) = std::env::var("SALAMI_ONLINE_URL") {
        if !url.is_empty() {
            return Some(url);
        }
    }
    crate::branding::ONLINE_COUNTER_URL.map(str::to_string)
}

async fn post(base: &str, action: &str, timeout: Duration) -> Option<u32> {
    let url = format!("{}/v1/{}", base.trim_end_matches('/'), action);
    let response = HTTP_CLIENT
        .post(url)
        .timeout(timeout)
        .json(&serde_json::json!({ "id": SESSION_ID.as_str() }))
        .send()
        .await
        .ok()?;
    if !response.status().is_success() {
        return None;
    }
    response.json::<OnlineResponse>().await.ok().map(|r| r.online)
}

/// Aktuelle Zahl oder `None`, solange sie unbekannt ist (Zähler aus oder Server nicht erreichbar).
pub fn current() -> Option<u32> {
    u32::try_from(ONLINE.load(Ordering::Relaxed)).ok()
}

/// Startet die Lebenszeichen im Hintergrund. Ohne konfigurierten Server passiert nichts.
pub fn start(app: AppHandle) {
    let Some(base) = base_url() else {
        debug!("[Online] No ONLINE_COUNTER_URL configured, online counter disabled.");
        return;
    };
    if STARTED.swap(true, Ordering::SeqCst) {
        return;
    }
    info!("[Online] Reporting launcher presence to {}", base);

    tauri::async_runtime::spawn(async move {
        let mut ticker = tokio::time::interval(HEARTBEAT_INTERVAL);
        ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        let mut failures = 0u32;
        loop {
            ticker.tick().await;
            let before = current();
            match post(&base, "heartbeat", REQUEST_TIMEOUT).await {
                Some(online) => {
                    failures = 0;
                    ONLINE.store(i64::from(online), Ordering::Relaxed);
                }
                None => {
                    failures += 1;
                    if failures == FAILURES_BEFORE_UNKNOWN {
                        debug!("[Online] Counter server unreachable, hiding online count.");
                        ONLINE.store(-1, Ordering::Relaxed);
                    }
                }
            }
            let after = current();
            if after != before {
                let _ = app.emit(ONLINE_COUNT_EVENT, after);
            }
        }
    });
}

/// Meldet den Launcher beim Beenden ab (höchstens [`LEAVE_TIMEOUT`], Fehler egal).
/// Läuft in einem eigenen Thread, damit es aus jedem Kontext aufgerufen werden kann.
pub fn leave_blocking() {
    if !STARTED.load(Ordering::SeqCst) {
        return;
    }
    let Some(base) = base_url() else { return };
    let worker = std::thread::spawn(move || {
        tauri::async_runtime::block_on(post(&base, "leave", LEAVE_TIMEOUT));
    });
    let _ = worker.join();
}

/// Für die Oberfläche: aktuelle Zahl beim ersten Anzeigen (Updates kommen per [`ONLINE_COUNT_EVENT`]).
#[tauri::command]
pub fn get_online_count() -> Option<u32> {
    current()
}
