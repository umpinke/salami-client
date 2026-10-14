//! Schutz für Einstellungen, mit denen beim Spielstart fremde Programme oder fremder Code laufen
//! können: Start-Befehle (Hooks), eigenes Java, JVM-Optionen wie `-javaagent`, eigene
//! Spiel-Argumente und ein anderer Spielordner. Die Oberfläche kann sie nicht still setzen – jede
//! neue solche Einstellung muss in einem nativen Dialog bestätigt werden, den ein eingeschleustes
//! Skript in der Oberfläche nicht wegklicken kann.
//!
//! JVM-Optionen: Ohne Nachfrage gehen nur bekannte, harmlose Formen durch (Speicher, `-XX:` mit
//! Schalter oder Zahl, `-D` mit einfachem Wert). Alles andere – auch Optionen, die z. B. die
//! Anmelde-Server von Minecraft umleiten und so das Zugangstoken abgreifen könnten – muss
//! bestätigt werden.

use crate::state::config_state::Hooks;
use crate::state::profile_state::ProfileSettings;
use tauri::{AppHandle, Runtime};
use tauri_plugin_dialog::{DialogExt, MessageDialogButtons, MessageDialogKind};

/// JVM-Optionen (klein geschrieben), die Fremdcode laden oder Programme starten können.
const DANGEROUS_JVM_PREFIXES: &[&str] = &[
    "-javaagent",
    "-agentpath",
    "-agentlib",
    "-xrun",
    "-xx:onerror",
    "-xx:onoutofmemoryerror",
    "-xbootclasspath",
    "-djava.system.class.loader",
    "-djava.library.path",
    "-djava.class.path",
    "-djava.security.manager",
    "-djava.security.policy",
    "-djdk.module.",
    "-dlog4j.configurationfile",
    "-dlog4j2.configurationfile",
    "-dlog4j.configuration",
    "-dfabric.addmods",
    "-dfabric.loadmods",
    "-dfabric.gamejarpath",
    "-dfabric.remapclasspathfile",
    "-dloader.addmods",
    // Optionen aus Dateien nachladen
    "-xx:vmoptionsfile",
    "-xx:flags",
    // Dateien an beliebige Orte schreiben (z. B. Autostart)
    "-xx:errorfile",
    "-xx:heapdumppath",
    "-xx:logfile",
    // Anmelde-/Session-Server umleiten: Authlib schickt das Zugangstoken dorthin
    "-dminecraft.api.",
    "-djavax.net.ssl.",
    "-dhttp.proxy",
    "-dhttps.proxy",
    "-dsocksproxy",
    "-djava.net.usesystemproxies",
    // native Bibliotheken / Klassen aus fremden Ordnern
    "-dorg.lwjgl.librarypath",
    "-dorg.lwjgl.system.",
    "-djna.",
    "-djava.ext.dirs",
    "-djava.endorsed.dirs",
];

/// Kurze Optionen, die nur als ganzes Wort (oder mit `=`) gefährlich sind.
const DANGEROUS_JVM_FLAGS: &[&str] = &[
    "-cp",
    "-classpath",
    "--class-path",
    "-p",
    "--module-path",
    "--upgrade-module-path",
    "--patch-module",
];

/// Liefert die gefährlichen Teile eines JVM-Argument-Strings (so getrennt wie beim Start).
pub fn dangerous_jvm_args(args: Option<&str>) -> Vec<String> {
    let Some(args) = args else { return Vec::new() };
    args.split_whitespace()
        .filter(|token| is_dangerous_jvm_token(token))
        .map(str::to_string)
        .collect()
}

fn is_dangerous_jvm_token(token: &str) -> bool {
    let lower = token
        .trim_matches(|c| c == '"' || c == '\'')
        .to_ascii_lowercase();
    // Argument-Dateien (`@datei`) können beliebige Optionen enthalten.
    if lower.starts_with('@') {
        return true;
    }
    if DANGEROUS_JVM_PREFIXES.iter().any(|p| lower.starts_with(p))
        || DANGEROUS_JVM_FLAGS
            .iter()
            .any(|f| lower == *f || lower.starts_with(&format!("{f}=")))
    {
        return true;
    }
    !is_known_harmless_jvm_token(&lower)
}

/// Erlaubt-Liste: übliche Speicher- und Leistungsoptionen, die weder Code laden noch Dateien
/// schreiben noch Verbindungen umleiten können.
fn is_known_harmless_jvm_token(lower: &str) -> bool {
    fn is_name(s: &str) -> bool {
        !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
    }
    fn is_size(s: &str) -> bool {
        let digits = s.trim_end_matches(|c: char| matches!(c, 'k' | 'm' | 'g' | 't' | '%'));
        !digits.is_empty() && digits.len() + 1 >= s.len() && digits.chars().all(|c| c.is_ascii_digit() || c == '.')
    }

    // Speicher und Stack: -Xmx4G, -Xms1024M, -Xmn512m, -Xss2m
    for prefix in ["-xmx", "-xms", "-xmn", "-xss"] {
        if let Some(rest) = lower.strip_prefix(prefix) {
            return is_size(rest);
        }
    }
    // -XX:+UseG1GC / -XX:-UseAdaptiveSizePolicy / -XX:MaxGCPauseMillis=200 / -XX:G1HeapRegionSize=32M
    if let Some(rest) = lower.strip_prefix("-xx:") {
        if let Some(flag) = rest.strip_prefix('+').or_else(|| rest.strip_prefix('-')) {
            return is_name(flag);
        }
        return match rest.split_once('=') {
            Some((name, value)) => is_name(name) && (is_size(value) || value == "true" || value == "false"),
            None => false,
        };
    }
    // bekannter Kennzeichner aus den verbreiteten „Aikar-Flags“ (Wert ist nur ein Text)
    if lower == "-dusing.aikars.flags=https://mcflags.emc.gs" {
        return true;
    }
    // -Dschlüssel=einfacher.wert (kein Pfad, keine Adresse, keine Liste)
    if let Some(rest) = lower.strip_prefix("-d") {
        let (key, value) = rest.split_once('=').unwrap_or((rest, ""));
        let key_ok = !key.is_empty() && key.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'));
        let value_ok = value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-' | '+' | ','))
            && !value.contains("..");
        return key_ok && value_ok;
    }
    // einfache Schalter: -Xshare:auto, -Xverify:none, -Xnoclassgc, -server, -ea
    if let Some(rest) = lower.strip_prefix("-x") {
        let (name, value) = rest.split_once(':').unwrap_or((rest, ""));
        return is_name(name) && value.chars().all(|c| c.is_ascii_alphanumeric());
    }
    matches!(
        lower,
        "-server" | "-client" | "-ea" | "-da" | "-esa" | "-dsa" | "-verbose:gc" | "--enable-preview"
            | "--enable-native-access=all-unnamed"
    )
}

/// Spiel-Argumente, die ohne Nachfrage erlaubt sind (Fenstergröße/Vollbild).
fn is_harmless_game_arg(arg: &str) -> bool {
    let lower = arg.trim().to_ascii_lowercase();
    matches!(lower.as_str(), "--width" | "--height" | "--fullscreen")
        || (!lower.is_empty() && lower.chars().all(|c| c.is_ascii_digit()))
}

/// Neue eigene Spiel-Argumente, die bestätigt werden müssen (z. B. `--gameDir \\server\share`).
pub fn changed_game_args(old: &[String], new: &[String]) -> Vec<String> {
    new.iter()
        .filter(|arg| !arg.trim().is_empty() && !is_harmless_game_arg(arg) && !old.contains(arg))
        .cloned()
        .collect()
}

/// Neuer eigener Spielordner – Bibliotheken und Versionen werden dann von dort geladen.
pub fn changed_game_directory(
    old: Option<&std::path::Path>,
    new: Option<&std::path::Path>,
) -> Option<String> {
    let new = new.filter(|p| !p.as_os_str().is_empty())?;
    (old != Some(new)).then(|| new.display().to_string())
}

/// Gefährliche JVM-Argumente, die in `new` stehen, aber nicht schon in `old`.
pub fn new_dangerous_jvm_args(old: Option<&str>, new: Option<&str>) -> Vec<String> {
    let old = dangerous_jvm_args(old);
    dangerous_jvm_args(new)
        .into_iter()
        .filter(|arg| !old.contains(arg))
        .collect()
}

fn non_empty(value: &Option<String>) -> Option<&str> {
    value.as_deref().map(str::trim).filter(|v| !v.is_empty())
}

/// Start-Befehle, die neu gesetzt oder geändert wurden (Entfernen ist immer erlaubt).
pub fn changed_hooks(old: &Hooks, new: &Hooks) -> Vec<String> {
    let mut changes = Vec::new();
    let pairs = [
        ("Vor dem Start / Before launch", &old.pre_launch, &new.pre_launch),
        ("Wrapper", &old.wrapper, &new.wrapper),
        ("Nach dem Beenden / After exit", &old.post_exit, &new.post_exit),
    ];
    for (label, before, after) in pairs {
        if let Some(cmd) = non_empty(after) {
            if non_empty(before) != Some(cmd) {
                changes.push(format!("{label}: {cmd}"));
            }
        }
    }
    changes
}

/// Neues eigenes Java, das künftig gestartet würde.
pub fn changed_java_path(old: &ProfileSettings, new: &ProfileSettings) -> Option<String> {
    if !new.use_custom_java_path {
        return None;
    }
    let path = non_empty(&new.java_path)?;
    let unchanged = old.use_custom_java_path && non_empty(&old.java_path) == Some(path);
    (!unchanged).then(|| path.to_string())
}

/// Zeigt einen nativen Warn-Dialog; `true` nur, wenn der Nutzer ausdrücklich erlaubt.
pub async fn confirm<R: Runtime>(app: &AppHandle<R>, changes: &[String]) -> bool {
    let message: String = [
        "Diese Einstellung lässt den Launcher beim Spielstart Programme oder fremden Code ausführen:"
            .to_string(),
        "This setting makes the launcher run programs or foreign code when the game starts:"
            .to_string(),
        String::new(),
        changes.join("\n"),
        String::new(),
        "Nur erlauben, wenn du das gerade selbst eingetragen hast.".to_string(),
        "Only allow this if you just entered it yourself.".to_string(),
    ]
    .join("\n");
    confirm_dialog(app, message).await
}

/// Nativer Ja/Nein-Dialog (Erlauben/Abbrechen), den Skripte in der Oberfläche nicht bedienen können.
pub async fn confirm_dialog<R: Runtime>(app: &AppHandle<R>, message: String) -> bool {
    let app = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        app.dialog()
            .message(message)
            .title("Salami Client - Sicherheitsabfrage / Security check")
            .kind(MessageDialogKind::Warning)
            .buttons(MessageDialogButtons::OkCancelCustom(
                "Erlauben / Allow".to_string(),
                "Abbrechen / Cancel".to_string(),
            ))
            .blocking_show()
    })
    .await
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_code_loading_jvm_args() {
        let args = "-XX:+UseG1GC -Xmx4G -javaagent:C:\\x\\a.jar -XX:OnError=\"calc.exe\" @args.txt -cp x.jar -Dfoo=bar";
        let found = dangerous_jvm_args(Some(args));
        assert_eq!(
            found,
            vec!["-javaagent:C:\\x\\a.jar", "-XX:OnError=\"calc.exe\"", "@args.txt", "-cp", "x.jar"]
        );
        assert!(dangerous_jvm_args(Some("-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dsun.java2d.opengl=true")).is_empty());
        assert!(dangerous_jvm_args(Some("-XX:+ParallelRefProcEnabled -p")).len() == 1);
        assert!(dangerous_jvm_args(None).is_empty());
    }

    #[test]
    fn common_performance_flags_need_no_confirmation() {
        let aikar = "-Xms4G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
            -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 \
            -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 \
            -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
            -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem \
            -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
            -Dfml.ignoreInvalidMinecraftCertificates=true -Dfile.encoding=UTF-8 -Xss2m -XX:+UseZGC -XX:+ZGenerational";
        assert!(dangerous_jvm_args(Some(aikar)).is_empty(), "{:?}", dangerous_jvm_args(Some(aikar)));
    }

    #[test]
    fn redirects_and_option_files_need_confirmation() {
        for arg in [
            "-Dminecraft.api.auth.host=https://evil.example",
            "-Dminecraft.api.services.host=https://evil.example",
            "-Djavax.net.ssl.trustStore=C:\\x.jks",
            "-XX:VMOptionsFile=C:\\x.txt",
            "-XX:Flags=x",
            "-XX:ErrorFile=C:\\Startup\\x.bat",
            "-Dorg.lwjgl.librarypath=\\\\host\\share",
            "-Djna.library.path=C:\\x",
            "-Dloader.addMods=x.jar",
            "-Xlog:gc*:file=C:\\x.bat",
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "-Dfoo=C:\\path",
        ] {
            assert_eq!(dangerous_jvm_args(Some(arg)), vec![arg.to_string()], "{arg}");
        }
    }

    #[test]
    fn game_args_and_directory() {
        let old: Vec<String> = vec![];
        let new: Vec<String> = ["--width", "1920", "--gameDir", "\\\\host\\share"].iter().map(|s| s.to_string()).collect();
        assert_eq!(changed_game_args(&old, &new), vec!["--gameDir", "\\\\host\\share"]);
        assert!(changed_game_args(&new, &new).is_empty());
        let a = std::path::Path::new("D:\\Games");
        assert_eq!(changed_game_directory(None, Some(a)), Some("D:\\Games".to_string()));
        assert_eq!(changed_game_directory(Some(a), Some(a)), None);
        assert_eq!(changed_game_directory(Some(a), None), None);
    }

    #[test]
    fn only_new_dangerous_args_count() {
        let old = Some("-javaagent:a.jar -Xmx2G");
        assert!(new_dangerous_jvm_args(old, Some("-javaagent:a.jar -Xmx4G")).is_empty());
        assert_eq!(new_dangerous_jvm_args(old, Some("-javaagent:b.jar")), vec!["-javaagent:b.jar"]);
    }

    #[test]
    fn hook_changes_but_not_removals() {
        let old = Hooks { pre_launch: Some("a.bat".into()), wrapper: None, post_exit: None };
        let same = old.clone();
        assert!(changed_hooks(&old, &same).is_empty());
        let removed = Hooks::default();
        assert!(changed_hooks(&old, &removed).is_empty());
        let added = Hooks { pre_launch: Some("a.bat".into()), wrapper: Some("  ".into()), post_exit: Some("b.exe".into()) };
        assert_eq!(changed_hooks(&old, &added).len(), 1);
    }
}
