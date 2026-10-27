// Baut salami-core für alle Minecraft-Versionen und legt die Jars nach
// mod/build/launcher/salami-core-<version>.jar, von wo der Installer sie als
// mods/salami-core-<version>.jar mitliefert (bundle.resources in src-tauri/tauri.release-local.json).
// Danach muss packs/salami-packs.json dazu passen (scripts/update-salami-pack.mjs).
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const modDir = join(root, "..", "mod");
const outDir = join(modDir, "build", "launcher");
const windows = process.platform === "win32";

// Erwartete Jars: eine pro Stonecutter-Version ([„…“]-Abschnitte in stonecutter.properties.toml)
const builds = [...readFileSync(join(modDir, "stonecutter.properties.toml"), "utf8").matchAll(/^\["([^"]+)"\]\s*$/gm)].map((m) => m[1]);

// Alte Jars weg, damit der Installer nichts Veraltetes mitnimmt
rmSync(outDir, { recursive: true, force: true });

// Voller Pfad: cmd sucht nicht immer im aktuellen Ordner (NoDefaultCurrentDirectoryInExePath).
// .bat-Dateien lassen sich nur über die Shell starten, daher dort selbst quoten.
// "launcherBundle" ohne Projektpfad läuft in allen Versionen.
const args = ["launcherBundle", "--console=plain"];
const options = { cwd: modDir, stdio: "inherit" };
const result = windows
  ? spawnSync(`"${join(modDir, "gradlew.bat")}" ${args.join(" ")}`, { ...options, shell: true })
  : spawnSync(join(modDir, "gradlew"), args, options);

const jars = builds.map((build) => join(outDir, `salami-core-${build}.jar`));
const missing = jars.filter((jar) => !existsSync(jar));
if (result.status !== 0 || builds.length === 0 || missing.length > 0) {
  console.error(`salami-core konnte nicht gebaut werden (fehlt: ${missing.join(", ") || "alles"})`);
  process.exit(result.status || 1);
}
console.log(`salami-core bereit: ${jars.join(", ")}`);
