/**
 * Hüte und Emotes für den Shop. Beide sind Listen auf dem Cape-Server (`server/capes/hats.json`,
 * `emotes.json`) – der Launcher holt sie von dort, damit Neues ohne Update erscheint. Ist der Server
 * nicht erreichbar, gilt die Kopie, die beim Bauen in den Launcher kommt.
 *
 * Geprüft wird hier noch einmal, mit denselben Grenzen wie im Spiel (`Hat.java`, `Emote.java`):
 * ein Eintrag, der nicht passt, fällt weg, der Rest bleibt.
 */
import { invoke } from "@tauri-apps/api/core";
import bundledEmotes from "../../../server/capes/emotes.json";
import bundledHats from "../../../server/capes/hats.json";

export type Vec3 = [number, number, number];
/** Würfel in Spieler-Pixeln: x, y, z, Breite, Höhe, Tiefe. */
export type HatBox = [number, number, number, number, number, number];

/** Teil eines Hutes, wie ein Modellteil in Minecraft: erst zum Drehpunkt `at`, dann um z, y, x drehen. */
export interface HatPart {
  /** `#rrggbb`; `null` bei Teilen, die nur Unterteile halten. */
  color: string | null;
  at: Vec3 | null;
  /** Drehung in Grad. */
  rot: Vec3 | null;
  boxes: HatBox[];
  parts: HatPart[];
}

export interface CatalogHat {
  id: string;
  /** Anzeigenamen je Sprache (`en_us`, `de_de` …). */
  names: Record<string, string>;
  /** `head` sitzt auf dem Kopf, `shoulder` schwebt über der linken Schulter. */
  anchor: "head" | "shoulder";
  scale: number;
  /** Leuchtet (volle Helligkeit). */
  glow: boolean;
  /** So weit schwebt der Hut auf und ab (Pixel). */
  bob: number;
  /** So weit pendelt er (Grad). */
  sway: number;
  parts: HatPart[];
}

export const EMOTE_PARTS = ["head", "right_arm", "left_arm", "right_leg", "left_leg"] as const;
export type EmotePart = (typeof EMOTE_PARTS)[number];

/** Schlüsselbild: Zeit in Sekunden, Drehung in Radiant. */
export interface EmoteKey {
  t: number;
  rot: Vec3;
}

/**
 * Bewegung eines Körperteils. Mit `cycle > 0` wiederholen sich die Schlüsselbilder in diesem Takt,
 * sonst laufen sie einmal und halten das letzte.
 */
export interface EmoteTrack {
  part: EmotePart;
  cycle: number;
  linear: boolean;
  keys: EmoteKey[];
}

export interface CatalogEmote {
  id: string;
  names: Record<string, string>;
  /** Länge in Sekunden; bei endlosen Emotes die Zeit, nach der die Vorschau neu beginnt. */
  duration: number;
  /** Läuft im Spiel, bis sich der Spieler bewegt. */
  loop: boolean;
  /** Zeitpunkt (s), dessen Haltung das Vorschaubild zeigt. */
  iconTime: number;
  tracks: EmoteTrack[];
}

export interface CosmeticsCatalog<T> {
  items: T[];
  /** `false`: der Cape-Server war nicht erreichbar, das ist die Liste aus dem Launcher. */
  live: boolean;
}

// ------------------------------------------------------------------ prüfen

const ID = /^[a-z0-9_]{1,24}$/;
const LANGUAGE = /^[a-z]{2,3}_[a-z]{2,3}$/;
const COLOR = /^#[0-9a-fA-F]{6}$/;
const MAX_ITEMS = 64;
const MAX_NAMES = 12;
const MAX_NAME = 24;
const MAX_BOXES = 64;
const MAX_PARTS = 32;
const MAX_DEPTH = 3;
const MAX_PIXELS = 32;
const MAX_HAT_DEGREES = 360;
const MAX_KEYS = 32;
const MAX_SECONDS = 15;
const MAX_EMOTE_DEGREES = 720;
/** So lange läuft ein endloses Emote in der Vorschau, bevor es neu beginnt. */
const LOOP_PREVIEW_SECONDS = 15;

class Invalid extends Error {}

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

function number(value: unknown, min: number, max: number, what: string): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new Invalid(`${what} muss von ${min} bis ${max} sein`);
  }
  return value;
}

function triple(value: unknown, limit: number, what: string): Vec3 {
  if (!Array.isArray(value) || value.length !== 3) throw new Invalid(`${what} braucht [x, y, z]`);
  return [number(value[0], -limit, limit, what), number(value[1], -limit, limit, what), number(value[2], -limit, limit, what)];
}

function parseId(json: Record<string, unknown>): string {
  if (typeof json.id !== "string" || !ID.test(json.id)) throw new Invalid("id fehlt oder ist ungültig");
  return json.id;
}

function parseNames(json: Record<string, unknown>, id: string): Record<string, string> {
  if (!isObject(json.names)) throw new Invalid(`${id}: names fehlt`);
  const names: Record<string, string> = {};
  for (const [language, name] of Object.entries(json.names).slice(0, MAX_NAMES)) {
    if (!LANGUAGE.test(language) || typeof name !== "string") continue;
    const clean = name.trim().slice(0, MAX_NAME);
    if (clean) names[language] = clean;
  }
  if (!names.en_us) throw new Invalid(`${id}: names.en_us fehlt`);
  return names;
}

/** Liest die Liste `key`; Einträge, die nicht passen, fallen weg. */
function parseList<T extends { id: string }>(root: unknown, key: string, parseOne: (json: Record<string, unknown>) => T): T[] {
  if (!isObject(root) || !Array.isArray(root[key])) throw new Invalid(`Liste ${key} fehlt`);
  const items: T[] = [];
  const seen = new Set<string>();
  for (const raw of root[key]) {
    if (items.length >= MAX_ITEMS) break;
    try {
      if (!isObject(raw)) throw new Invalid("Eintrag ist kein Objekt");
      const item = parseOne(raw);
      if (seen.has(item.id)) throw new Invalid(`${item.id} steht doppelt in der Liste`);
      seen.add(item.id);
      items.push(item);
    } catch (error) {
      if (!(error instanceof Invalid)) throw error;
      console.warn(`[cosmetics] ${key}: Eintrag ausgelassen –`, error.message);
    }
  }
  return items;
}

function parseHat(json: Record<string, unknown>): CatalogHat {
  const id = parseId(json);
  if (id === "none") throw new Invalid('id "none" ist vergeben (kein Hut)');
  const names = parseNames(json, id);
  if (json.anchor !== undefined && json.anchor !== "head" && json.anchor !== "shoulder") {
    throw new Invalid(`${id}: anchor unbekannt`);
  }
  if (!Array.isArray(json.parts) || json.parts.length === 0) throw new Invalid(`${id}: parts fehlt`);
  const count = { parts: 0, boxes: 0 };

  const parsePart = (raw: unknown, depth: number): HatPart => {
    if (!isObject(raw)) throw new Invalid(`${id}: Teil ist kein Objekt`);
    if (depth > MAX_DEPTH) throw new Invalid(`${id}: Teile höchstens ${MAX_DEPTH} Ebenen tief`);
    if (++count.parts > MAX_PARTS) throw new Invalid(`${id}: mehr als ${MAX_PARTS} Teile`);
    const part: HatPart = {
      color: null,
      at: raw.at === undefined ? null : triple(raw.at, MAX_PIXELS, `${id}: at`),
      rot: raw.rot === undefined ? null : triple(raw.rot, MAX_HAT_DEGREES, `${id}: rot`),
      boxes: [],
      parts: [],
    };
    if (raw.boxes !== undefined) {
      if (!Array.isArray(raw.boxes)) throw new Invalid(`${id}: boxes ist keine Liste`);
      if (raw.boxes.length > 0) {
        if (typeof raw.color !== "string" || !COLOR.test(raw.color)) throw new Invalid(`${id}: color fehlt (#rrggbb)`);
        part.color = raw.color;
      }
      for (const box of raw.boxes) {
        if (!Array.isArray(box) || box.length !== 6) throw new Invalid(`${id}: Würfel braucht [x, y, z, Breite, Höhe, Tiefe]`);
        if (++count.boxes > MAX_BOXES) throw new Invalid(`${id}: mehr als ${MAX_BOXES} Würfel`);
        part.boxes.push(
          box.map((value, i) =>
            i < 3 ? number(value, -MAX_PIXELS, MAX_PIXELS, `${id}: Würfel-Position`) : number(value, 0.05, MAX_PIXELS, `${id}: Würfel-Größe`),
          ) as HatBox,
        );
      }
    }
    if (raw.parts !== undefined) {
      if (!Array.isArray(raw.parts)) throw new Invalid(`${id}: parts ist keine Liste`);
      part.parts = raw.parts.map((child) => parsePart(child, depth + 1));
    }
    if (part.boxes.length === 0 && part.parts.length === 0) throw new Invalid(`${id}: Teil ohne Würfel und ohne Unterteile`);
    return part;
  };

  const parts = json.parts.map((part) => parsePart(part, 1));
  if (count.boxes === 0) throw new Invalid(`${id}: mindestens ein Würfel nötig`);
  return {
    id,
    names,
    anchor: json.anchor === "shoulder" ? "shoulder" : "head",
    scale: json.scale === undefined ? 1 : number(json.scale, 0.1, 3, `${id}: scale`),
    glow: json.glow === true,
    bob: json.bob === undefined ? 0 : number(json.bob, 0, 4, `${id}: bob`),
    sway: json.sway === undefined ? 0 : number(json.sway, 0, 45, `${id}: sway`),
    parts,
  };
}

function parseTrack(id: string, part: EmotePart, json: unknown): EmoteTrack {
  const where = `${id}/${part}`;
  if (!isObject(json)) throw new Invalid(`${where} ist kein Objekt`);
  // „base“ (Bezug auf die laufende Haltung) spielt in der Vorschau keine Rolle: die Figur steht still
  if (json.base !== undefined && json.base !== "none" && json.base !== "current" && json.base !== "head") {
    throw new Invalid(`${where}: base unbekannt`);
  }
  if (json.ease !== undefined && json.ease !== "sine" && json.ease !== "linear") throw new Invalid(`${where}: ease unbekannt`);
  const cycle = json.cycle === undefined ? 0 : number(json.cycle, 0.1, MAX_SECONDS, `${where}: cycle`);
  if (!Array.isArray(json.keys) || json.keys.length === 0 || json.keys.length > MAX_KEYS) {
    throw new Invalid(`${where}: 1 bis ${MAX_KEYS} Schlüsselbilder nötig`);
  }
  let last = -1;
  const keys = json.keys.map((raw): EmoteKey => {
    if (!isObject(raw)) throw new Invalid(`${where}: Schlüsselbild braucht t und rot [x, y, z]`);
    const t = number(raw.t, 0, MAX_SECONDS, `${where}: t`);
    if (t <= last || (cycle > 0 && t >= cycle)) throw new Invalid(`${where}: Zeiten müssen aufsteigen und vor cycle liegen`);
    last = t;
    const degrees = triple(raw.rot, MAX_EMOTE_DEGREES, `${where}: Winkel`);
    return { t, rot: degrees.map((d) => (d * Math.PI) / 180) as Vec3 };
  });
  return { part, cycle, linear: json.ease === "linear", keys };
}

function parseEmote(json: Record<string, unknown>): CatalogEmote {
  const id = parseId(json);
  const names = parseNames(json, id);
  const loop = json.loop === true;
  const duration = loop ? LOOP_PREVIEW_SECONDS : number(json.duration, 0.2, MAX_SECONDS, `${id}: duration`);
  if (!isObject(json.tracks)) throw new Invalid(`${id}: tracks fehlt`);
  const tracks = Object.entries(json.tracks).map(([name, track]) => {
    if (!(EMOTE_PARTS as readonly string[]).includes(name)) throw new Invalid(`${id}: Körperteil ${name} unbekannt`);
    return parseTrack(id, name as EmotePart, track);
  });
  if (tracks.length === 0) throw new Invalid(`${id}: mindestens ein Körperteil nötig`);
  // ohne Angabe: das zweite Schlüsselbild der ersten bewegten Spur – meist der Ausschlag der Bewegung
  const moving = tracks.find((track) => track.keys.length > 1);
  const iconTime = json.icon === undefined ? (moving ? moving.keys[1].t : 0) : number(json.icon, 0, MAX_SECONDS, `${id}: icon`);
  return { id, names, duration, loop, iconTime, tracks };
}

/** Hüte aus einem Katalog (`{"hats": […]}`); wirft, wenn die Liste fehlt. */
export const parseHatCatalog = (root: unknown): CatalogHat[] => parseList(root, "hats", parseHat);

/** Emotes aus einem Katalog (`{"emotes": […]}`); wirft, wenn die Liste fehlt. */
export const parseEmoteCatalog = (root: unknown): CatalogEmote[] => parseList(root, "emotes", parseEmote);

// ------------------------------------------------------------------ laden

async function load<T>(kind: "hats" | "emotes", parse: (root: unknown) => T[], bundled: unknown): Promise<CosmeticsCatalog<T>> {
  try {
    const items = parse(await invoke<unknown>("salami_cosmetics_catalog", { kind }));
    if (items.length > 0) return { items, live: true };
    console.warn(`[cosmetics] Katalog ${kind} vom Cape-Server ist leer – nehme die Liste aus dem Launcher`);
  } catch (error) {
    console.warn(`[cosmetics] Katalog ${kind} nicht geladen – nehme die Liste aus dem Launcher:`, error);
  }
  return { items: parse(bundled), live: false };
}

/** Alle Hüte, vom Cape-Server oder (ohne Verbindung) aus dem Launcher. */
export const getHatCatalog = () => load("hats", parseHatCatalog, bundledHats);

/** Alle Emotes, vom Cape-Server oder (ohne Verbindung) aus dem Launcher. */
export const getEmoteCatalog = () => load("emotes", parseEmoteCatalog, bundledEmotes);

// ------------------------------------------------------------------ benutzen

/** Anzeigename in der Sprache des Launchers (`de` → `de_de`), sonst englisch. */
export function cosmeticName(item: { id: string; names: Record<string, string> }, language: string | undefined): string {
  const short = (language ?? "en").toLowerCase().split(/[-_]/)[0];
  const own = item.names[`${short}_${short}`] ?? Object.entries(item.names).find(([key]) => key.startsWith(`${short}_`))?.[1];
  return own ?? item.names.en_us ?? item.id;
}

/** Drehung einer Spur zur Zeit `t` (Sekunden seit Beginn), in Radiant – wie `Emote.Track.sample` im Spiel. */
export function sampleTrack(track: EmoteTrack, t: number): Vec3 {
  const { keys, cycle } = track;
  const n = keys.length;
  if (n === 1) return keys[0].rot;
  let from: EmoteKey;
  let to: EmoteKey;
  let span: number;
  let into: number;
  if (cycle > 0) {
    const tt = t % cycle;
    let next = 0;
    while (next < n && keys[next].t <= tt) next++;
    // next = erstes Schlüsselbild nach tt; davor liegt next-1 (oder das letzte aus dem vorigen Durchlauf)
    from = keys[(next - 1 + n) % n];
    to = keys[next % n];
    const fromT = next === 0 ? from.t - cycle : from.t;
    const toT = next === n ? to.t + cycle : to.t;
    span = toT - fromT;
    into = tt - fromT;
  } else {
    if (t <= keys[0].t) return keys[0].rot;
    if (t >= keys[n - 1].t) return keys[n - 1].rot;
    let next = 1;
    while (keys[next].t <= t) next++;
    from = keys[next - 1];
    to = keys[next];
    span = to.t - from.t;
    into = t - from.t;
  }
  let f = span <= 0 ? 1 : Math.max(0, Math.min(1, into / span));
  if (!track.linear) f = (1 - Math.cos(Math.PI * f)) / 2;
  return [
    from.rot[0] + (to.rot[0] - from.rot[0]) * f,
    from.rot[1] + (to.rot[1] - from.rot[1]) * f,
    from.rot[2] + (to.rot[2] - from.rot[2]) * f,
  ];
}
