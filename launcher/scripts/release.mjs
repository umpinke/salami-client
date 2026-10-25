// Salami-Release: signierten Installer bauen und als GitHub-Release veröffentlichen.
//
//   node scripts/release.mjs --notes "Was ist neu"            (bauen + hochladen)
//   node scripts/release.mjs --notes-file notes.md            (Notizen aus Datei)
//   node scripts/release.mjs --notes "..." --skip-build       (vorhandenen Build hochladen)
//   node scripts/release.mjs --dry-run                        (nur bauen + latest.json schreiben)
//   node scripts/release.mjs --beta --notes "..."             (Beta, Version z. B. 1.1.0-beta.1)
//
// Kanäle: Normale Releases sind „latest“ → .../releases/latest/download/latest.json.
// Betas sind Vorab-Versionen; ihre Updatedatei liegt zusätzlich im festen Release `beta`
// → .../releases/download/beta/latest.json (branding.rs: UPDATE_BASE_URL_BETA). Ein normales
// Release aktualisiert die Beta-Updatedatei mit, sobald es neuer ist als die letzte Beta.
//
// Voraussetzungen: Version in src-tauri/tauri.conf.json erhöht, `gh auth login` erledigt,
// Signaturschlüssel in %USERPROFILE%\.salami-client\updater.key (oder TAURI_SIGNING_PRIVATE_KEY),
// CurseForge-API-Key in %USERPROFILE%\.salami-client\curseforge.key (oder SALAMI_CURSEFORGE_API_KEY).
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
const beta = flag("--beta");
const BETA_CHANNEL_TAG = "beta";
if (beta !== version.includes("-")) {
  console.error(
    beta
      ? `Beta braucht eine Vorab-Version wie 1.1.0-beta.1 (tauri.conf.json: ${version}).`
      : `${version} ist eine Vorab-Version – mit --beta veröffentlichen.`,
  );
  process.exit(1);
}

/** Semver-Vergleich inkl. Vorab-Teil (1.1.0-beta.2 < 1.1.0). */
function compareVersions(a, b) {
  const parse = (v) => {
    const [core, pre] = v.split("-", 2);
    return { core: core.split(".").map(Number), pre: pre ? pre.split(".") : [] };
  };
  const x = parse(a);
  const y = parse(b);
  for (let i = 0; i < 3; i++) if (x.core[i] !== y.core[i]) return x.core[i] - y.core[i];
  if (!x.pre.length || !y.pre.length) return y.pre.length - x.pre.length;
  for (let i = 0; i < Math.max(x.pre.length, y.pre.length); i++) {
    const p = x.pre[i];
    const q = y.pre[i];
    if (p === undefined || q === undefined) return p === undefined ? -1 : 1;
    if (p === q) continue;
    const pn = /^\d+$/.test(p);
    const qn = /^\d+$/.test(q);
    if (pn && qn) return Number(p) - Number(q);
    if (pn !== qn) return pn ? -1 : 1;
    return p < q ? -1 : 1;
  }
  return 0;
}
const notesFileArg = option("--notes-file");
const notes = notesFileArg
  ? readFileSync(notesFileArg, "utf8").trim()
  : option("--notes") ?? `Salami Client ${version}`;
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
  // build.rs baut den CurseForge-Key ein – ohne ihn wäre CurseForge im Release aus
  const curseforgeKeyFile = join(homedir(), ".salami-client", "curseforge.key");
  if (
    !env.SALAMI_CURSEFORGE_API_KEY &&
    !(existsSync(curseforgeKeyFile) && readFileSync(curseforgeKeyFile, "utf8").trim())
  ) {
    console.error(`CurseForge-API-Key fehlt: ${curseforgeKeyFile} (oder SALAMI_CURSEFORGE_API_KEY)`);
    process.exit(1);
  }
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
  "--title", beta ? `Salami Client ${version} (Beta)` : `Salami Client ${version}`,
  "--notes-file", notesFile,
  beta ? "--prerelease" : "--latest",
]);
console.log(`Veröffentlicht: https://github.com/${repo}/releases/tag/${tag}`);

// Beta-Kanal: Updatedatei im festen Release `beta` (Vorab-Version, nie „latest“)
if (!capture(`gh release view ${BETA_CHANNEL_TAG} --json tagName`)) {
  const channelNotes = join(outDir, "beta-channel.md");
  writeFileSync(
    channelNotes,
    "Updatedatei für den Beta-Kanal (Einstellungen → Beta Updates). Die Installer stehen bei den Vorab-Versionen.\n",
  );
  run("gh", [
    "release", "create", BETA_CHANNEL_TAG,
    "--prerelease", "--target", "main",
    "--title", "Beta-Kanal",
    "--notes-file", channelNotes,
  ]);
}
const channelManifest = capture(`gh release download ${BETA_CHANNEL_TAG} --pattern latest.json --output -`);
const channelVersion = (() => {
  try {
    return channelManifest ? JSON.parse(channelManifest).version : null;
  } catch {
    return null;
  }
})();
if (!channelVersion || compareVersions(version, channelVersion) >= 0) {
  run("gh", ["release", "upload", BETA_CHANNEL_TAG, manifestFile, "--clobber"]);
  console.log(`Beta-Kanal zeigt jetzt auf ${version}.`);
} else {
  console.log(`Beta-Kanal bleibt bei ${channelVersion} (neuer als ${version}).`);
}
