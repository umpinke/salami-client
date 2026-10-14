//! Pfadprüfung für Datei-Befehle, die die Oberfläche aufruft: Sie dürfen nur innerhalb der
//! Launcher-Ordner arbeiten (Datenordner, Meta-Ordner, eigener Spielordner) und nie auf die
//! Account-Datei zugreifen. Schützt davor, dass ein eingeschleustes Skript über Launcher-
//! Befehle beliebige Dateien liest oder löscht.

use crate::config::{standard_meta_dir, ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use std::path::{Path, PathBuf};

/// Dateien, die nie über Oberflächen-Befehle gelesen oder gelöscht werden dürfen.
const PROTECTED_FILE_NAMES: &[&str] = &["accounts.json"];

fn allowed_roots() -> Vec<PathBuf> {
    let mut roots = vec![
        LAUNCHER_DIRECTORY.root_dir(),
        LAUNCHER_DIRECTORY.meta_dir(),
        standard_meta_dir(),
    ];
    roots.retain(|r| !r.as_os_str().is_empty());
    roots
        .into_iter()
        .map(|r| std::fs::canonicalize(&r).unwrap_or(r))
        .collect()
}

pub fn is_protected(path: &Path) -> bool {
    path.file_name()
        .and_then(|n| n.to_str())
        .map(|n| {
            let lower = n.to_ascii_lowercase();
            PROTECTED_FILE_NAMES
                .iter()
                .any(|p| lower == *p || lower.starts_with(&format!("{p}.")))
        })
        .unwrap_or(false)
}

/// Gibt den kanonischen Pfad zurück, wenn er existiert, innerhalb der Launcher-Ordner liegt
/// und keine geschützte Datei ist – sonst einen Fehler.
pub fn ensure_allowed(path: &Path) -> Result<PathBuf> {
    ensure_allowed_in(path, &allowed_roots())
}

fn ensure_allowed_in(path: &Path, roots: &[PathBuf]) -> Result<PathBuf> {
    let canonical = std::fs::canonicalize(path)
        .map_err(|_| AppError::Other(format!("Path not found: {}", path.display())))?;
    if is_protected(&canonical) {
        return Err(AppError::Other("Access to this file is not allowed".to_string()));
    }
    if roots.iter().any(|root| canonical.starts_with(root)) {
        Ok(canonical)
    } else {
        Err(AppError::Other(format!(
            "Access outside the launcher folders is not allowed: {}",
            path.display()
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_inside_roots_and_never_accounts() {
        let base = std::env::temp_dir().join(format!("salami-path-guard-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        let root = base.join("root");
        std::fs::create_dir_all(root.join("profiles")).unwrap();
        std::fs::write(root.join("profiles").join("shot.png"), "x").unwrap();
        std::fs::write(root.join("accounts.json"), "{}").unwrap();
        std::fs::write(base.join("outside.txt"), "x").unwrap();
        let roots = vec![std::fs::canonicalize(&root).unwrap()];

        assert!(ensure_allowed_in(&root.join("profiles").join("shot.png"), &roots).is_ok());
        assert!(ensure_allowed_in(&root.join("accounts.json"), &roots).is_err());
        assert!(ensure_allowed_in(&base.join("outside.txt"), &roots).is_err());
        assert!(ensure_allowed_in(&root.join("profiles").join("..").join("..").join("outside.txt"), &roots).is_err());
        assert!(ensure_allowed_in(&root.join("missing.png"), &roots).is_err());
        let _ = std::fs::remove_dir_all(&base);
    }
}
