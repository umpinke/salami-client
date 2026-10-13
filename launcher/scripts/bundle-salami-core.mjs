// Baut salami-core und legt es nach mod/build/launcher/salami-core.jar,
// von wo der Installer es als mods/salami-core.jar mitliefert
// (bundle.resources in src-tauri/tauri.release-local.json).
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const modDir = join(root, "..", "mod");
const jar = join(modDir, "build", "launcher", "salami-core.jar");
const windows = process.platform === "win32";

// Voller Pfad: cmd sucht nicht immer im aktuellen Ordner (NoDefaultCurrentDirectoryInExePath).
// .bat-Dateien lassen sich nur über die Shell starten, daher dort selbst quoten.
const args = ["launcherBundle", "--console=plain"];
const options = { cwd: modDir, stdio: "inherit" };
const result = windows
  ? spawnSync(`"${join(modDir, "gradlew.bat")}" ${args.join(" ")}`, { ...options, shell: true })
  : spawnSync(join(modDir, "gradlew"), args, options);

if (result.status !== 0 || !existsSync(jar)) {
  console.error(`salami-core konnte nicht gebaut werden (erwartet: ${jar})`);
  process.exit(result.status || 1);
}
console.log(`salami-core bereit: ${jar}`);
