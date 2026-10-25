use std::fs;
use std::path::Path;

/// Liest die Namen aller Befehle aus `tauri::generate_handler![...]` in `src/main.rs`.
/// So bleibt die Rechte-Liste automatisch vollständig, wenn ein Befehl dazukommt.
fn app_commands() -> Vec<String> {
    let main = fs::read_to_string("src/main.rs").expect("src/main.rs lesen");
    let start = main.find("generate_handler![").expect("generate_handler! in main.rs");
    let body_start = start + "generate_handler![".len();
    let mut depth = 1;
    let mut end = body_start;
    for (i, ch) in main[body_start..].char_indices() {
        match ch {
            '[' => depth += 1,
            ']' => {
                depth -= 1;
                if depth == 0 {
                    end = body_start + i;
                    break;
                }
            }
            _ => {}
        }
    }
    main[body_start..end]
        .lines()
        .map(|line| line.split("//").next().unwrap_or(""))
        .flat_map(|line| line.split(','))
        .map(|item| item.trim())
        .filter(|item| !item.is_empty())
        .map(|item| item.rsplit("::").next().unwrap_or(item).to_string())
        .collect()
}

/// Rechte-Set `app-commands` (alle Befehle) für die lokalen Launcher-Fenster.
fn write_permission_set(commands: &[String]) {
    let list = commands
        .iter()
        .map(|c| format!("  \"allow-{}\",", c.replace('_', "-")))
        .collect::<Vec<_>>()
        .join("\n");
    let toml = format!(
        "# Automatisch erzeugt von build.rs – nicht von Hand ändern.\n\
         [[set]]\n\
         identifier = \"app-commands\"\n\
         description = \"Alle Befehle des Launchers – nur für die eigenen, lokalen Fenster.\"\n\
         permissions = [\n{list}\n]\n"
    );
    let path = Path::new("permissions").join("app-commands.toml");
    fs::create_dir_all("permissions").expect("permissions/ anlegen");
    if fs::read_to_string(&path).ok().as_deref() != Some(toml.as_str()) {
        fs::write(&path, toml).expect("permissions/app-commands.toml schreiben");
    }
}

/// CurseForge-API-Key für `option_env!("SALAMI_CURSEFORGE_API_KEY")` in integrations/curseforge.rs.
/// Vorrang hat die Umgebungsvariable, sonst `%USERPROFILE%\.salami-client\curseforge.key`
/// (wie updater.key nicht im Repo – das Repo ist öffentlich).
fn curseforge_api_key() {
    println!("cargo:rerun-if-env-changed=SALAMI_CURSEFORGE_API_KEY");
    if std::env::var_os("SALAMI_CURSEFORGE_API_KEY").is_some() {
        return;
    }
    let Some(home) = std::env::var_os("USERPROFILE").or_else(|| std::env::var_os("HOME")) else {
        return;
    };
    let dir = Path::new(&home).join(".salami-client");
    // Ordner statt Datei beobachten: eine fehlende Datei ließe Cargo bei jedem Build neu kompilieren
    if dir.is_dir() {
        println!("cargo:rerun-if-changed={}", dir.display());
    }
    let key = fs::read_to_string(dir.join("curseforge.key")).unwrap_or_default();
    let key = key.trim();
    if key.is_empty() {
        println!(
            "cargo:warning=Kein CurseForge-API-Key ({} fehlt) – CurseForge ist in diesem Build aus.",
            dir.join("curseforge.key").display()
        );
    } else {
        println!("cargo:rustc-env=SALAMI_CURSEFORGE_API_KEY={key}");
    }
}

fn main() {
    println!("cargo:rerun-if-env-changed=NRC_CAPTURE_RUNTIME_SHA256");
    println!("cargo:rerun-if-env-changed=NRC_CAPTURE_RUNTIME_URL");
    println!("cargo:rerun-if-changed=src/main.rs");
    curseforge_api_key();

    // Ohne App-Manifest prüft Tauri Launcher-Befehle gar nicht: jede Seite in einem Fenster
    // (auch eine fremde nach einer Weiterleitung) könnte sie aufrufen. Mit Manifest gelten
    // nur die Rechte aus capabilities/ – und die geben Befehle nur lokalen Fenstern.
    let commands = app_commands();
    write_permission_set(&commands);
    let commands: &'static [&'static str] = Box::leak(
        commands
            .into_iter()
            .map(|c| &*Box::leak(c.into_boxed_str()))
            .collect::<Vec<&'static str>>()
            .into_boxed_slice(),
    );
    tauri_build::try_build(
        tauri_build::Attributes::new()
            .app_manifest(tauri_build::AppManifest::new().commands(commands)),
    )
    .expect("tauri build");
}
