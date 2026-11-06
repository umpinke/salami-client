//! Umzug der Ordner aus der NoRisk-Zeit (`noriskclient/`, `NoRiskClientLauncher/`) auf „salami“.

use salami_client_lib::state::profile_state::ProfileManager;
use salami_client_lib::utils::legacy_cleanup::{migrate_profile_image_dirs, migrate_profile_namespace};
use std::fs;
use std::path::PathBuf;

fn temp_profiles(tag: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("salami-legacy-folders-{}-{}", tag, std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    dir
}

#[test]
fn shared_group_folders_move_to_salamiclient() {
    let profiles = temp_profiles("namespace");
    fs::create_dir_all(profiles.join("noriskclient/new/saves/Welt")).unwrap();
    fs::write(profiles.join("noriskclient/new/options.txt"), "fov:90").unwrap();
    fs::create_dir_all(profiles.join("noriskclient/legacy")).unwrap();
    fs::create_dir_all(profiles.join("noriskclient/temp")).unwrap();
    // Standard-Profil, das schon unter salamiclient/ liegt, bleibt unberührt
    fs::create_dir_all(profiles.join("salamiclient/1.21.11/mods")).unwrap();

    migrate_profile_namespace(&profiles);

    assert_eq!(fs::read_to_string(profiles.join("salamiclient/new/options.txt")).unwrap(), "fov:90");
    assert!(profiles.join("salamiclient/new/saves/Welt").is_dir());
    assert!(profiles.join("salamiclient/legacy").is_dir());
    assert!(profiles.join("salamiclient/1.21.11/mods").is_dir());
    assert!(!profiles.join("noriskclient").exists(), "empty old folder should be gone");
    let _ = fs::remove_dir_all(&profiles);
}

#[test]
fn nothing_is_overwritten_when_both_folders_exist() {
    let profiles = temp_profiles("both");
    fs::create_dir_all(profiles.join("noriskclient/new")).unwrap();
    fs::write(profiles.join("noriskclient/new/options.txt"), "alt").unwrap();
    fs::create_dir_all(profiles.join("salamiclient/new")).unwrap();
    fs::write(profiles.join("salamiclient/new/options.txt"), "neu").unwrap();
    // nicht leerer temp-Ordner bleibt auch liegen
    fs::create_dir_all(profiles.join("noriskclient/temp/1234")).unwrap();

    migrate_profile_namespace(&profiles);

    assert_eq!(fs::read_to_string(profiles.join("noriskclient/new/options.txt")).unwrap(), "alt");
    assert_eq!(fs::read_to_string(profiles.join("salamiclient/new/options.txt")).unwrap(), "neu");
    assert!(profiles.join("noriskclient/temp/1234").is_dir());
    let _ = fs::remove_dir_all(&profiles);
}

#[test]
fn profile_image_folders_are_renamed() {
    let profiles = temp_profiles("images");
    fs::create_dir_all(profiles.join("PVP/NoRiskClientLauncher")).unwrap();
    fs::write(profiles.join("PVP/NoRiskClientLauncher/icon.png"), "png").unwrap();
    // eine Ebene tiefer (Gruppen- und Standard-Profilordner)
    fs::create_dir_all(profiles.join("groups/freunde/NoRiskClientLauncher")).unwrap();
    fs::write(profiles.join("groups/freunde/NoRiskClientLauncher/banner.png"), "png").unwrap();
    // beide Ordner da: fehlendes Bild wandert, vorhandenes wird nicht überschrieben
    fs::create_dir_all(profiles.join("W/NoRiskClientLauncher")).unwrap();
    fs::create_dir_all(profiles.join("W/SalamiClient")).unwrap();
    fs::write(profiles.join("W/NoRiskClientLauncher/icon.png"), "alt").unwrap();
    fs::write(profiles.join("W/NoRiskClientLauncher/banner.png"), "banner").unwrap();
    fs::write(profiles.join("W/SalamiClient/icon.png"), "neu").unwrap();
    // der Ordner der NoRisk-Mod im Spielordner ist kein Profilbild-Ordner und bleibt
    fs::create_dir_all(profiles.join("PVP/NoRiskClient/waypoints")).unwrap();

    migrate_profile_image_dirs(&profiles);

    assert!(profiles.join("PVP/SalamiClient/icon.png").is_file());
    assert!(!profiles.join("PVP/NoRiskClientLauncher").exists());
    assert!(profiles.join("groups/freunde/SalamiClient/banner.png").is_file());
    assert_eq!(fs::read_to_string(profiles.join("W/SalamiClient/icon.png")).unwrap(), "neu");
    assert_eq!(fs::read_to_string(profiles.join("W/SalamiClient/banner.png")).unwrap(), "banner");
    assert_eq!(fs::read_to_string(profiles.join("W/NoRiskClientLauncher/icon.png")).unwrap(), "alt");
    assert!(profiles.join("PVP/NoRiskClient/waypoints").is_dir());
    let _ = fs::remove_dir_all(&profiles);
}

#[test]
fn stored_noriskclient_paths_follow_the_move() {
    let profiles = temp_profiles("resolve");
    // noch nicht verschoben: alter Ordner gilt weiter
    fs::create_dir_all(profiles.join("noriskclient/new")).unwrap();
    assert_eq!(
        ProfileManager::resolve_profile_path(&profiles, "noriskclient/new"),
        profiles.join("noriskclient").join("new")
    );
    // verschoben: der gespeicherte alte Pfad zeigt auf den neuen Ordner
    migrate_profile_namespace(&profiles);
    assert_eq!(
        ProfileManager::resolve_profile_path(&profiles, "noriskclient/new"),
        profiles.join("salamiclient").join("new")
    );
    // andere Pfade bleiben, wie sie sind
    assert_eq!(ProfileManager::resolve_profile_path(&profiles, "PVP"), profiles.join("PVP"));
    assert_eq!(
        ProfileManager::resolve_profile_path(&profiles, "salamiclient/1.21.11"),
        profiles.join("salamiclient").join("1.21.11")
    );
    let _ = fs::remove_dir_all(&profiles);
}
