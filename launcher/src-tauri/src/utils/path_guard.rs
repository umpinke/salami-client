//! Pfadprüfung für Datei-Befehle, die die Oberfläche aufruft: Sie dürfen nur innerhalb der
//! Launcher-Ordner arbeiten (Datenordner, Meta-Ordner, eigener Spielordner) und nie auf die
//! Account-Datei zugreifen. Schützt davor, dass ein eingeschleustes Skript über Launcher-
//! Befehle beliebige Dateien liest oder löscht.

use crate::config::{standard_meta_dir, ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::error::{AppError, Result};
use crate::utils::import_safety::safe_file_component;
use std::path::{Component, Path, PathBuf};

/// Netzwerk- oder Gerätepfad unter Windows (`\\server\share`, `\\?\UNC\…`, `\\.\…`, WebDAV
/// `\\host@SSL\…`). Über solche Pfade würde der Launcher fremde Rechner ansprechen (und dabei
/// z. B. die Windows-Anmeldung preisgeben) oder Programme von dort starten.
pub fn is_network_path(path: &Path) -> bool {
    #[cfg(windows)]
    {
        let text = path.to_string_lossy().replace('/', "\\");
        if let Some(rest) = text.strip_prefix(r"\\?\") {
            let bytes = rest.as_bytes();
            return !(bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':');
        }
        text.starts_with(r"\\")
    }
    #[cfg(not(windows))]
    {
        let _ = path;
        false
    }
}

/// Fehler für Netzwerk-/Gerätepfade ([`is_network_path`]), auch wenn erst der aufgelöste Pfad
/// (Verknüpfung auf eine Freigabe) dorthin zeigt.
pub fn reject_network_path(path: &Path) -> Result<()> {
    let reject = || {
        AppError::Other(format!(
            "Netzwerkpfade sind hier nicht erlaubt / network paths are not allowed: {}",
            path.display()
        ))
    };
    // Erst den Text prüfen: schon das Auflösen eines UNC-Pfads würde den fremden Rechner kontaktieren
    if is_network_path(path) {
        return Err(reject());
    }
    if std::fs::canonicalize(path).ok().as_deref().is_some_and(is_network_path) {
        return Err(reject());
    }
    Ok(())
}

/// Der letzte Teil eines Pfads als schlichter Dateiname – kein `..`, `.`, Laufwerk, kein leerer
/// Name (z. B. `C:\x\mods\..` hätte sonst den Ordner `mods` selbst gemeint).
pub fn plain_file_name(path: &Path) -> Result<String> {
    match path.components().next_back() {
        Some(Component::Normal(name)) => {
            let name = name
                .to_str()
                .ok_or_else(|| AppError::Other(format!("Invalid file name: {}", path.display())))?;
            safe_file_component(name)
        }
        _ => Err(AppError::Other(format!(
            "Not a plain file name: {}",
            path.display()
        ))),
    }
}

/// `dir` plus ein Name aus der Oberfläche. Erlaubt nur einen schlichten Dateinamen
/// ([`safe_file_component`]); existiert der Eintrag und ist kein Link, muss er kanonisch direkt
/// in `dir` liegen.
pub fn child_path(dir: &Path, name: &str) -> Result<PathBuf> {
    let name = safe_file_component(name)?;
    let path = dir.join(&name);
    if let Ok(meta) = std::fs::symlink_metadata(&path) {
        if !meta.file_type().is_symlink() {
            let parent = std::fs::canonicalize(dir).map_err(AppError::Io)?;
            let canonical = std::fs::canonicalize(&path).map_err(AppError::Io)?;
            if canonical.parent() != Some(parent.as_path()) {
                return Err(AppError::Other(format!(
                    "'{}' is not inside {}",
                    name,
                    dir.display()
                )));
            }
        }
    }
    Ok(path)
}

/// Liegt `path` (kanonisch) direkt in einem der Ordner `dirs`? → kanonischer Pfad. Für Inhalte
/// eines Profils (Mods, Pakete), deren Pfad die Oberfläche mitschickt.
pub fn ensure_direct_child_of_any(path: &Path, dirs: &[PathBuf]) -> Result<PathBuf> {
    plain_file_name(path)?;
    reject_network_path(path)?;
    let canonical = std::fs::canonicalize(path)
        .map_err(|_| AppError::Other(format!("Path not found: {}", path.display())))?;
    let parent = canonical
        .parent()
        .ok_or_else(|| AppError::Other(format!("No parent folder: {}", path.display())))?;
    let inside = dirs
        .iter()
        .filter_map(|dir| std::fs::canonicalize(dir).ok())
        .any(|dir| dir == parent);
    if !inside {
        return Err(AppError::Other(format!(
            "'{}' is not part of this profile's content",
            path.display()
        )));
    }
    Ok(canonical)
}

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
    // Salami: kein Ausweg über Netzwerkpfade (nur wenn der Launcher-Ordner nicht selbst dort liegt);
    // vor dem Auflösen prüfen, das würde den fremden Rechner schon kontaktieren
    let network_roots = roots.iter().any(|root| is_network_path(root));
    if is_network_path(path) && !network_roots {
        return Err(AppError::Other(format!(
            "Access to network paths is not allowed: {}",
            path.display()
        )));
    }
    let canonical = std::fs::canonicalize(path)
        .map_err(|_| AppError::Other(format!("Path not found: {}", path.display())))?;
    if is_protected(&canonical) {
        return Err(AppError::Other("Access to this file is not allowed".to_string()));
    }
    if is_network_path(&canonical) && !network_roots {
        return Err(AppError::Other(format!(
            "Access to network paths is not allowed: {}",
            path.display()
        )));
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

    #[test]
    fn plain_names_only() {
        assert_eq!(plain_file_name(Path::new("a/b/mod.jar")).unwrap(), "mod.jar");
        // `a/.` ist für Rust dasselbe wie `a` (Name `a` in seinem Elternordner) – kein Ausweg
        assert_eq!(plain_file_name(Path::new("a/.")).unwrap(), "a");
        for bad in ["a/b/..", "..", "", "a/b/con", "a/b/x.jar."] {
            assert!(plain_file_name(Path::new(bad)).is_err(), "{bad:?}");
        }
        #[cfg(windows)]
        {
            assert!(plain_file_name(Path::new("C:\\x\\mods\\..")).is_err());
            assert!(plain_file_name(Path::new("D:")).is_err());
            assert!(plain_file_name(Path::new("C:\\")).is_err());
        }
    }

    #[test]
    fn child_path_rejects_escapes() {
        let base = std::env::temp_dir().join(format!("salami-child-path-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        let dir = base.join("saves");
        std::fs::create_dir_all(dir.join("World 1")).unwrap();
        assert_eq!(child_path(&dir, "World 1").unwrap(), dir.join("World 1"));
        assert_eq!(child_path(&dir, "missing").unwrap(), dir.join("missing"));
        for bad in ["..", ".", "", "a/b", "a\\b", "D:", "C:\\x", "x:y", "nul", "x."] {
            assert!(child_path(&dir, bad).is_err(), "{bad:?}");
        }
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn direct_children_of_content_dirs() {
        let base = std::env::temp_dir().join(format!("salami-content-dirs-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        let mods = base.join("mods");
        std::fs::create_dir_all(mods.join("nested")).unwrap();
        std::fs::write(mods.join("a.jar"), "x").unwrap();
        std::fs::write(mods.join("nested").join("b.jar"), "x").unwrap();
        std::fs::write(base.join("outside.jar"), "x").unwrap();
        let dirs = vec![mods.clone()];
        assert!(ensure_direct_child_of_any(&mods.join("a.jar"), &dirs).is_ok());
        assert!(ensure_direct_child_of_any(&mods.join("nested").join("b.jar"), &dirs).is_err());
        assert!(ensure_direct_child_of_any(&base.join("outside.jar"), &dirs).is_err());
        assert!(ensure_direct_child_of_any(&mods.join("nested").join(".."), &dirs).is_err());
        assert!(ensure_direct_child_of_any(&mods.join("..").join("outside.jar"), &dirs).is_err());
        let _ = std::fs::remove_dir_all(&base);
    }

    #[test]
    fn network_paths() {
        #[cfg(windows)]
        {
            for p in [r"\\server\share\x", r"//server/share", r"\\?\UNC\server\share", r"\\.\PhysicalDrive0", r"\\host@SSL\DavWWWRoot\java.exe"] {
                assert!(is_network_path(Path::new(p)), "{p}");
                assert!(reject_network_path(Path::new(p)).is_err(), "{p}");
            }
            for p in [r"C:\x", r"\\?\C:\x", "relative\\x"] {
                assert!(!is_network_path(Path::new(p)), "{p}");
            }
        }
        assert!(!is_network_path(Path::new("/home/x")));
    }
}
