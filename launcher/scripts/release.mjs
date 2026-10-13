// Salami-Release: signierten Installer bauen und als GitHub-Release veröffentlichen.
//
//   node scripts/release.mjs --notes "Was ist neu"            (bauen + hochladen)
//   node scripts/release.mjs --notes "..." --skip-build       (vorhandenen Build hochladen)
//   node scripts/release.mjs --dry-run                        (nur bauen + latest.json schreiben)
//
// Voraussetzungen: Version in src-tauri/tauri.conf.json erhöht, `gh auth login` erledigt,
// Signaturschlüssel in %USERPROFILE%\.salami-client\updater.key (oder TAURI_SIGNING_PRIVATE_KEY).
// Der Launcher liest .../releases/latest/download/latest.json (branding.rs: UPDATE_BASE_URL).
import { spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const tauriDir = join(root, "src-tauri");
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

function run(command, commandArgs, options = {}) {
  const line = [command, ...commandArgs].map((a) => (/\s/.test(a) ? `"${a}"` : a)).join(" ");
  const result = spawnSync(line, { stdio: "inherit", shell: true, cwd: root, ...options });
  if (result.status !== 0) {
    console.error(`Fehlgeschlagen (${result.status}): ${line}`);
    process.exit(result.status || 1);
  }
}

function capture(command) {
  const result = spawnSync(command, { shell: true, cwd: root, encoding: "utf8" });
  return result.status === 0 ? result.stdout.trim() : null;
}

const version = JSON.parse(readFileSync(join(tauriDir, "tauri.conf.json"), "utf8")).version;
const tag = `v${version}`;
const notes = option("--notes") ?? `Salami Client ${version}`;
console.log(`Salami Client ${version}`);

const repo = capture("gh repo view --json nameWithOwner --jq .nameWithOwner");
if (!repo && !flag("--dry-run")) {
  console.error("Kein GitHub-Repo gefunden – `gh auth login` und `git remote add origin …` erledigt?");
  process.exit(1);
}
if (repo && capture(`gh release view ${tag} --json tagName`)) {
  console.error(`Release ${tag} gibt es schon – Version in src-tauri/tauri.conf.json erhöhen.`);
  process.exit(1);
}

if (!flag("--skip-build")) {
  const env = { ...process.env };
  if (!env.TAURI_SIGNING_PRIVATE_KEY) {
    const keyFile = join(homedir(), ".salami-client", "updater.key");
    if (!existsSync(keyFile)) {
      console.error(`Signaturschlüssel fehlt: ${keyFile}`);
      process.exit(1);
    }
    env.TAURI_SIGNING_PRIVATE_KEY = readFileSync(keyFile, "utf8").trim();
  }
  env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD ??= "";
  run("npx", [
    "yarn@1.22.22", "tauri", "build",
    "--config", "src-tauri/tauri.release-local.json",
    "--config", "src-tauri/tauri.release-signed.json",
  ], { env });
}

const bundleDir = join(tauriDir, "target", "release", "bundle", "nsis");
const built = join(bundleDir, `Salami Client_${version}_x64-setup.exe`);
for (const file of [built, `${built}.sig`]) {
  if (!existsSync(file)) {
    console.error(`Nicht gefunden: ${file}`);
    process.exit(1);
  }
}

// GitHub ersetzt Leerzeichen in Dateinamen – daher ohne Leerzeichen hochladen
const outDir = join(tauriDir, "target", "release", "github-release");
mkdirSync(outDir, { recursive: true });
const assetName = `Salami-Client_${version}_x64-setup.exe`;
const asset = join(outDir, assetName);
copyFileSync(built, asset);

const manifest = {
  version,
  notes,
  pub_date: new Date().toISOString(),
  platforms: {
    "windows-x86_64": {
      signature: readFileSync(`${built}.sig`, "utf8").trim(),
      url: `https://github.com/${repo ?? "OWNER/salami-client"}/releases/download/${tag}/${assetName}`,
    },
  },
};
const manifestFile = join(outDir, "latest.json");
writeFileSync(manifestFile, JSON.stringify(manifest, null, 2) + "\n");
console.log(`latest.json geschrieben: ${manifestFile}`);

if (flag("--dry-run")) {
  console.log("Dry run – nichts hochgeladen.");
  process.exit(0);
}

// Notizen über eine Datei – so übersteht jeder Text das Shell-Quoting
const notesFile = join(outDir, "notes.md");
writeFileSync(notesFile, `${notes}\n`);
run("gh", [
  "release", "create", tag, asset, manifestFile,
  "--title", `Salami Client ${version}`,
  "--notes-file", notesFile,
]);
console.log(`Veröffentlicht: https://github.com/${repo}/releases/tag/${tag}`);
