// Salami-Release: signierte Installer für Windows, macOS und Linux bauen und als GitHub-Release
// veröffentlichen.
//
//   node scripts/release.mjs --notes "Was ist neu"            (bauen + hochladen)
//   node scripts/release.mjs --notes-file notes.md            (Notizen aus Datei)
//   node scripts/release.mjs --notes "..." --skip-build       (vorhandenen Windows-Build hochladen)
//   node scripts/release.mjs --notes "..." --windows-only     (nur Windows, ohne macOS/Linux)
//   node scripts/release.mjs --dry-run                        (nur bauen + latest.json schreiben)
//   node scripts/release.mjs --beta --notes "..."             (Beta, Version z. B. 1.1.0-beta.1)
//   node scripts/release.mjs --setup-ci                       (CurseForge-Key einmalig als GitHub-Secret)
//
// Windows baut das Skript lokal. macOS und Linux baut GitHub Actions
// (.github/workflows/launcher-mac-linux.yml) parallel dazu aus dem Release-Commit – der muss
// deshalb committet und gepusht sein. Danach lädt das Skript die Pakete herunter und signiert
// die Update-Dateien lokal: Der Updater-Schlüssel verlässt den Rechner nie. Ein Lauf für
// denselben Commit wird wiederverwendet, ein erneuter Aufruf nach einem Fehler baut also nicht
// alles neu.
//
// Kanäle: Normale Releases sind „latest“ → .../releases/latest/download/latest.json.
// Betas sind Vorab-Versionen; ihre Updatedatei liegt zusätzlich im festen Release `beta`
// → .../releases/download/beta/latest.json (branding.rs: UPDATE_BASE_URL_BETA). Ein normales
// Release aktualisiert die Beta-Updatedatei mit, sobald es neuer ist als die letzte Beta.
//
// Voraussetzungen: Version in src-tauri/tauri.conf.json erhöht, `gh auth login` erledigt,
// Signaturschlüssel in %USERPROFILE%\.salami-client\updater.key (oder TAURI_SIGNING_PRIVATE_KEY),
// CurseForge-API-Key in %USERPROFILE%\.salami-client\curseforge.key (oder SALAMI_CURSEFORGE_API_KEY)
// und für macOS/Linux als GitHub-Secret SALAMI_CURSEFORGE_API_KEY (--setup-ci).
// Der Launcher liest .../releases/latest/download/latest.json (branding.rs: UPDATE_BASE_URL).
import { spawnSync } from "node:child_process";
import { createHash, createPublicKey, verify } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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

function fail(message) {
  console.error(message);
  process.exit(1);
}

const secretsDir = join(homedir(), ".salami-client");
const curseforgeKeyFile = join(secretsDir, "curseforge.key");
const readSecret = (file) => (existsSync(file) ? readFileSync(file, "utf8").trim() : "");

const CI_WORKFLOW = "launcher-mac-linux.yml";
const CI_SECRET = "SALAMI_CURSEFORGE_API_KEY";
// Artefakte eines Workflow-Laufs hält GitHub 7 Tage (retention-days im Workflow)
const CI_RUN_MAX_AGE_MS = 6 * 24 * 60 * 60 * 1000;
const CI_TIMEOUT_MS = 150 * 60 * 1000;

if (flag("--setup-ci")) {
  const key = process.env.SALAMI_CURSEFORGE_API_KEY?.trim() || readSecret(curseforgeKeyFile);
  if (!key) fail(`CurseForge-API-Key fehlt: ${curseforgeKeyFile} (oder SALAMI_CURSEFORGE_API_KEY)`);
  // Über stdin, damit der Key in keiner Befehlszeile auftaucht
  const result = spawnSync("gh", ["secret", "set", CI_SECRET], { cwd: root, input: key, stdio: ["pipe", "inherit", "inherit"] });
  if (result.status !== 0) fail("GitHub-Secret konnte nicht gesetzt werden.");
  console.log(`GitHub-Secret ${CI_SECRET} gesetzt – macOS/Linux-Builds haben jetzt CurseForge.`);
  process.exit(0);
}

const tauriConf = JSON.parse(readFileSync(join(tauriDir, "tauri.conf.json"), "utf8"));
const version = tauriConf.version;
const tag = `v${version}`;
const beta = flag("--beta");
const crossPlatform = !flag("--windows-only");
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

/**
 * Prüft eine Updater-Signatur (minisign, wie `tauri signer sign` sie schreibt) gegen den
 * öffentlichen Schlüssel aus tauri.conf.json – genau das, was installierte Launcher vor einem
 * Update tun. So geht nie ein Release raus, das sie ablehnen würden.
 */
function verifyUpdateSignature(file, signature, pubkey) {
  const decode = (b64) => Buffer.from(b64, "base64").toString("utf8").trim().split(/\r?\n/);
  const keyBytes = Buffer.from(decode(pubkey)[1] ?? "", "base64");
  const [, sigLine = "", trustedLine = "", globalLine = ""] = decode(signature);
  const sigBytes = Buffer.from(sigLine, "base64");
  if (keyBytes.length !== 42 || sigBytes.length !== 74) return false;
  if (!sigBytes.subarray(2, 10).equals(keyBytes.subarray(2, 10))) return false;
  const key = createPublicKey({
    key: Buffer.concat([Buffer.from("302a300506032b6570032100", "hex"), keyBytes.subarray(10)]),
    format: "der",
    type: "spki",
  });
  const data = readFileSync(file);
  // „ED“ = vorab gehasht (BLAKE2b-512), „Ed“ = Datei direkt signiert
  const message = sigBytes.subarray(0, 2).toString() === "ED" ? createHash("blake2b512").update(data).digest() : data;
  const trusted = Buffer.from(trustedLine.replace(/^trusted comment: /, ""));
  return (
    verify(null, message, key, sigBytes.subarray(10)) &&
    verify(null, Buffer.concat([sigBytes.subarray(10), trusted]), key, Buffer.from(globalLine, "base64"))
  );
}

/** Pakete aus dem Workflow; `platform` = Eintrag in latest.json für die Update-Datei. */
const ciAssets = [
  { name: `Salami-Client_${version}_macOS-Apple-Silicon.dmg` },
  { name: `Salami-Client_${version}_macOS-Apple-Silicon.app.tar.gz`, platform: "darwin-aarch64" },
  { name: `Salami-Client_${version}_macOS-Intel.dmg` },
  { name: `Salami-Client_${version}_macOS-Intel.app.tar.gz`, platform: "darwin-x86_64" },
  { name: `Salami-Client_${version}_Linux.AppImage`, platform: "linux-x86_64" },
  // .deb-Installationen fragen diesen Eintrag ab (updater_utils.rs: deb_manifest_target)
  { name: `Salami-Client_${version}_Linux.deb`, platform: "linux-x86_64-deb" },
];

function ciRuns() {
  const json = capture(
    `gh run list --workflow ${CI_WORKFLOW} --event workflow_dispatch --limit 30 --json databaseId,displayTitle,status,conclusion,createdAt,url`,
  );
  return json ? JSON.parse(json) : [];
}

/** Startet den macOS/Linux-Build für `commit` – oder nimmt einen laufenden/fertigen dafür. */
async function startCiRun(repo, commit) {
  const label = `Release-Build ${commit}`;
  const reusable = ciRuns().find(
    (r) =>
      r.displayTitle === label &&
      (r.status !== "completed" || r.conclusion === "success") &&
      Date.now() - Date.parse(r.createdAt) < CI_RUN_MAX_AGE_MS,
  );
  if (reusable) {
    console.log(`macOS/Linux: vorhandener Build für ${commit.slice(0, 7)} – ${reusable.url}`);
    return reusable.databaseId;
  }
  const branch = capture("gh repo view --json defaultBranchRef --jq .defaultBranchRef.name") || "main";
  run("gh", ["workflow", "run", CI_WORKFLOW, "--repo", repo, "--ref", branch, "-f", `commit=${commit}`, "-f", `label=${label}`]);
  for (let i = 0; i < 30; i++) {
    await sleep(3000);
    const started = ciRuns().find((r) => r.displayTitle === label && r.status !== "completed");
    if (started) {
      console.log(`macOS/Linux: Build gestartet – ${started.url}`);
      return started.databaseId;
    }
  }
  fail(`Der gestartete Workflow-Lauf „${label}“ ist nicht aufgetaucht – auf GitHub unter Actions nachsehen.`);
}

async function waitForCiRun(runId) {
  const started = Date.now();
  let last = "";
  for (;;) {
    const json = capture(`gh run view ${runId} --json status,conclusion,url,jobs`);
    if (json) {
      const info = JSON.parse(json);
      const jobs = info.jobs.map((j) => `${j.name}: ${j.conclusion || j.status}`).join(", ");
      if (jobs !== last) {
        console.log(`macOS/Linux: ${jobs}`);
        last = jobs;
      }
      if (info.status === "completed") {
        if (info.conclusion !== "success") {
          fail(
            `macOS/Linux-Build fehlgeschlagen (${info.conclusion}): ${info.url}\n` +
              "Nach dem Beheben erneut starten – mit --skip-build bleibt der Windows-Build erhalten.",
          );
        }
        return;
      }
    }
    if (Date.now() - started > CI_TIMEOUT_MS) fail(`macOS/Linux-Build dauert zu lange: gh run view ${runId}`);
    await sleep(30000);
  }
}

const notesFileArg = option("--notes-file");
const notes = notesFileArg
  ? readFileSync(notesFileArg, "utf8").trim()
  : option("--notes") ?? `Salami Client ${version}`;
console.log(`Salami Client ${version}${crossPlatform ? "" : " (nur Windows)"}`);

const repo = capture("gh repo view --json nameWithOwner --jq .nameWithOwner");
if (!repo && !flag("--dry-run")) {
  console.error("Kein GitHub-Repo gefunden – `gh auth login` und `git remote add origin …` erledigt?");
  process.exit(1);
}
if (repo && capture(`gh release view ${tag} --json tagName`)) {
  console.error(`Release ${tag} gibt es schon – Version in src-tauri/tauri.conf.json erhöhen.`);
  process.exit(1);
}

// macOS/Linux zuerst anstoßen: GitHub baut, während hier Windows baut
let ciRunId = null;
if (crossPlatform) {
  if (!repo) fail("macOS/Linux baut GitHub – dafür braucht es das GitHub-Repo (oder --windows-only).");
  if (capture("git status --porcelain --untracked-files=no -- . ../mod ../packs")) {
    fail(
      "Nicht committete Änderungen in launcher/, mod/ oder packs/. GitHub baut macOS/Linux aus dem\n" +
        "Commit, Windows enthielte sie aber – erst committen und pushen (oder --windows-only).",
    );
  }
  const commit = capture("git rev-parse HEAD");
  if (!commit || !capture(`gh api repos/${repo}/commits/${commit} --jq .sha`)) {
    fail(`Commit ${commit?.slice(0, 7)} ist nicht auf GitHub – erst pushen (oder --windows-only).`);
  }
  const secrets = capture("gh secret list --json name --jq .[].name");
  if (secrets === null || !secrets.split(/\r?\n/).includes(CI_SECRET)) {
    fail(
      `GitHub-Secret ${CI_SECRET} fehlt – macOS/Linux hätten kein CurseForge.\n` +
        "Einmalig setzen: node scripts/release.mjs --setup-ci",
    );
  }
  ciRunId = await startCiRun(repo, commit);
}

const signingEnv = { ...process.env };
if (!signingEnv.TAURI_SIGNING_PRIVATE_KEY) {
  const keyFile = join(secretsDir, "updater.key");
  if (!existsSync(keyFile)) {
    console.error(`Signaturschlüssel fehlt: ${keyFile}`);
    process.exit(1);
  }
  signingEnv.TAURI_SIGNING_PRIVATE_KEY = readFileSync(keyFile, "utf8").trim();
}
signingEnv.TAURI_SIGNING_PRIVATE_KEY_PASSWORD ??= "";

if (!flag("--skip-build")) {
  // build.rs baut den CurseForge-Key ein – ohne ihn wäre CurseForge im Release aus
  if (!signingEnv.SALAMI_CURSEFORGE_API_KEY && !readSecret(curseforgeKeyFile)) {
    console.error(`CurseForge-API-Key fehlt: ${curseforgeKeyFile} (oder SALAMI_CURSEFORGE_API_KEY)`);
    process.exit(1);
  }
  run("npx", [
    "yarn@1.22.22", "tauri", "build",
    "--config", "src-tauri/tauri.release-local.json",
    "--config", "src-tauri/tauri.release-signed.json",
  ], { env: signingEnv });
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
const releaseUrl = (name) => `https://github.com/${repo ?? "OWNER/salami-client"}/releases/download/${tag}/${name}`;

// Eintrag in latest.json → [Datei, Signatur]
const updates = { "windows-x86_64": [asset, readFileSync(`${built}.sig`, "utf8").trim()] };
const assets = [asset];

if (ciRunId) {
  await waitForCiRun(ciRunId);
  const ciDir = join(outDir, "ci");
  rmSync(ciDir, { recursive: true, force: true });
  run("gh", ["run", "download", String(ciRunId), "--repo", repo, "--dir", ciDir]);
  // Wie tauri build: Signatur über TAURI_PRIVATE_KEY (Name bei `tauri signer sign`), leeres Passwort
  const signerEnv = {
    ...signingEnv,
    TAURI_PRIVATE_KEY: signingEnv.TAURI_SIGNING_PRIVATE_KEY,
    TAURI_PRIVATE_KEY_PASSWORD: signingEnv.TAURI_SIGNING_PRIVATE_KEY_PASSWORD,
  };
  for (const { name, platform } of ciAssets) {
    const suffix = name.slice(`Salami-Client_${version}_`.length).split(".")[0];
    const file = join(ciDir, `launcher-${suffix}`, name);
    if (!existsSync(file)) fail(`Nicht im macOS/Linux-Build: ${name} (stimmt die Version im gepushten Commit?)`);
    if (platform) {
      run("npx", ["yarn@1.22.22", "tauri", "signer", "sign", file], { env: signerEnv });
      updates[platform] = [file, readFileSync(`${file}.sig`, "utf8").trim()];
    }
    assets.push(file);
  }
}

const pubkey = tauriConf.plugins.updater.pubkey;
for (const [platform, [file, signature]] of Object.entries(updates)) {
  if (!verifyUpdateSignature(file, signature, pubkey)) {
    fail(`Signatur passt nicht zum Updater-Schlüssel in tauri.conf.json: ${platform} (${file})`);
  }
}
console.log(`Signaturen geprüft: ${Object.keys(updates).join(", ")}`);

const manifest = {
  version,
  notes,
  pub_date: new Date().toISOString(),
  platforms: Object.fromEntries(
    Object.entries(updates).map(([platform, [file, signature]]) => [
      platform,
      { signature, url: releaseUrl(file.split(/[\\/]/).pop()) },
    ]),
  ),
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
  "release", "create", tag, ...assets, manifestFile,
  "--title", beta ? `Salami Client ${version} (Beta)` : `Salami Client ${version}`,
  "--notes-file", notesFile,
  beta ? "--prerelease" : "--latest",
]);
console.log(`Veröffentlicht: https://github.com/${repo}/releases/tag/${tag}`);
for (const file of assets) console.log(`  ${releaseUrl(file.split(/[\\/]/).pop())}`);

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
