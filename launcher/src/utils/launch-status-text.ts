import i18n from "../i18n/i18n";

/**
 * The backend reports launch progress as plain English text (it doesn't know the UI
 * language). This maps the known messages to translated, player-friendly texts and
 * drops developer timings like "(412ms)". Unknown messages pass through unchanged.
 */

const t = (key: string, options?: Record<string, unknown>): string =>
  i18n.t(`launch.progress.${key}`, options) as string;

/** Labels of `timed_step` in `minecraft/installer.rs` → key suffix. */
const STEPS: Record<string, string> = {
  "sync packs": "sync_packs",
  libraries: "libraries",
  natives: "natives",
  client: "client",
  "profile mods": "mods",
  "sync pack mods": "sync_pack_mods",
  "sync mods": "sync_mods",
  launch: "launch",
};
const STEP_LABELS = Object.keys(STEPS).join("|");

type Rule = [RegExp, (match: RegExpMatchArray) => string];

const RULES: Rule[] = [
  // installer.rs: timed steps
  [new RegExp(`^(${STEP_LABELS})\\.\\.\\.$`), (m) => t(`step.${STEPS[m[1]]}`)],
  [new RegExp(`^(${STEP_LABELS}) completed!$`), (m) => t(`step.${STEPS[m[1]]}_done`)],

  // installer.rs: Java
  [/^Installing Java (\d+)\.\.\.$/, (m) => t("java", { version: m[1] })],
  [/^Java (\d+) installation completed!$/, (m) => t("java_done", { version: m[1] })],
  [/^Using custom Java installation!$/, () => t("java_custom")],

  // installer.rs: client mod pack, mod list, hook, migration
  [/^Downloading pack '.+' mods\.\.\.$/, () => t("pack_mods")],
  [/^Pack '.+' mods downloaded$/, () => t("pack_mods_done")],
  [/^Error downloading pack '.+' mods!$/, () => t("pack_mods_failed")],
  [/^Resolving final mod list\.\.\.$/, () => t("resolving_mods")],
  [/^Resolved (\d+) mods for sync\.$/, (m) => t("resolved_mods", { count: Number(m[1]) })],
  [/^Executing pre-launch hook\.\.\.$/, () => t("pre_launch_hook")],
  [/^Migration failed: (.+)$/s, (m) => t("migration_failed", { error: m[1] })],
  [/^Preparing migration\.\.\.$/, () => t("migration_preparing")],
  [/^Copying (\d+) files\.\.\.$/, (m) => t("migration_copying", { count: Number(m[1]) })],
  [/^Migration completed!$/, () => t("migration_done")],
  [/^Launch process wurde abgebrochen$/, () => t("cancelled")],

  // mc_assets_download.rs
  [/^Starting download for asset index: .+$/, () => t("assets_checking")],
  [/^Downloading Minecraft assets index\.\.\.$/, () => t("assets_checking")],
  [/^Asset index downloaded\. Found \d+ assets to process$/, () => t("assets_checking")],
  [/^Reused existing Minecraft assets\. Checking for missing files\.\.\.$/, () => t("assets_checking")],
  [/^All Minecraft assets already up to date!$/, () => t("assets_up_to_date")],
  [/^Downloading (\d+) Minecraft assets\.\.\.$/, (m) => t("assets_downloading", { count: Number(m[1]) })],
  [/^Downloading Minecraft assets: (\d+)\/(\d+)$/, (m) => t("assets_progress", { done: m[1], total: m[2] })],
  [/^Asset download completed with (\d+) errors$/, (m) => t("assets_errors", { count: Number(m[1]) })],
  [/^Minecraft assets download completed successfully!$/, () => t("assets_done")],

  // mc_utils.rs: reusing assets of an existing Minecraft installation
  [/^Checking for existing Minecraft assets \(index: .+\)$/, () => t("assets_looking")],
  [/^Found Minecraft directory at: .+$/s, () => t("assets_looking")],
  [/^Asset index .+ not found in existing Minecraft installation$/, () => t("assets_looking")],
  [/^Asset index already exists with correct size, no need to copy$/, () => t("assets_looking")],
  [/^(No existing Minecraft installation found|Copied index but assets directory not found), will download assets directly$/, () => t("assets_checking")],
  [/^Found existing Minecraft assets, preparing to copy$/, () => t("assets_reusing")],
  [/^Successfully copied asset index file$/, () => t("assets_reusing")],
  [/^Found \d+ assets to reuse from existing Minecraft installation$/, () => t("assets_reusing")],
  [/^Reusing Minecraft assets: (\d+)\/(\d+) files processed$/, (m) => t("assets_reusing_progress", { done: m[1], total: m[2] })],
  [/^Successfully reused Minecraft assets: .+$/, () => t("assets_reused")],

  // mc_utils.rs: importing data from the regular Minecraft launcher
  [/^Checking for user data to import\.\.\.$/, () => t("import_checking")],
  [/^Default Minecraft installation not found\.$/, () => t("import_none")],
  [/^Found existing installation, copying files\.\.\.$/, () => t("import_copying")],
  [/^\((\d+)\/(\d+)\) Importing user data\.\.\.$/, (m) => t("import_progress", { done: m[1], total: m[2] })],
  [/^User data import complete\.$/, () => t("import_done")],
  [/^Error during data import\.$/, () => t("import_failed")],
  [/^Copying StartUpHelper files\.\.\.$/, () => t("startup_files")],
  [/^StartUpHelper files copied: .+$/, () => t("startup_files_done")],

  // modloader/*_installer.rs and *_patcher.rs
  [/^Installing (Fabric|Quilt|Forge|NeoForge)\.\.\.$/, (m) => t("loader_installing", { loader: m[1] })],
  [/^(Fabric|Quilt|Forge|NeoForge) installation completed!$/, (m) => t("loader_done", { loader: m[1] })],
  [/^(Forge|NeoForge) Version (.+) wird verwendet$/, (m) => t("loader_version", { loader: m[1], version: m[2] })],
  [/^Downloading (Forge|NeoForge) installer\.\.\.$/, (m) => t("loader_installer", { loader: m[1] })],
  [/^(Forge|NeoForge) Installer wird extrahiert\.\.\.$/, (m) => t("loader_extracting", { loader: m[1] })],
  [/^(?:Legacy )?(Forge|NeoForge)(?: Installer)? Libraries werden heruntergeladen\.\.\.$/, (m) => t("loader_libraries", { loader: m[1] })],
  [/^(Forge|NeoForge) wird gepatcht\.\.\.$/, (m) => t("loader_patching", { loader: m[1] })],
  [/^Vorgepatchte (Forge|NeoForge)-Client Datei gefunden/, (m) => t("loader_patched", { loader: m[1] })],
  [/^(Forge|NeoForge) Processor: .+ \((\d+)\/(\d+)\)$/, (m) => t("loader_patching_progress", { loader: m[1], done: m[2], total: m[3] })],
];

/** Developer timings the backend appends, e.g. "libraries completed! (412ms)". */
const TIMING = /\s*\(\d+\s*ms\)\s*$/;

export function translateLaunchStatus(message: string): string {
  const text = message.replace(TIMING, "").trim();
  for (const [pattern, render] of RULES) {
    const match = text.match(pattern);
    if (match) return render(match);
  }
  return text;
}
