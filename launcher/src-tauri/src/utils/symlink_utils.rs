use crate::error::{AppError, Result};
use log::{info, error};
use std::path::Path;
use serde::{Serialize, Deserialize};
use tokio::fs;

#[cfg(unix)]
use std::os::unix::fs::symlink as symlink_dir;

#[cfg(windows)]
use std::fs::hard_link;

/// Creates a symlink/junction/hardlink at `link_path` pointing to `target_path`
/// On Windows:
/// - Directories: Junction Points (no admin rights needed)
/// - Files: Hard Links (no admin rights needed) - tracked in metadata
/// On Unix/Linux/macOS: Regular symlinks
/// 
/// For hard links on Windows, we also store metadata to track them
pub async fn create_symlink(target_path: &Path, link_path: &Path, is_dir: bool) -> Result<()> {
    #[cfg(unix)]
    {
        tokio::task::spawn_blocking({
            let target = target_path.to_path_buf();
            let link = link_path.to_path_buf();
            move || std::os::unix::fs::symlink(target, link)
        })
        .await
        .map_err(|e| AppError::Other(format!("Join error: {}", e)))?
        .map_err(|e| AppError::Io(e))?;
    }

    #[cfg(windows)]
    {
        if is_dir {
            // Use junction points for directories - no admin rights needed!
            info!("Creating junction point from {:?} to {:?}", link_path, target_path);
            create_junction(target_path, link_path).await?;
        } else {
            // For files, use hard links - no admin rights needed!
            info!("Creating hard link from {:?} to {:?}", link_path, target_path);
            create_hardlink(target_path, link_path).await?;
        }
    }

    Ok(())
}

#[cfg(windows)]
async fn create_junction(target: &Path, junction: &Path) -> Result<()> {
    // Salami: Junction direkt über die Windows-API anlegen statt `cmd /C mklink /J` – über cmd.exe
    // hätten Sonderzeichen in Profil- oder Pack-Namen (`&`, `|`, `^` …) Befehle ausführen können.
    let target_abs = tokio::fs::canonicalize(target)
        .await
        .map_err(|e| AppError::Other(format!("Failed to resolve target path: {}", e)))?;
    let target_abs = strip_verbatim_prefix(&target_abs);
    let junction_path = junction.to_path_buf();

    info!("Creating junction point: {:?} -> {:?}", junction_path, target_abs);

    tokio::task::spawn_blocking({
        let target_abs = target_abs.clone();
        let junction_path = junction_path.clone();
        move || junction::create(&target_abs, &junction_path)
    })
    .await
    .map_err(|e| AppError::Other(format!("Join error: {}", e)))?
    .map_err(|e| {
        error!(
            "Creating junction failed. Junction: {:?}, Target: {:?}: {}",
            junction_path, target_abs, e
        );
        AppError::Other(format!("Failed to create junction point: {}", e))
    })?;

    info!("Successfully created junction point: {:?} -> {:?}", junction_path, target_abs);
    Ok(())
}

/// `\\?\C:\…` (von `canonicalize`) → `C:\…`; UNC-Pfade bleiben unverändert.
#[cfg(windows)]
fn strip_verbatim_prefix(path: &Path) -> std::path::PathBuf {
    let text = path.to_string_lossy();
    match text.strip_prefix(r"\\?\") {
        Some(rest) if !rest.starts_with("UNC\\") => std::path::PathBuf::from(rest),
        _ => path.to_path_buf(),
    }
}

#[cfg(windows)]
async fn create_hardlink(target: &Path, link: &Path) -> Result<()> {
    // Convert to absolute path
    let target_abs = tokio::fs::canonicalize(target).await
        .map_err(|e| AppError::Other(format!("Failed to resolve target file path: {}", e)))?;
    
    // Create hard link - no admin rights needed!
    tokio::task::spawn_blocking({
        let link = link.to_path_buf();
        let target_abs = target_abs.clone();
        move || hard_link(&target_abs, &link)
    })
    .await
    .map_err(|e| AppError::Other(format!("Join error: {}", e)))?
    .map_err(|e| AppError::Other(format!("Failed to create hard link: {}", e)))?;

    info!("Successfully created hard link: {:?} -> {:?}", link, target_abs);
    Ok(())
}

/// Checks if a path is a symlink
pub async fn is_symlink(path: &Path) -> Result<bool> {
    let metadata = fs::symlink_metadata(path).await?;
    Ok(metadata.file_type().is_symlink())
}

/// Removes a symlink without affecting the target
pub async fn remove_symlink(path: &Path) -> Result<()> {
    if !is_symlink(path).await? {
        return Err(AppError::Other(format!(
            "Path is not a symlink: {:?}",
            path
        )));
    }

    // Try removing as file first, then as directory if that fails
    match fs::remove_file(path).await {
        Ok(()) => Ok(()),
        Err(_) => fs::remove_dir(path).await.map_err(|e| AppError::Io(e)),
    }
}

/// Information about a symlink/junction/hardlink
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SymlinkInfo {
    /// The path of the link itself (relative to profile instance)
    pub link_path: String,
    /// The target path the link points to (absolute)
    pub target_path: String,
    /// Type of link (junction, symlink, hardlink)
    pub link_type: String,
    /// Whether this is a directory or file
    pub is_directory: bool,
}

/// Recursively finds all junctions and symlinks in a directory (iterative version)
pub async fn find_all_links(directory: &Path) -> Result<Vec<SymlinkInfo>> {
    let mut links = Vec::new();
    
    if !directory.exists() {
        return Ok(links);
    }
    
    // Use an iterative approach with a stack to avoid recursion
    let mut dirs_to_process = vec![directory.to_path_buf()];
    
    while let Some(current_dir) = dirs_to_process.pop() {
        let mut entries = match tokio::fs::read_dir(&current_dir).await {
            Ok(e) => e,
            Err(_) => continue, // Skip directories we can't read
        };
        
        while let Ok(Some(entry)) = entries.next_entry().await {
            let path = entry.path();
            let metadata = match tokio::fs::symlink_metadata(&path).await {
                Ok(m) => m,
                Err(_) => continue,
            };
            
            let is_dir = metadata.is_dir();
            
            // Check if this is a symlink/junction
            let is_link = is_symlink(&path).await.unwrap_or(false);
            
            // Skip hardlink detection for now (too complex/unstable to detect reliably)
            let _is_hardlink = false;
            
            if is_link {
                // Read the link target
                let target = match tokio::fs::read_link(&path).await {
                    Ok(t) => t.to_string_lossy().to_string(),
                    Err(_) => {
                        // On Windows, read_link might fail for junctions
                        // Try to get the actual target using canonicalize
                        match tokio::fs::canonicalize(&path).await {
                            Ok(t) => t.to_string_lossy().to_string(),
                            Err(_) => continue,
                        }
                    }
                };
                
                let relative_path = path.strip_prefix(directory)
                    .unwrap_or(&path)
                    .to_string_lossy()
                    .to_string();
                
                #[cfg(unix)]
                let link_type = "symlink";
                
                #[cfg(windows)]
                let link_type = if is_dir {
                    "junction"
                } else {
                    // For files on Windows, we use hard links but can't detect them reliably
                    "hardlink"
                };
                
                links.push(SymlinkInfo {
                    link_path: relative_path,
                    target_path: target,
                    link_type: link_type.to_string(),
                    is_directory: is_dir,
                });
                
                // Don't recurse into symlinked directories to avoid loops
                if is_link && is_dir {
                    continue;
                }
            }
            
            // Add regular directories to the stack for processing
            if path.is_dir() && !is_link {
                dirs_to_process.push(path);
            }
        }
    }
    
    Ok(links)
}

#[cfg(all(test, windows))]
mod junction_tests {
    use super::*;

    #[tokio::test]
    async fn creates_junction_without_a_shell() {
        let base = std::env::temp_dir().join(format!("salami-junction-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&base);
        let target = base.join("target & echo pwned");
        std::fs::create_dir_all(&target).unwrap();
        std::fs::write(target.join("file.txt"), "ok").unwrap();
        let link = base.join("link ^ & (x)");

        create_symlink(&target, &link, true).await.unwrap();
        assert_eq!(std::fs::read_to_string(link.join("file.txt")).unwrap(), "ok");

        std::fs::remove_dir(&link).unwrap();
        let _ = std::fs::remove_dir_all(&base);
    }
}
