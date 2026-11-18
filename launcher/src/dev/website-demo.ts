/**
 * Website-Demo: der echte Launcher im Browser, eingebettet auf der Salami-Website
 * („Der Launcher. Probier ihn aus.“). Wie die Design-Vorschau ersetzen Tauris Mocks
 * das Rust-Backend – hier aber mit mehr Beispieldaten, damit sich alles echt anfühlt:
 * Salami-Modus, Steve-Skin, echte Modrinth-Suche, simuliertes Starten.
 *
 * Bauen: node node_modules/vite/bin/vite.js build --config vite.website-demo.config.ts
 * Die Website steuert die Demo per postMessage ({ source: "salami-site", type, ... }).
 */
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import { emit } from "@tauri-apps/api/event";
import capeTemplateUrl from "../../src-tauri/assets/salami_cape_template.png";
import buddyCatalog from "../../../server/capes/buddies.json";
import emoteCatalog from "../../../server/capes/emotes.json";
import hatCatalog from "../../../server/capes/hats.json";

const now = new Date().toISOString();
const params = new URLSearchParams(location.search);

// Salami-Modus ist in der Demo immer an (sonst blaues NoRisk-Theme).
localStorage.setItem(
  "launcher-theme-storage",
  JSON.stringify({ state: { selectedThemeId: "salami_theme", openedAdventDoors: [], originalAccentColor: null }, version: 0 }),
);

// ---------- Beispieldaten ----------

const sampleProfiles = [
  {
    id: "4fc38730-fe87-431d-81bc-9668b31359f4",
    name: "PVP",
    path: "PVP",
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
    group: null,
    is_standard_version: false,
  },
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
    id: "7e57a11a-0000-4000-8000-000000000003",
    name: "Vanilla 1.21.11",
    path: "Vanilla 1.21.11",
    game_version: "1.21.11",
    loader: "vanilla",
    loader_version: null,
    created: now,
    last_played: now,
    settings: {},
    state: "Installed",
    mods: [],
    selected_norisk_pack_id: null,
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
};

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
  last_played_profile: sampleProfiles[0].id,
  pack_rollout_override: "auto",
  clips: { enabled: false },
  log_level: "info",
  salami_theme: true,
};

const capeStore = {
  selected: "builtin:salami" as string | null,
  builtin: ["salami", "aurora", "magma", "galaxy", "neon", "prism", "sakura", "storm", "dragon", "pepperoni", "board"],
  capes: [{ id: "vorlage", name: "Vorlage", created: now, image: capeTemplateUrl }] as {
    id: string;
    name: string;
    created: string;
    image: string;
    share_id?: string;
  }[],
};

// Galerie: die echten freigegebenen Capes über den schreibgeschützten
// Proxy der Website (/capes-api/, nur GET). Lokal gibt es den Proxy nicht – dort die Live-Seite.
const CAPES_API =
  ["localhost", "127.0.0.1"].includes(location.hostname) ? "https://salami-client.umpinke.de/capes-api" : "/capes-api";
const galleryImageUrl = (id: string) => {
  if (!/^[0-9a-f]{64}$/.test(id)) throw new Error("ungültige Cape-ID");
  return `${CAPES_API}/v1/capes/img/${id}.png`;
};

// ---------- Skin & Avatar (Steve aus public/skins) ----------

let steveBase64: Promise<string> | null = null;
function getSteveBase64(): Promise<string> {
  steveBase64 ??= fetch("./skins/steve.png")
    .then((r) => r.blob())
    .then(
      (blob) =>
        new Promise<string>((resolve) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result).split(",")[1]);
          reader.readAsDataURL(blob);
        }),
    );
  return steveBase64;
}

async function faceAvatar(size: number): Promise<string> {
  const img = new Image();
  img.src = `data:image/png;base64,${await getSteveBase64()}`;
  await img.decode();
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(img, 8, 8, 8, 8, 0, 0, size, size);
  ctx.drawImage(img, 40, 8, 8, 8, 0, 0, size, size);
  return canvas.toDataURL("image/png");
}

// ---------- Mods: echte Suche über die öffentliche Modrinth-API ----------

const PROJECT_TYPES: Record<string, string> = {
  Mod: "mod",
  Modpack: "modpack",
  ResourcePack: "resourcepack",
  Shader: "shader",
  Datapack: "datapack",
};
const SORTS: Record<string, string> = {
  Relevance: "relevance",
  Downloads: "downloads",
  Follows: "follows",
  Newest: "newest",
  Updated: "updated",
};

// Auf der Website läuft Modrinth über die Proxys des eigenen Servers (/modrinth-api/, /modrinth-cdn/),
// damit Besucher keine Verbindung zu Modrinth aufbauen. Lokal gibt es die Proxys nicht.
const IS_LOCAL = ["localhost", "127.0.0.1"].includes(location.hostname);
const MODRINTH_API = IS_LOCAL ? "https://api.modrinth.com" : "/modrinth-api";
const MODRINTH_CDN = IS_LOCAL ? "https://cdn.modrinth.com" : "/modrinth-cdn";

interface SearchParams {
  query?: string;
  project_type?: string;
  sort?: string;
  limit?: number;
  offset?: number;
  game_version?: string;
  game_versions?: string[];
  categories?: string[];
  mod_loaders?: string[];
}

async function searchModrinth(p: SearchParams) {
  const type = PROJECT_TYPES[p.project_type ?? "Mod"] ?? "mod";
  const facets: string[][] = [type === "datapack" ? ["categories:datapack"] : [`project_type:${type}`]];
  const versions = p.game_versions?.length ? p.game_versions : p.game_version ? [p.game_version] : [];
  if (versions.length) facets.push(versions.map((v) => `versions:${v}`));
  if (p.mod_loaders?.length && type === "mod") facets.push(p.mod_loaders.map((l) => `categories:${l}`));
  for (const c of p.categories ?? []) facets.push([`categories:${c}`]);

  const limit = p.limit ?? 20;
  const offset = p.offset ?? 0;
  const url =
    `${MODRINTH_API}/v2/search?` +
    new URLSearchParams({
      query: p.query ?? "",
      limit: String(limit),
      offset: String(offset),
      index: SORTS[p.sort ?? "Relevance"] ?? "relevance",
      facets: JSON.stringify(facets),
    });

  const data = await fetch(url).then((r) => {
    if (!r.ok) throw new Error(`Modrinth ${r.status}`);
    return r.json();
  });

  return {
    results: data.hits.map((h: Record<string, any>) => ({
      project_id: h.project_id,
      source: "Modrinth",
      title: h.title,
      slug: h.slug,
      description: h.description,
      author: h.author,
      categories: h.categories ?? [],
      display_categories: h.display_categories ?? h.categories ?? [],
      client_side: h.client_side,
      server_side: h.server_side,
      downloads: h.downloads,
      follows: h.follows ?? null,
      icon_url: h.icon_url ? String(h.icon_url).replace("https://cdn.modrinth.com", MODRINTH_CDN) : null,
      project_url: `https://modrinth.com/${h.project_type}/${h.slug}`,
      project_type: h.project_type,
      latest_version: h.latest_version ?? null,
      date_created: h.date_created,
      date_modified: h.date_modified,
      license: h.license,
      gallery: h.gallery ?? [],
      versions: h.versions ?? null,
    })),
    pagination: { index: offset, page_size: limit, result_count: data.hits.length, total_count: data.total_hits },
  };
}

let modrinthTags: Promise<unknown> | null = null;
function getModrinthTags() {
  modrinthTags ??= Promise.all(
    ["category", "loader", "game_version"].map((tag) =>
      fetch(`${MODRINTH_API}/v2/tag/${tag}`).then((r) => r.json()),
    ),
  ).then(([categories, loaders, game_versions]) => ({ categories, loaders, game_versions }));
  return modrinthTags;
}

// ---------- Simuliertes Starten ----------

const launching = new Set<string>();
const LAUNCH_STEPS: [string, string, number][] = [
  ["downloading_libraries", "Downloading libraries...", 0.15],
  ["downloading_assets", "Downloading assets...", 0.4],
  ["installing_fabric", "Installing Fabric...", 0.6],
  ["downloading_mods", "Downloading mods...", 0.8],
  ["launching_minecraft", "Launching Minecraft...", 0.95],
];

function simulateLaunch(profileId: string) {
  launching.add(profileId);
  const send = (event_type: string, message: string, progress: number | null) =>
    emit("state_event", { event_id: crypto.randomUUID(), event_type, target_id: profileId, message, progress, error: null });

  LAUNCH_STEPS.forEach(([type, message, progress], i) => {
    setTimeout(() => send(type, message, progress), 300 + i * 750);
  });
  setTimeout(() => {
    send("launch_successful", "", 1);
    launching.delete(profileId);
    parent.postMessage({ source: "salami-demo", type: "launched" }, "*");
  }, 300 + LAUNCH_STEPS.length * 750);
}

// ---------- Backend-Mocks ----------

const LIST_COMMANDS = /^(get_|list_|search_|fetch_).*(s|_list)$/;

mockWindows("main");
mockIPC(
  async (cmd, args) => {
    const a = (args ?? {}) as Record<string, unknown>;
    switch (cmd) {
      // Capes
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
      case "salami_capes_gallery": {
        const r = await fetch(`${CAPES_API}/v1/capes/gallery`);
        if (!r.ok) throw new Error("Cape-Server nicht erreichbar");
        return (await r.json()).capes;
      }
      case "salami_capes_gallery_image":
        return galleryImageUrl(String(a.id));
      case "salami_capes_review_status":
        return Object.fromEntries(
          ((a.ids as string[]) ?? [])
            .filter((id) => capeStore.capes.some((c) => c.share_id === id))
            .map((id) => [id, { status: "approved" }]),
        );
      case "salami_capes_adopt": {
        const id = String(a.id);
        const own = capeStore.capes.find((c) => c.share_id === id);
        if (own) return own;
        const cape = { id: crypto.randomUUID(), name: String(a.name), created: now, image: galleryImageUrl(id), share_id: id };
        capeStore.capes = [...capeStore.capes, cape];
        return cape;
      }
      case "salami_capes_report":
        return "reported";
      case "salami_cosmetics_catalog": {
        // live vom Cape-Server, sobald der Proxy der Website die Kataloge durchlässt – sonst der Stand vom Bauen
        const kind = a.kind === "emotes" ? "emotes" : a.kind === "buddies" ? "buddies" : "hats";
        try {
          const r = await fetch(`${CAPES_API}/v1/${kind}/catalog`);
          if (r.ok) return await r.json();
        } catch {
          // Stand vom Bauen
        }
        return structuredClone(kind === "emotes" ? emoteCatalog : kind === "buddies" ? buddyCatalog : hatCatalog);
      }
      case "salami_capes_download_template":
        return "Downloads/salami_cape_template.png";

      // Einstellungen
      case "get_launcher_config":
        return structuredClone(launcherConfig);
      case "set_launcher_config":
        Object.assign(launcherConfig, a.config as object);
        return structuredClone(launcherConfig);
      case "plugin:webview|set_webview_zoom":
        (document.documentElement.style as CSSStyleDeclaration & { zoom: string }).zoom = String(a.value);
        return null;

      // Profile
      case "get_all_profiles_and_last_played":
        return { all_profiles: sampleProfiles, last_played_profile_id: sampleProfiles[0].id };
      case "list_profiles":
      case "get_all_profiles":
        return sampleProfiles;
      case "get_profile":
        return sampleProfiles.find((p) => p.id === a.id || p.id === a.profileId) ?? sampleProfiles[0];
      case "resolve_loader_version":
        return "0.19.5";

      // Starten
      case "check_for_group_migration_command":
        return { direction: "None", source_path: null, target_path: null };
      case "launch_profile":
      case "launch_profile_with_overrides":
        simulateLaunch(String(a.id ?? a.profileId));
        return null;
      case "is_profile_launching":
        return launching.has(String(a.profileId));
      case "abort_profile_launch":
        launching.delete(String(a.profileId));
        return null;

      // Account & Skin
      case "get_active_account":
        return sampleAccount;
      case "get_accounts":
        return [sampleAccount];
      case "get_active_skin":
        return { uuid: sampleAccount.id, base64_data: await getSteveBase64(), variant: "classic", source: "demo", updated_at: now };
      case "get_all_skins":
        return [{ id: "steve", name: "Steve", base64_data: await getSteveBase64(), variant: "classic", added_at: now }];
      case "get_face_avatar":
        return faceAvatar(Number(a.size ?? 64));

      // Mods (Toppings)
      case "search_mods_unified_command":
        return searchModrinth((a.params ?? {}) as SearchParams);
      case "get_modrinth_tags_command":
        return getModrinthTags();

      // Sonstiges
      case "get_app_version":
      case "get_launcher_version":
        return "1.7.0";
      case "is_flatpak":
      case "check_update_available_command":
        return false;
      case "refresh_norisk_packs":
      case "refresh_permissions":
      case "set_pack_rollout_config":
        return null;
      case "refresh_standard_versions":
        return [];
    }
    if (cmd.startsWith("plugin:")) return null;
    if (LIST_COMMANDS.test(cmd)) return [];
    // Nicht verfügbare Dienste schlagen fehl – wie im echten Salami Client ohne Backend.
    throw new Error(`demo: no mock for ${cmd}`);
  },
  { shouldMockEvents: true },
);
(window as unknown as { __TAURI_INTERNALS__: { convertFileSrc: (path: string) => string } }).__TAURI_INTERNALS__.convertFileSrc =
  (path) => path;

// Kein Top-Level-await: main importiert gemeinsame Module aus diesem Chunk – ein await hier
// würde im Build gegenseitig blockieren.
void import("../main").then(() => Promise.all([import("../i18n/i18n"), import("../store/settings-modal-store")])).then(
  ([{ default: i18n }, { useSettingsModalStore }]) => {
    // ---------- Steuerung durch die Website ----------
    type SettingsTab = Parameters<ReturnType<typeof useSettingsModalStore.getState>["open"]>[0];

    const setLanguage = (lang: unknown) => {
      if (lang === "de" || lang === "en") void i18n.changeLanguage(lang);
    };
    setLanguage(params.get("lang"));

    window.addEventListener("message", (event) => {
      const data = event.data as { source?: string; type?: string; route?: string; lang?: string } | null;
      if (!data || data.source !== "salami-site") return;
      if (data.type === "hello") report();
      if (data.type === "lang") setLanguage(data.lang);
      if (data.type === "navigate" && data.route) {
        // "settings" oder "settings:<tab>" (z. B. settings:appearance für den Salami-Modus)
        if (data.route.startsWith("settings")) {
          useSettingsModalStore.getState().open(data.route.split(":")[1] as SettingsTab);
        } else {
          useSettingsModalStore.getState().close();
          location.hash = `#/${data.route}`;
        }
      }
    });

    // Der Website melden, welche Seite gerade offen ist (für die Feature-Liste links).
    const report = () => {
      const route = useSettingsModalStore.getState().isOpen ? "settings" : location.hash.replace(/^#\//, "").split("/")[0];
      parent.postMessage({ source: "salami-demo", type: "route", route }, "*");
    };
    window.addEventListener("hashchange", report);
    useSettingsModalStore.subscribe(report);
    report();
  },
);
