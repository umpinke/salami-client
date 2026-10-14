//! Schutz für Einstellungen, mit denen beim Spielstart fremde Programme oder fremder Code laufen
//! können: Start-Befehle (Hooks), eigenes Java und JVM-Optionen wie `-javaagent`. Die Oberfläche
//! kann sie nicht still setzen – jede neue solche Einstellung muss in einem nativen Dialog
//! bestätigt werden, den ein eingeschleustes Skript in der Oberfläche nicht wegklicken kann.

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
    DANGEROUS_JVM_PREFIXES.iter().any(|p| lower.starts_with(p))
        || DANGEROUS_JVM_FLAGS
            .iter()
            .any(|f| lower == *f || lower.starts_with(&format!("{f}=")))
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
            vec!["-javaagent:C:\\x\\a.jar", "-XX:OnError=\"calc.exe\"", "@args.txt", "-cp"]
        );
        assert!(dangerous_jvm_args(Some("-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dsun.java2d.opengl=true")).is_empty());
        assert!(dangerous_jvm_args(Some("-XX:+ParallelRefProcEnabled -p")).len() == 1);
        assert!(dangerous_jvm_args(None).is_empty());
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
