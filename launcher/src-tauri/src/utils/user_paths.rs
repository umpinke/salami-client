//! Pfade, die der Nutzer selbst gewählt hat: im nativen Datei-Dialog (Tauri trägt jede Auswahl in
//! den Bereich des Asset-Protokolls ein, das melden wir hier mit) oder per Drag & Drop auf ein
//! Launcher-Fenster. Befehle, die einen Pfad aus der Oberfläche bekommen und damit außerhalb der
//! Launcher-Ordner lesen, schreiben oder etwas starten würden, nehmen ihn ohne Rückfrage nur, wenn
//! er so gewählt wurde – sonst fragt ein nativer Dialog, den ein eingeschleustes Skript in der
//! Oberfläche nicht bedienen kann (wie `launch_guard`).

use crate::error::{AppError, Result};
use crate::utils::launch_guard;
use crate::utils::path_guard;
use once_cell::sync::Lazy;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager, Runtime};

/// So lange gilt eine Auswahl.
const KEEP: Duration = Duration::from_secs(30 * 60);
const MAX_ENTRIES: usize = 512;

struct Chosen {
    key: String,
    at: Instant,
}

static CHOSEN: Lazy<Mutex<Vec<Chosen>>> = Lazy::new(|| Mutex::new(Vec::new()));

/// Vergleichbare Form: kanonisch (falls vorhanden), ohne `\\?\`, `/` als Trenner, ohne `/` am
/// Ende, unter Windows klein geschrieben.
fn comparable_key(path: &Path) -> String {
    // Netzwerkpfade nicht auflösen – das würde den fremden Rechner kontaktieren
    let resolved = if path_guard::is_network_path(path) {
        path.to_path_buf()
    } else {
        std::fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf())
    };
    let text = resolved.to_string_lossy();
    let text = text.strip_prefix(r"\\?\").unwrap_or(&text).replace('\\', "/");
    let text = text.trim_end_matches('/').to_string();
    if cfg!(windows) {
        text.to_lowercase()
    } else {
        text
    }
}

fn is_within(inner: &str, outer: &str) -> bool {
    inner == outer
        || (!outer.is_empty()
            && inner.len() > outer.len()
            && inner.starts_with(outer)
            && inner.as_bytes()[outer.len()] == b'/')
}

/// Merkt sich einen gewählten Pfad (Datei oder Ordner).
pub fn remember(path: &Path) {
    let key = comparable_key(path);
    if key.is_empty() {
        return;
    }
    let mut chosen = CHOSEN.lock().unwrap_or_else(|e| e.into_inner());
    let now = Instant::now();
    chosen.retain(|c| now.duration_since(c.at) < KEEP && c.key != key);
    chosen.push(Chosen { key, at: now });
    if chosen.len() > MAX_ENTRIES {
        let drop = chosen.len() - MAX_ENTRIES;
        chosen.drain(..drop);
    }
}

/// Wurde `path` (oder ein Ordner, in dem er liegt) gerade vom Nutzer gewählt? Ein gewähltes
/// Laufwerk (`C:\`) zählt nur für sich selbst, nicht für alles darauf.
pub fn was_chosen(path: &Path) -> bool {
    let key = comparable_key(path);
    let chosen = CHOSEN.lock().unwrap_or_else(|e| e.into_inner());
    let now = Instant::now();
    chosen.iter().any(|c| {
        now.duration_since(c.at) < KEEP
            && (c.key == key || (!is_root_key(&c.key) && is_within(&key, &c.key)))
    })
}

fn is_root_key(key: &str) -> bool {
    let trimmed = key.trim_end_matches('/');
    trimmed.is_empty() || (trimmed.len() == 2 && trimmed.ends_with(':'))
}

/// Meldet Auswahlen aus dem Datei-Dialog (Asset-Bereich) mit. Eigene Freigaben des Launchers
/// (`asset_scope`) zählen nicht als Auswahl des Nutzers.
pub fn install<R: Runtime>(app: &AppHandle<R>) {
    app.asset_protocol_scope().listen(|event| {
        if let tauri::scope::fs::Event::PathAllowed(path) = event {
            // eigene Freigaben des Launchers (asset_scope) sind keine Auswahl des Nutzers
            if !crate::utils::asset_scope::is_internal_grant() {
                remember(path);
            }
        }
    });
}

/// Pfade aus einem Drag & Drop auf ein Launcher-Fenster.
pub fn remember_dropped(paths: &[PathBuf]) {
    for path in paths {
        remember(path);
    }
}

/// Nimmt einen Pfad aus der Oberfläche nur, wenn der Nutzer ihn gewählt hat oder ihn jetzt im
/// nativen Dialog erlaubt. Netzwerkpfade werden immer abgelehnt. `what` beschreibt, was der
/// Launcher damit tut (zweisprachig, eine Zeile).
pub async fn require_user_choice<R: Runtime>(app: &AppHandle<R>, path: &Path, what: &str) -> Result<()> {
    path_guard::reject_network_path(path)?;
    if was_chosen(path) {
        return Ok(());
    }
    let message = [
        what.to_string(),
        String::new(),
        path.display().to_string(),
        String::new(),
        "Nur erlauben, wenn du diesen Pfad gerade selbst ausgewählt hast.".to_string(),
        "Only allow this if you just picked this path yourself.".to_string(),
    ]
    .join("\n");
    if launch_guard::confirm_dialog(app, message).await {
        remember(path);
        Ok(())
    } else {
        Err(AppError::Other(
            "Abgebrochen: der Pfad wurde nicht bestätigt / cancelled: the path was not confirmed".to_string(),
        ))
    }
}

/// Wie [`require_user_choice`] für Dateien, die der Launcher nur liest und kopiert (Bilder für
/// Icons, Skins): auch ohne Rückfrage, wenn die Oberfläche sie ohnehin anzeigen darf (Launcher-
/// Ordner, gemerkte eigene Bilder – siehe `asset_scope`/`user_media`).
pub async fn require_readable_choice<R: Runtime>(app: &AppHandle<R>, path: &Path, what: &str) -> Result<()> {
    path_guard::reject_network_path(path)?;
    if !path_guard::is_protected(path) && app.asset_protocol_scope().is_allowed(path) {
        return Ok(());
    }
    require_user_choice(app, path, what).await
}

#[cfg(test)]
fn forget_all() {
    CHOSEN.lock().unwrap().clear();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn chosen_paths_and_their_contents() {
        forget_all();
        let base = std::env::temp_dir().join(format!("salami-user-paths-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        std::fs::create_dir_all(base.join("picked").join("inner")).unwrap();
        std::fs::create_dir_all(base.join("other")).unwrap();

        assert!(!was_chosen(&base.join("picked")));
        remember(&base.join("picked"));
        assert!(was_chosen(&base.join("picked")));
        assert!(was_chosen(&base.join("picked").join("inner")));
        assert!(was_chosen(&base.join("picked").join("inner").join("..")));
        assert!(!was_chosen(&base.join("picked").join("..").join("other")));
        assert!(!was_chosen(&base.join("other")));
        assert!(!was_chosen(&base));
        // ähnlicher Name ist kein Unterordner
        assert!(!was_chosen(&base.join("picked2")));

        #[cfg(windows)]
        {
            remember(Path::new("C:\\"));
            assert!(!was_chosen(Path::new("C:\\Windows")));
        }
        forget_all();
        let _ = std::fs::remove_dir_all(&base);
    }
}
