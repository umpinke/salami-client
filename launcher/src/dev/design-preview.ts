/**
 * Design-Vorschau: startet die Launcher-Oberfläche im normalen Browser (`yarn dev`,
 * dann /design-preview.html). Das Rust-Backend wird durch Tauris offizielle Mocks
 * ersetzt; Befehle liefern leere oder Beispiel-Daten. Nur zum Gestalten – nicht im Build.
 */
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import buddyCatalog from "../../../server/capes/buddies.json";
import emoteCatalog from "../../../server/capes/emotes.json";
import hatCatalog from "../../../server/capes/hats.json";

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
  use_browser_based_login: false,
  cache_natives_extraction: true,
  last_played_profile: sampleProfiles[1].id,
  clips: { enabled: false },
  log_level: "info",
  salami_theme: true,
  salami_app_sync: true,
  salami_app_clips: true,
};

/** Beispiel-Log für die Log-Ansichten. */
const SAMPLE_LOG = [
  "[21:27:03] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.19.5",
  "[21:27:05] [main/INFO]: Loading 52 mods:",
  "\t- fabric-api 0.141.6+1.21.11",
  "\t- salami 0.1.0",
  "\t- sodium 0.8.13+mc1.21.11",
  "[21:27:06] [main/WARN]: Mod 'Reese's Sodium Options' recommends version 0.4.2 or later of sodium-extra, which is missing!",
  "[21:27:08] [Render thread/INFO]: Backend library: LWJGL version 3.3.3-snapshot",
  "[21:27:09] [Render thread/INFO]: [Salami] Salami Client 0.1.0 geladen – 42 Module",
  "[21:27:10] [Render thread/DEBUG]: Reloading ResourceManager: vanilla, fabric, salami",
  "[21:27:12] [Render thread/WARN]: Missing sound for event: minecraft:item.goat_horn.play",
  "[21:27:14] [Render thread/INFO]: Sound engine started",
  "[21:27:15] [Render thread/INFO]: Created: 1024x1024x4 minecraft:textures/atlas/blocks.png-atlas",
  "[21:27:20] [Server thread/INFO]: Starting integrated minecraft server version 1.21.11",
  "[21:27:22] [Server thread/INFO]: Preparing spawn area: 84%",
  "[21:27:24] [Render thread/ERROR]: Failed to load texture salami:textures/cape/custom.png",
  "java.io.FileNotFoundException: salami:textures/cape/custom.png",
  "\tat net.minecraft.client.renderer.texture.SimpleTexture.load(SimpleTexture.java:45)",
  "\tat dev.salamiclient.modules.visual.CapesModule.loadCustom(CapesModule.java:131)",
  "[21:27:25] [Render thread/INFO]: Connecting to localhost, 25565",
  "[21:27:26] [Render thread/INFO]: [CHAT] SalamiFan joined the game",
  "[21:27:40] [Render thread/WARN]: Received passengers for unknown entity",
  "[21:28:02] [Render thread/INFO]: [CHAT] Welcome to the server!",
].join("\n");

/** design-preview.html?window=logs zeigt statt des Launchers das Log-Fenster (eigenes Fenster in der App). */
const LOG_WINDOW = new URLSearchParams(location.search).get("window") === "logs";
/** design-preview.html?window=updater zeigt das Update-Fenster (325×400 in der App); `&status=error|downloading` wählt den Zustand. */
const UPDATER_WINDOW = new URLSearchParams(location.search).get("window") === "updater";
/** design-preview.html?signedout zeigt den Willkommens-Bildschirm (kein Konto). */
const SIGNED_OUT = new URLSearchParams(location.search).has("signedout");
const logProcess = {
  id: "11111111-2222-3333-4444-555555555555",
  profile_id: sampleProfiles[0].id,
  start_time: new Date(Date.now() - 12 * 60_000).toISOString(),
  state: "Running",
  pid: 4242,
  account_uuid: null,
  account_name: "SalamiFan",
  minecraft_version: sampleProfiles[0].game_version,
  modloader: "fabric",
  modloader_version: "0.19.5",
  norisk_pack: null,
  profile_name: sampleProfiles[0].name,
  profile_image_url: null,
  memory_max_mb: 4096,
  log_session_id: "preview-session",
  mods: [],
};

/** Cape-Speicher im Speicher (statt salami_capes/ im Launcher-Ordner). */
const capeStore = {
  selected: "builtin:salami" as string | null,
  builtin: ["salami", "aurora", "magma", "galaxy", "neon", "prism", "sakura", "storm", "dragon", "pepperoni", "board"],
  // Beispiel: die Vorlage selbst als eigenes Cape (zeigt Cape- und Elytra-Flächen)
  capes: [
    { id: "vorlage", name: "Vorlage", created: now, image: "/src-tauri/assets/salami_cape_template.png", share_id: "a".repeat(64) },
    { id: "freigegeben", name: "Mein Brett", created: now, image: "/capes/board.png", share_id: "b".repeat(64) },
    { id: "abgelehnt", name: "Werbung", created: now, image: "/capes/pepperoni.png", share_id: "c".repeat(64) },
  ] as { id: string; name: string; created: string; image: string; share_id: string }[],
};
/** Prüfstatus auf dem Cape-Server; neu gezeichnete Capes fehlen hier („Nur für dich“). */
const capeReviews: Record<string, { status: string; reason?: string }> = {
  ["a".repeat(64)]: { status: "pending" },
  ["b".repeat(64)]: { status: "approved" },
  ["c".repeat(64)]: { status: "rejected", reason: "Bitte keine Werbung" },
  // Galerie-Capes sind freigegeben
  ["e".repeat(64)]: { status: "approved" },
  ["9".repeat(64)]: { status: "approved" },
};

/** Galerie: freigegebene Capes anderer Spieler („b…“ hat man schon als „Mein Brett“). */
const capeGallery = [
  { id: "e".repeat(64), approvedAt: now, wearers: 3 },
  { id: "b".repeat(64), approvedAt: now, wearers: 1 },
  { id: "9".repeat(64), approvedAt: now, wearers: 0 },
];
const capeGalleryImages: Record<string, string> = {
  ["e".repeat(64)]: "/capes/salami.png",
  ["9".repeat(64)]: "/src-tauri/assets/salami_cape_template.png",
};

/** Salami-App: gekoppelte Handys; beim dritten Abruf „koppelt“ sich ein zweites. */
let appDevices = [
  { id: "d_galaxy", name: "Galaxy S24", pairedAt: "2026-11-05T18:12:00Z", lastSeen: now },
];
let appDeviceCalls = 0;
/** Platzhalter-QR (nur fürs Layout, der echte kommt aus dem Rust-Backend). */
function previewQr(): string {
  const n = 25;
  let path = "";
  for (let y = 0; y < n; y++)
    for (let x = 0; x < n; x++) {
      const finder = (x < 7 || x >= n - 7) && y < 7 || x < 7 && y >= n - 7;
      if (finder ? Math.max(Math.abs((x % (n - 7)) - 3), Math.abs((y % (n - 7)) - 3)) !== 2 : (x * 7 + y * 13 + x * y) % 3 === 0)
        path += `M${x + 2},${y + 2}h1v1h-1z`;
    }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n + 4} ${n + 4}" shape-rendering="crispEdges"><rect width="${n + 4}" height="${n + 4}" fill="#fff"/><path d="${path}" fill="#000"/></svg>`;
}

// ── Freunde und Chat ─────────────────────────────────────────────────────────────
// /design-preview.html?friends=off zeigt die Einschalt-Seite.
let friendsEnabled = new URLSearchParams(location.search).get("friends") !== "off";
const friendsMe = { uuid: "00000000-0000-4000-8000-000000000001", name: "SalamiFan" };
const friendUuid = (n: number) => `00000000-0000-4000-8000-0000000001${String(n).padStart(2, "0")}`;
const friendsList = [
  { uuid: friendUuid(1), name: "killall123", online: true, status: "game", since: "2026-11-10T17:00:00Z" },
  { uuid: friendUuid(2), name: "Pepper_99", online: true, status: "launcher", since: "2026-11-12T17:00:00Z" },
  { uuid: friendUuid(3), name: "Brotzeit", online: false, status: null, since: "2026-11-01T17:00:00Z" },
  { uuid: friendUuid(4), name: "KaeseKoenig", online: false, status: null, since: "2026-10-21T17:00:00Z" },
];
let friendsIncoming = [{ uuid: friendUuid(9), name: "Wurstwasser", at: new Date().toISOString() }];
let friendsOutgoing = [{ uuid: friendUuid(8), name: "Schinken_LP", at: new Date().toISOString() }];
const friendsUnread: Record<string, number> = { [friendUuid(2)]: 2 };
let friendMessageId = 100;
const friendMessage = (from: string, to: string, text: string, minutesAgo: number, media?: Record<string, unknown>) => ({
  id: (friendMessageId++).toString(16).padStart(12, "0"),
  from,
  to,
  text,
  at: new Date(Date.now() - minutesAgo * 60_000).toISOString(),
  ...(media ? { media } : {}),
});
const friendsMessages: Record<string, ReturnType<typeof friendMessage>[]> = {
  [friendUuid(1)]: [
    friendMessage(friendUuid(1), friendsMe.uuid, "bist du gleich on?", 60 * 26),
    friendMessage(friendsMe.uuid, friendUuid(1), "ja, 5 min", 60 * 26 - 2),
    friendMessage(friendUuid(1), friendsMe.uuid, "schau dir den Clip an", 14),
    friendMessage(friendUuid(1), friendsMe.uuid, "[Clip] clutch.mp4", 13, { id: "a".repeat(32), kind: "clip", mime: "video/mp4", size: 18_400_000, name: "clutch.mp4", expired: true }),
    friendMessage(friendsMe.uuid, friendUuid(1), "stark! Hier meine neue Base", 9),
    friendMessage(friendsMe.uuid, friendUuid(1), "[Bild] 2026-11-17_18.02.11.png", 8, { id: "b".repeat(32), kind: "image", mime: "image/png", size: 1_240_000, name: "2026-11-17_18.02.11.png" }),
    friendMessage(friendUuid(1), friendsMe.uuid, "sieht richtig gut aus", 2),
  ],
  [friendUuid(2)]: [
    friendMessage(friendUuid(2), friendsMe.uuid, "hey", 30),
    friendMessage(friendUuid(2), friendsMe.uuid, "Lust auf Bedwars?", 29),
  ],
};

function friendsMock(cmd: string, a: Record<string, unknown>): unknown {
  switch (cmd) {
    case "salami_friends_state":
      return { available: true, enabled: friendsEnabled, connected: friendsEnabled, signedIn: true };
    case "salami_friends_set_enabled":
      friendsEnabled = a.enabled as boolean;
      return { available: true, enabled: friendsEnabled, connected: friendsEnabled, signedIn: true };
    case "salami_friends_reconnect":
      return null;
    case "salami_friends_overview":
      return structuredClone({ me: friendsMe, friends: friendsList, incoming: friendsIncoming, outgoing: friendsOutgoing, unread: friendsUnread });
    case "salami_friends_request":
      friendsOutgoing = [{ uuid: friendUuid(7), name: a.name as string, at: new Date().toISOString() }, ...friendsOutgoing];
      return { status: "requested", uuid: friendUuid(7), name: a.name };
    case "salami_friends_respond":
      friendsIncoming = friendsIncoming.filter((r) => r.uuid !== a.uuid);
      friendsOutgoing = friendsOutgoing.filter((r) => r.uuid !== a.uuid);
      return { status: "none" };
    case "salami_friends_messages":
      delete friendsUnread[a.with as string];
      return { messages: structuredClone(friendsMessages[a.with as string] ?? []) };
    case "salami_friends_send": {
      const message = friendMessage(friendsMe.uuid, a.to as string, a.text as string, 0);
      (friendsMessages[a.to as string] ??= []).push(message);
      return { message };
    }
    case "salami_friends_read":
      return null;
    case "salami_friends_send_file":
      throw new Error("Vorschau: Dateien lassen sich hier nicht verschicken");
    case "salami_friends_media":
      // In der Vorschau gibt es kein Asset-Protokoll: das Bild bleibt beim Ladehinweis.
      return new Promise(() => undefined);
  }
  return undefined;
}

const modrinthProjects: Record<string, { title: string; icon_url: string | null }> = {
  AANobbMI: { title: "Sodium", icon_url: null },
  "9s6osm5g": { title: "Cloth Config API", icon_url: null },
};
const curseforgeMods: Record<number, { name: string; logo: { url: string } | null }> = {
  308702: { name: "Mod Menu", logo: null },
};
const modItem = (filename: string, extra: Record<string, unknown>) => ({
  filename,
  path_str: `C:/Salami/PVP/mods/${filename}`,
  sha1_hash: null,
  file_size: 1_200_000,
  is_disabled: false,
  is_directory: false,
  content_type: "Mod",
  modrinth_info: null,
  curseforge_info: null,
  platform: null,
  source_type: null,
  norisk_info: null,
  fallback_version: null,
  id: crypto.randomUUID(),
  associated_loader: "fabric",
  modpack_origin: null,
  updates_enabled: true,
  ...extra,
});
const sampleMods = [
  modItem("sodium-fabric-0.9.3-beta.4+mc26.3.jar", {
    platform: "Modrinth",
    modrinth_info: { project_id: "AANobbMI", version_id: "2MZWfUts", name: "Sodium 0.9.3-beta.4 for Fabric 26.3", version_number: "mc26.3-0.9.3-beta.4-fabric" },
  }),
  modItem("cloth-config-fabric-26.3.159.jar", {
    platform: "Modrinth",
    modrinth_info: { project_id: "9s6osm5g", version_id: "fg2uyxOW", name: "[Fabric 26.3] v26.3.158", version_number: "26.3.159+fabric" },
  }),
  modItem("modmenu-21.0.0.jar", {
    platform: "CurseForge",
    curseforge_info: { project_id: "308702", file_id: "8958984", name: "v21.0.0 for 26.3", version_number: "v21.0.0 for 26.3" },
  }),
  modItem("my-own-mod-1.0.jar", { source_type: "custom" }),
];

mockWindows("main");
mockIPC((cmd, args) => {
  const a = (args ?? {}) as Record<string, unknown>;
  const friendsResult = friendsMock(cmd, a);
  if (friendsResult !== undefined) return friendsResult;
  switch (cmd) {
    case "salami_app_status":
      return {
        available: true,
        enabled: launcherConfig.salami_app_sync ?? true,
        linked: true,
        accountName: sampleAccount.username,
        pendingSessions: 0,
        storageFull: new URLSearchParams(location.search).has("full"),
        clipsStorageFull: new URLSearchParams(location.search).has("full"),
        downloadUrl: "https://salami-client.umpinke.de/#download",
        privacyUrl: "https://salami-client.umpinke.de/#info",
      };
    case "salami_app_pair_code": {
      const code = String(Math.floor(100000 + Math.random() * 900000));
      const minutes = new URLSearchParams(location.search).has("expiring") ? 0.1 : 10;
      return new Promise((resolve) =>
        setTimeout(
          () =>
            resolve({
              code,
              expiresAt: new Date(Date.now() + minutes * 60_000).toISOString(),
              url: `salami://pair?code=${code}`,
              qrSvg: previewQr(),
            }),
          300,
        ),
      );
    }
    case "salami_app_devices":
      appDeviceCalls += 1;
      if (appDeviceCalls === 3) {
        appDevices = [...appDevices, { id: "d_pixel", name: "Pixel 8", pairedAt: new Date().toISOString(), lastSeen: new Date().toISOString() }];
      }
      return structuredClone(appDevices);
    case "salami_app_remove_device":
      appDevices = appDevices.filter((d) => d.id !== a.id);
      return null;
    case "salami_app_delete_data":
      launcherConfig.salami_app_sync = false;
      appDevices = [];
      return new Promise((resolve) => setTimeout(() => resolve(1), 500));
    case "salami_capes_overview":
      return structuredClone(capeStore);
    case "salami_capes_select":
      capeStore.selected = (a.selection as string | null) ?? null;
      return null;
    case "salami_capes_save": {
      const id = (a.id as string | null) ?? crypto.randomUUID();
      const cape = { id, name: String(a.name), created: now, image: String(a.pngBase64), share_id: "d".repeat(64) };
      capeStore.capes = [...capeStore.capes.filter((c) => c.id !== id), cape];
      return cape;
    }
    case "salami_capes_rename":
      capeStore.capes = capeStore.capes.map((c) => (c.id === a.id ? { ...c, name: String(a.name) } : c));
      return null;
    case "salami_capes_delete":
      capeStore.capes = capeStore.capes.filter((c) => c.id !== a.id);
      return null;
    case "salami_capes_review_status":
      return Object.fromEntries((a.ids as string[]).filter((id) => capeReviews[id]).map((id) => [id, capeReviews[id]]));
    case "salami_capes_gallery":
      return structuredClone(capeGallery);
    case "salami_capes_gallery_image":
      return capeGalleryImages[a.id as string] ?? Promise.reject(new Error("nicht gefunden"));
    case "salami_capes_adopt": {
      const id = a.id as string;
      const own = capeStore.capes.find((c) => c.share_id === id);
      if (own) return own;
      const cape = { id: crypto.randomUUID(), name: String(a.name), created: now, image: capeGalleryImages[id], share_id: id };
      capeStore.capes = [...capeStore.capes, cape];
      capeReviews[id] = { status: "approved" };
      return cape;
    }
    case "salami_capes_report":
      return new Promise((resolve) => setTimeout(() => resolve("reported"), 400));
    case "salami_cosmetics_catalog":
      // wie vom Cape-Server; zum Ansehen des Hinweises „Server nicht erreichbar“ hier einen Fehler werfen
      return structuredClone(a.kind === "emotes" ? emoteCatalog : a.kind === "buddies" ? buddyCatalog : hatCatalog);
    case "salami_capes_download_template":
      return "C:\\Users\\Du\\Downloads\\salami_cape_vorlage.png";
    // ── Log-Fenster (design-preview.html?window=logs): ein laufendes Spiel mit Ausgabe ──
    case "get_processes":
      return LOG_WINDOW ? [logProcess] : [];
    case "get_process":
      return LOG_WINDOW ? logProcess : null;
    case "get_process_log_cursor": {
      const cursor = (a.cursor as number) ?? 0;
      return cursor === 0
        ? { cursor: SAMPLE_LOG.length, output: SAMPLE_LOG, new_file: true }
        : { cursor, output: "", new_file: false };
    }
    case "get_profile_log_files":
      return ["C:/Salami/PVP/logs/latest.log", "C:/Salami/PVP/logs/2026-10-13-2.log.gz", "C:/Salami/PVP/logs/debug.log"];
    case "get_log_file_content":
      return SAMPLE_LOG;
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
      return SIGNED_OUT ? null : sampleAccount;
    case "get_accounts":
      return SIGNED_OUT ? [] : [sampleAccount];
    case "is_flatpak":
      return false;
    // Profil-Einstellungen → Java & Speicher
    case "get_system_ram_mb":
      return 16384;
    case "detect_java_installations_command":
      return [
        { path: "C:/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot/bin/javaw.exe", major_version: 21, vendor: "Eclipse Adoptium", architecture: "x64" },
        { path: "C:/Program Files/Java/jdk-17/bin/javaw.exe", major_version: 17, vendor: "Oracle", architecture: "x64" },
      ];
    case "validate_java_path_command":
      return true;
    // Profil exportieren: Dateibaum
    case "get_profile_directory_structure": {
      const node = (path: string, is_dir: boolean, children: unknown[] = [], size = 0) =>
        ({ name: path.split("/").pop(), path: `C:/Salami/PVP/${path}`, is_dir, children, size, last_modified: null });
      return node("PVP", true, [
        node("config", true, [node("config/sodium-options.json", false, [], 1_204)]),
        node("mods", true, [node("mods/sodium.jar", false, [], 1_200_000), node("mods/modmenu.jar", false, [], 640_000)]),
        node("resourcepacks", true),
        node("options.txt", false, [], 5_120),
        node("servers.dat", false, [], 812),
      ]);
    }
    case "refresh_norisk_packs":
      return null;
    case "refresh_standard_versions":
      return [];
    case "get_online_count":
      return 42;
    case "get_local_content":
      // Mods im Profil: Namen kommen von Modrinth/CurseForge, die Datei hat ihren eigenen Namen
      return (a.params as { content_type?: string } | undefined)?.content_type === "Mod" ? structuredClone(sampleMods) : [];
    case "get_modrinth_project_details":
      return (a.ids as string[]).flatMap((id) => (modrinthProjects[id] ? [{ id, ...modrinthProjects[id] }] : []));
    case "get_curseforge_mods_by_ids":
      return { data: (a.modIds as number[]).flatMap((id) => (curseforgeMods[id] ? [{ id, ...curseforgeMods[id] }] : [])) };
  }
  // ── Skins und Clips ── /design-preview.html?clips=on zeigt die Clip-Seite statt der Einschalt-Seite
  if (new URLSearchParams(location.search).get("clips") === "on") launcherConfig.clips.enabled = true;
  switch (cmd) {
    case "get_all_skins":
    case "get_user_skin_data":
      return fetch("/skins/steve.png")
        .then((r) => r.blob())
        .then((blob) => new Promise<string>((done) => {
          const reader = new FileReader();
          reader.onload = () => done(String(reader.result).split(",")[1]);
          reader.readAsDataURL(blob);
        }))
        .then((steve) =>
          cmd === "get_all_skins"
            ? [
                { id: "steve", name: "Steve", base64_data: steve, variant: "classic", added_at: now },
                { id: "pvp", name: "PvP-Skin", base64_data: steve, variant: "classic", added_at: now },
                { id: "schlank", name: "Schlanker Steve", base64_data: steve, variant: "slim", added_at: now },
              ]
            : {
                id: sampleAccount.id,
                name: sampleAccount.username,
                properties: [{ name: "textures", value: btoa(JSON.stringify({ textures: { SKIN: { url: "http://textures.minecraft.net/texture/steve" } } })) }],
              },
        );
    case "capture_status":
      return {
        running: true, runtime: { state: "ready" }, state: "buffering", blocked_by_fullscreen: false, engine_version: null,
        adapter: null, available_encoders: [], capabilities: [], active_codec: null, active_encoder: null,
        audio_devices: [], microphones: [], supports_game_only_audio: false,
      };
    case "clip_list": {
      const clip = (name: string, minutesAgo: number, game: string, thumb: string, favourite = false) => ({
        path: `C:/Salami/clips/${name}.mp4`, name, sizeBytes: 38_000_000 + minutesAgo * 9000, createdAt: Math.floor(Date.now() / 1000) - minutesAgo * 60,
        durationSeconds: 30, game, thumbnail: thumb, favourite,
      });
      return [
        clip("Clutch im Bedwars", 12, "Minecraft", "/capes/magma.png", true),
        clip("Elytra-Flug", 95, "Minecraft", "/capes/aurora.png"),
        clip("Drachenkampf", 60 * 30, "Minecraft", "/capes/dragon.png"),
        clip("Galaxie-Base", 60 * 50, "Minecraft", "/capes/galaxy.png"),
        clip(`${Math.floor(Date.now() / 1000) - 60 * 60 * 26}_clip`, 60 * 26, "Minecraft", "/capes/board.png"),
      ];
    }
    case "clip_storage_usage":
      return { usedBytes: 3_400_000_000, limitBytes: 10_000_000_000, clipCount: 4 };
  }
  // ── Mod-Browser (#/mods) und Mod-Detailseite (#/mods/modrinth/<id>) ──
  {
    const icon = (bg: string, letter: string) =>
      `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="12" fill="${bg}"/><text x="32" y="44" font-family="Arial" font-weight="bold" font-size="32" fill="#fff" text-anchor="middle">${letter}</text></svg>`)}`;
    const hits: [string, string, string, string, string, number, string[], string][] = [
      ["AANobbMI0", "Sodium", "jellysquid3", "Der schnellste Rendering-Mod für Minecraft – deutlich mehr FPS ohne Grafikverlust.", "#4f9a5f", 82_400_000, ["optimization", "fabric", "neoforge"], "S"],
      ["P7dR8mSH0", "Fabric API", "modmuss50", "Kernbibliothek für die meisten Fabric-Mods.", "#b5a17a", 121_900_000, ["library", "fabric"], "F"],
      ["YL57xq9U0", "Iris Shaders", "coderbot", "Shaderpacks mit Sodium-Leistung – kompatibel mit OptiFine-Shadern.", "#6a54c4", 64_300_000, ["decoration", "optimization", "fabric"], "I"],
      ["gvQqBUqZ0", "Lithium", "jellysquid3", "Optimiert Spiellogik, Server-Ticks und Wegfindung.", "#c9a227", 38_100_000, ["optimization", "fabric"], "L"],
      ["mOgUt4GM0", "Mod Menu", "Prospector", "Fügt ein Menü hinzu, in dem du alle installierten Mods siehst und einstellst.", "#3d78c9", 59_700_000, ["utility", "fabric", "quilt"], "M"],
      ["NNAgCjsB0", "Entity Culling", "tr7zw", "Überspringt Wesen und Blockobjekte, die gerade nicht sichtbar sind.", "#8a4d2f", 31_200_000, ["optimization", "client"], "E"],
      ["1bokaNcj0", "Xaero's Minimap", "xaero96", "Minimap mit Wegpunkten, Höhlenmodus und Todespunkten.", "#2f7d8a", 27_800_000, ["adventure", "utility", "fabric"], "X"],
      ["1IjD5062a", "Continuity", "PepperCode1", "Verbundene Texturen für Glas, Bücherregale und mehr.", "#9b3a52", 18_600_000, ["decoration", "fabric"], "C"],
      ["w7ThoJFB0", "Zoomify", "isXander", "Zoomen wie mit OptiFine – mit Animationen und vielen Einstellungen.", "#5a6b2f", 12_400_000, ["utility", "fabric", "quilt"], "Z"],
      ["fQEb0iXm0", "Krypton", "astei", "Optimiert den Netzwerk-Stack von Minecraft.", "#3a3a3a", 9_100_000, ["optimization", "fabric"], "K"],
    ];
    const results = hits.map(([id, title, author, description, color, downloads, categories, letter], i) => ({
      project_id: id, source: (a.params as { source?: string } | undefined)?.source ?? "Modrinth", title,
      slug: title.toLowerCase().replace(/[^a-z]+/g, "-"), description, author, categories,
      display_categories: categories, client_side: "required", server_side: i % 2 ? "optional" : "unsupported",
      downloads, follows: Math.round(downloads / 900), icon_url: i === 9 ? null : icon(color, letter),
      project_url: `https://modrinth.com/mod/${id}`, project_type: "mod", latest_version: "0.6.13",
      date_created: "2021-01-03T00:00:00Z", date_modified: now, license: "LGPL-3.0", gallery: [], versions: null,
    }));
    const versionsFor = (projectId: string) =>
      ["0.6.13", "0.6.12", "0.6.11-beta.2", "0.6.10", "0.6.9", "0.6.8", "0.6.7"].map((v, i) => ({
        id: `${projectId}-v${i}`, project_id: projectId, source: "Modrinth",
        name: `${v} für Fabric ${i < 3 ? "1.21.11" : "1.21.4"}`, version_number: `mc1.21-${v}`,
        dependencies: i === 0 ? [{ project_id: "P7dR8mSH0", dependency_type: "required" }] : [],
        game_versions: i < 3 ? ["1.21.11", "1.21.10"] : ["1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21", "1.20.6"],
        loaders: i % 3 === 2 ? ["fabric", "quilt"] : ["fabric"],
        files: [{ filename: `mod-${v}.jar`, url: "", size: 1_048_576, hashes: { sha1: `${projectId}${i}` }, primary: true }],
        date_published: new Date(Date.now() - i * 9 * 86_400_000).toISOString(), downloads: 2_400_000 - i * 310_000,
        release_type: v.includes("beta") ? "beta" : "release", url: "",
      }));
    // Detailseite: get_modrinth_project_details liest `modrinthProjects` (oben) – hier um volle Projekte ergänzen
    for (const hit of results)
      (modrinthProjects as Record<string, unknown>)[hit.project_id] ??= {
        ...hit, id: hit.project_id, team: "team", organization: null,
        body: `## Was macht ${hit.title}?\n\n${hit.description}\n\n- Läuft mit **Fabric** und Quilt\n- Keine Einstellungen nötig\n- Funktioniert mit dem Salami Client\n\n> Tipp: Zusammen mit Lithium und Entity Culling noch schneller.\n\n### Installation\n\nEinfach im Launcher auf *Installieren* klicken.`,
        published: "2021-01-03T00:00:00Z", updated: now, approved: now, status: "approved", moderator_message: null,
        license: { id: "LGPL-3.0-only", name: "GNU Lesser General Public License v3.0", url: "https://spdx.org/licenses/LGPL-3.0-only.html" },
        followers: hit.follows, versions: [], color: null, issues_url: "https://github.com/example/issues",
        source_url: "https://github.com/example", wiki_url: null, discord_url: "https://discord.gg/example",
        donation_urls: [{ id: "ko-fi", platform: "Ko-fi", url: "https://ko-fi.com/example" }],
        gallery: [
          { url: "/welcome-background.jpg", featured: true, title: "Im Spiel", description: null, created: now, ordering: 0 },
          { url: "/themes/salami_theme.jpg", featured: false, title: "Einstellungen", description: null, created: now, ordering: 1 },
          { url: "/themes/christmas_theme.png", featured: false, title: null, description: null, created: now, ordering: 2 },
        ],
        game_versions: ["1.21.11", "1.21.10", "1.21.4", "1.21.3", "1.21.1", "1.20.6", "1.20.4", "1.20.1"],
        loaders: hit.categories.filter((c) => ["fabric", "quilt", "neoforge"].includes(c)),
      };
    const svg = (d: string) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="${d}"/></svg>`;
    switch (cmd) {
      case "search_mods_unified_command": {
        const p = a.params as { query?: string; offset?: number; project_type?: string };
        const q = (p.query ?? "").toLowerCase();
        const found = results.filter((r) => r.title.toLowerCase().includes(q) || r.description.toLowerCase().includes(q));
        const page = (p.offset ?? 0) > 0 ? [] : p.project_type === "Mod" ? found : found.slice(0, 4).map((r) => ({ ...r, project_type: String(p.project_type).toLowerCase() }));
        return { results: page, pagination: { index: 0, page_size: 20, result_count: page.length, total_count: page.length } };
      }
      case "get_modrinth_tags_command":
        return {
          categories: [
            ["adventure", "M3 12h18M12 3v18"], ["decoration", "M4 20l8-16 8 16z"], ["library", "M5 4h4v16H5zM13 4h6v16h-6z"],
            ["optimization", "M13 2L3 14h9l-1 8 10-12h-9z"], ["utility", "M14 7l3 3-8 8H6v-3z"], ["equipment", "M6 3l12 18"],
          ].flatMap(([name, d]) => [
            { name, icon: svg(d), project_type: "mod", header: "categories" },
            { name, icon: svg(d), project_type: "modpack", header: "categories" },
          ]).concat([{ name: "client", icon: svg("M4 5h16v10H4zM8 19h8"), project_type: "mod", header: "environment" }]),
          loaders: ["fabric", "forge", "neoforge", "quilt", "iris", "optifine", "minecraft"].map((name) => ({
            name, icon: svg("M12 3l9 5v8l-9 5-9-5V8z"),
            supported_project_types: name === "iris" || name === "optifine" ? ["shader"] : name === "minecraft" ? ["resourcepack", "datapack"] : ["mod", "modpack"],
          })),
          game_versions: ["1.21.11", "1.21.10", "1.21.9", "1.21.4", "25w45a", "1.21.3", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"].map((version) => ({
            version, version_type: version.includes("w") ? "snapshot" : "release", date: now, major: version.split(".").length === 2,
          })),
        };
      case "get_mod_versions_unified_command": {
        const versions = versionsFor((a.params as { project_id: string }).project_id);
        return { versions, total_count: versions.length };
      }
      case "get_modrinth_mod_versions":
        return versionsFor(a.projectIdOrSlug as string).map((v) => ({ ...v, version_type: v.release_type, featured: false, author_id: null, changelog: null }));
      case "get_modrinth_project_members":
        return [
          { team_id: "team", role: "Owner", ordering: 0, user: { id: "u1", username: "jellysquid3", avatar_url: icon("#7a4a35", "J"), bio: null, role: null } },
          { team_id: "team", role: "Mitwirkende", ordering: 1, user: { id: "u2", username: "IMS212", avatar_url: null, bio: null, role: null } },
        ];
      case "batch_check_content_installed":
        return { results: [] };
    }
  }
  // ── Profil-Detail (Welten, Server, Screenshots) und Sync Packs ──
  if (cmd === "get_worlds_for_profile")
    return [
      { folder_name: "Salami-Insel", display_name: "Salami-Insel", last_played: Date.now() - 3_600_000, icon_path: null, game_mode: 0, difficulty: 2, difficulty_locked: false, is_hardcore: false, version_name: "1.21.11" },
      { folder_name: "Theke", display_name: "Hardcore-Theke", last_played: Date.now() - 3 * 86_400_000, icon_path: null, game_mode: 0, difficulty: 3, difficulty_locked: true, is_hardcore: true, version_name: "1.21.11" },
    ];
  if (cmd === "get_servers_for_profile")
    return [{ name: "Salami-Server", address: "play.salami.gg", icon_base64: null, accepts_textures: 0, previews_chat: 0 }];
  if (cmd === "ping_minecraft_server")
    return { description: "§cSalami §fServer – frisch angeschnitten", description_json: null, version_name: "1.21.11", version_protocol: 774, players_online: 12, players_max: 100, favicon_base64: null, latency_ms: 42, error: null };
  if (cmd === "check_world_lock_status") return false;
  if (cmd === "list_profile_screenshots")
    return ["2026-11-17_18.02.11.png", "2026-11-12_21.40.03.png", "2026-11-02_15.11.54.png"].map((filename, i) => ({ filename, path: `/capes/${["salami", "board", "pepperoni"][i]}.png`, modified: new Date(Date.now() - i * 86_400_000).toISOString() }));
  if (cmd === "get_image_preview")
    return fetch((a.payload as { path: string }).path).then((r) => r.blob()).then((blob) => new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve({ base64_image: String(reader.result).split(",")[1], original_width: 64, original_height: 32, preview_width: 64, preview_height: 32 });
      reader.readAsDataURL(blob);
    }));
  if (cmd === "get_sync_packs")
    return [
      { id: "pack-standard", name: "Meine Einstellungen", description: null, icon: null, created: now, updated: now, enabled: true, sort_order: 0,
        targets: [
          { id: "t1", path: "options.txt", enabled: true, kind: { type: "file_merge", format: "minecraft_options", local_keys: [] }, external_path: null },
          { id: "t2", path: "config", enabled: true, kind: { type: "dir_link", adopt: "backup_local" }, external_path: "C:\\Users\\Du\\AppData\\Roaming\\salami\\sync_packs\\pack-standard\\config" },
        ],
        mods: [{ id: "m1", source: { type: "modrinth", project_id: "AANobbMI", version_id: "2MZWfUts", file_name: "sodium.jar", download_url: "", file_hash_sha1: null }, enabled: true, display_name: "Sodium", version: null, game_versions: null, file_name_override: null, associated_loader: null, updates_enabled: true, force_include_versions: [], version_overrides: {} }] },
      { id: "pack-pvp", name: "PvP-Welten", description: null, icon: null, created: now, updated: now, enabled: true, sort_order: 1, targets: [], mods: [] },
    ];
  if (cmd === "get_profile_sync_conflicts" || cmd === "get_sync_pack_local_jars" || cmd === "get_sync_pack_mod_matrix") return [];
  // Profil-Assistent: Versionsliste und Salami-Paket
  if (cmd === "get_minecraft_versions")
    return {
      latest: { release: "1.21.11", snapshot: "26w05a" },
      versions: ["26w05a", "1.21.11", "1.21.10", "1.21.8", "1.21.4", "1.20.6", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"].map((id) => ({
        id, type: id.includes("w") ? "snapshot" : "release", url: "", time: now, releaseTime: now,
      })),
    };
  if (cmd === "get_norisk_packs_resolved")
    return { packs: { salami: { displayName: "Salami Client", description: "Die Salami-Mods", mods: [] } }, repositories: {} };
  if (cmd.startsWith("plugin:")) return null;
  if (LIST_COMMANDS.test(cmd)) return [];
  // wie im echten Salami Client: nicht verfügbare Dienste schlagen fehl
  throw new Error(`design preview: no mock for ${cmd}`);
  // Ereignisse lassen sich per `emit` aus @tauri-apps/api/event nachstellen
}, { shouldMockEvents: true });
(window as unknown as { __TAURI_INTERNALS__: { convertFileSrc: (path: string) => string } })
  .__TAURI_INTERNALS__.convertFileSrc = (path) => path;

if (UPDATER_WINDOW) {
  await import("../updaterEntry");
  // Zustand nachstellen: das Backend schickt `updater_status`-Ereignisse
  const { emit } = await import("@tauri-apps/api/event");
  const state = new URLSearchParams(location.search).get("status") ?? "downloading";
  setTimeout(() => {
    void emit("updater_status",
      state === "error"
        ? { status: "error", message: "Update download/install failed: network timeout" }
        : state === "checking"
          ? { status: "checking", message: "Checking for stable updates..." }
          : { status: "downloading", message: "Downloading update: 24117248 bytes", progress: 62 });
  }, 400);
} else {
  await import(LOG_WINDOW ? "../minecraftLogWindowEntry" : "../main");
}
