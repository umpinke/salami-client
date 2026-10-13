/**
 * Design-Vorschau: startet die Launcher-Oberfläche im normalen Browser (`yarn dev`,
 * dann /design-preview.html). Das Rust-Backend wird durch Tauris offizielle Mocks
 * ersetzt; Befehle liefern leere oder Beispiel-Daten. Nur zum Gestalten – nicht im Build.
 */
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";

const now = new Date().toISOString();

const sampleProfiles = [
  {
    id: "5a1a3100-c0de-4b1d-9a1a-5a1a3100bee5",
    name: "Salami Client 1.21.11",
    path: "salamiclient/1.21.11",
    game_version: "1.21.11",
    loader: "fabric",
    loader_version: "0.19.5",
    created: now,
    last_played: now,
    settings: {},
    state: "Installed",
    mods: [],
    selected_norisk_pack_id: "salami",
    disabled_norisk_mods_detailed: [],
    group: "SALAMI CLIENT",
    is_standard_version: true,
  },
  {
    id: "4fc38730-fe87-431d-81bc-9668b31359f4",
    name: "PVP",
    path: "PVP",
    game_version: "1.21.11",
    loader: "fabric",
    loader_version: "0.19.3",
    created: now,
    last_played: now,
    settings: {},
    state: "Installed",
    mods: [],
    selected_norisk_pack_id: "salami",
    disabled_norisk_mods_detailed: [],
    group: null,
    is_standard_version: false,
  },
];

const sampleAccount = {
  id: "00000000-0000-0000-0000-000000000001",
  username: "SalamiFan",
  minecraft_username: "SalamiFan",
  active: true,
  access_token: "preview",
};

/** Befehle, die eine Liste liefern. */
const LIST_COMMANDS = /^(get_|list_|search_|fetch_).*(s|_list)$/;

const launcherConfig = {
  version: 1,
  is_experimental: false,
  auto_check_updates: false,
  concurrent_downloads: 10,
  enable_discord_presence: false,
  check_beta_channel: false,
  profile_grouping_criterion: null,
  open_logs_after_starting: false,
  concurrent_io_limit: 10,
  hooks: { pre_launch: null, wrapper: null, post_exit: null },
  hide_on_process_start: false,
  global_memory_settings: { min: 1024, max: 4096 },
  global_custom_jvm_args: null,
  custom_game_directory: null,
  enable_analytics: false,
  use_browser_based_login: false,
  cache_natives_extraction: true,
  referral_state: null,
  last_played_profile: sampleProfiles[1].id,
  pack_rollout_override: "auto",
  clips: { enabled: false },
  log_level: "info",
  salami_theme: true,
  norisk_ingame: false,
};

/** Cape-Speicher im Speicher (statt salami_capes/ im Launcher-Ordner). */
const capeStore = {
  selected: "builtin:salami" as string | null,
  builtin: ["salami", "pepperoni", "board"],
  // Beispiel: die Vorlage selbst als eigenes Cape (zeigt Cape- und Elytra-Flächen)
  capes: [
    { id: "vorlage", name: "Vorlage", created: now, image: "/src-tauri/assets/salami_cape_template.png" },
  ] as { id: string; name: string; created: string; image: string }[],
};

mockWindows("main");
mockIPC((cmd, args) => {
  const a = (args ?? {}) as Record<string, unknown>;
  switch (cmd) {
    case "salami_capes_overview":
      return structuredClone(capeStore);
    case "salami_capes_select":
      capeStore.selected = (a.selection as string | null) ?? null;
      return null;
    case "salami_capes_save": {
      const id = (a.id as string | null) ?? crypto.randomUUID();
      const cape = { id, name: String(a.name), created: now, image: String(a.pngBase64) };
      capeStore.capes = [...capeStore.capes.filter((c) => c.id !== id), cape];
      return cape;
    }
    case "salami_capes_rename":
      capeStore.capes = capeStore.capes.map((c) => (c.id === a.id ? { ...c, name: String(a.name) } : c));
      return null;
    case "salami_capes_delete":
      capeStore.capes = capeStore.capes.filter((c) => c.id !== a.id);
      return null;
    case "salami_capes_download_template":
      return "C:\\Users\\Du\\Downloads\\salami_cape_vorlage.png";
    case "get_launcher_config":
      return structuredClone(launcherConfig);
    case "set_launcher_config":
      Object.assign(launcherConfig, a.config as object);
      return structuredClone(launcherConfig);
    case "plugin:webview|set_webview_zoom":
      // im Browser: Zoom über CSS nachstellen
      (document.documentElement.style as CSSStyleDeclaration & { zoom: string }).zoom = String(a.value);
      return null;
    case "get_all_profiles_and_last_played":
      return { all_profiles: sampleProfiles, last_played_profile_id: sampleProfiles[1].id };
    case "list_profiles":
    case "get_all_profiles":
      return sampleProfiles;
    case "get_app_version":
    case "get_launcher_version":
      return "1.0.0";
    case "get_active_account":
      return sampleAccount;
    case "get_accounts":
      return [sampleAccount];
    case "is_flatpak":
      return false;
    case "refresh_norisk_packs":
      return null;
    case "refresh_standard_versions":
      return [];
  }
  if (cmd.startsWith("plugin:")) return null;
  if (LIST_COMMANDS.test(cmd)) return [];
  // wie im echten Salami Client: nicht verfügbare Dienste schlagen fehl
  throw new Error(`design preview: no mock for ${cmd}`);
});
(window as unknown as { __TAURI_INTERNALS__: { convertFileSrc: (path: string) => string } })
  .__TAURI_INTERNALS__.convertFileSrc = (path) => path;

await import("../main");
