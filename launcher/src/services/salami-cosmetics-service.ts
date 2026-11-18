/**
 * Hüte, Buddys und Emotes für den Shop. Alle drei sind Listen auf dem Cape-Server
 * (`server/capes/hats.json`, `buddies.json`, `emotes.json`) – der Launcher holt sie von dort, damit
 * Neues ohne Update erscheint. Ist der Server nicht erreichbar, gilt die Kopie, die beim Bauen in den
 * Launcher kommt.
 *
 * Geprüft wird hier noch einmal, mit denselben Grenzen wie im Spiel (`BoxModel.java`, `Hat.java`,
 * `Emote.java`): ein Eintrag, der nicht passt, fällt weg, der Rest bleibt.
 */
import { invoke } from "@tauri-apps/api/core";
import bundledBuddies from "../../../server/capes/buddies.json";
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

/**
 * Ein Buddy: ein kleiner Begleiter aus Würfeln. Als Eintrag ein Hut ohne festen Platz – sein Nullpunkt
 * liegt unten in der Mitte, wo er ist (Hand, Schulter, Kopf, läuft hinterher), wählt man im Spiel.
 * `anchor` hat hier keine Bedeutung.
 */
export type CatalogBuddy = CatalogHat;

export const EMOTE_PARTS = ["head", "right_arm", "left_arm", "right_leg", "left_leg"] as const;
export type EmotePart = (typeof EMOTE_PARTS)[number];
/** Woran eine Requisite hängen kann: die Körperteile, die ein Emote bewegt, und der Rumpf. */
export const EMOTE_LIMBS = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"] as const;
export type EmoteLimb = (typeof EMOTE_LIMBS)[number];

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

/** Requisite: ein Modell, das von `from` bis `to` (Sekunden; `null` = bis zum Schluss) an einem Körperteil hängt. */
export interface EmoteProp {
  on: EmoteLimb;
  from: number;
  to: number | null;
  /** Platz im Körperteil in Pixeln ab dessen Gelenk. */
  at: Vec3;
  /** Drehung in Grad. */
  rot: Vec3;
  scale: number;
  parts: HatPart[];
}

/** Wurf: zum Zeitpunkt `t` fliegt das Modell los. Der Shop zeigt es nur in der Hand, nicht im Flug. */
export interface EmoteThrow {
  t: number;
  scale: number;
  parts: HatPart[];
}

/** Teil einer Verwandlung; mit `follow` dreht es sich um sein `at` wie dieses Körperteil. */
export interface CostumePart extends HatPart {
  follow: EmotePart | null;
}

/**
 * Verwandlung: solange das Emote läuft, steht diese Figur anstelle des Spielers. Maße im Raum des
 * Spielermodells (Nullpunkt mitten im Hals, Boden bei y 24).
 */
export interface EmoteCostume {
  scale: number;
  /** Hüpft so viele Pixel im Takt `hopCycle` (Sekunden). */
  hop: number;
  hopCycle: number;
  parts: CostumePart[];
  /** Eigene Bewegungen der Figur für die Teile mit `follow`. */
  tracks: EmoteTrack[];
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
  /** Requisiten an Körperteilen (der Schneeball in der Hand, die Glocke …). */
  props: EmoteProp[];
  thrown: EmoteThrow | null;
  costume: EmoteCostume | null;
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
const MAX_PROPS = 4;
const MAX_SOUNDS = 8;
const IMPACTS = ["none", "snow", "poof"];
const SOUNDS = ["snowball", "snow", "bell", "pop"];
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

/** Zählt Teile und Würfel eines ganzen Modells mit, damit die Grenzen für alle Teile zusammen gelten. */
interface ModelCount {
  parts: number;
  boxes: number;
}

/** Prüft ein Teil samt Unterteilen (`what` steht vorn in der Meldung). */
function parsePart(what: string, raw: unknown, depth: number, count: ModelCount): HatPart {
  if (!isObject(raw)) throw new Invalid(`${what}: Teil ist kein Objekt`);
  if (depth > MAX_DEPTH) throw new Invalid(`${what}: Teile höchstens ${MAX_DEPTH} Ebenen tief`);
  if (++count.parts > MAX_PARTS) throw new Invalid(`${what}: mehr als ${MAX_PARTS} Teile`);
  const part: HatPart = {
    color: null,
    at: raw.at === undefined ? null : triple(raw.at, MAX_PIXELS, `${what}: at`),
    rot: raw.rot === undefined ? null : triple(raw.rot, MAX_HAT_DEGREES, `${what}: rot`),
    boxes: [],
    parts: [],
  };
  if (raw.boxes !== undefined) {
    if (!Array.isArray(raw.boxes)) throw new Invalid(`${what}: boxes ist keine Liste`);
    if (raw.boxes.length > 0) {
      if (typeof raw.color !== "string" || !COLOR.test(raw.color)) throw new Invalid(`${what}: color fehlt (#rrggbb)`);
      part.color = raw.color;
    }
    for (const box of raw.boxes) {
      if (!Array.isArray(box) || box.length !== 6) throw new Invalid(`${what}: Würfel braucht [x, y, z, Breite, Höhe, Tiefe]`);
      if (++count.boxes > MAX_BOXES) throw new Invalid(`${what}: mehr als ${MAX_BOXES} Würfel`);
      part.boxes.push(
        box.map((value, i) =>
          i < 3 ? number(value, -MAX_PIXELS, MAX_PIXELS, `${what}: Würfel-Position`) : number(value, 0.05, MAX_PIXELS, `${what}: Würfel-Größe`),
        ) as HatBox,
      );
    }
  }
  if (raw.parts !== undefined) {
    if (!Array.isArray(raw.parts)) throw new Invalid(`${what}: parts ist keine Liste`);
    part.parts = raw.parts.map((child) => parsePart(what, child, depth + 1, count));
  }
  if (part.boxes.length === 0 && part.parts.length === 0) throw new Invalid(`${what}: Teil ohne Würfel und ohne Unterteile`);
  return part;
}

/** Prüft ein ganzes Modell (Liste von Teilen) mit den Grenzen eines Hutes. */
function parseParts(what: string, raw: unknown): HatPart[] {
  if (!Array.isArray(raw) || raw.length === 0) throw new Invalid(`${what}: parts fehlt`);
  const count: ModelCount = { parts: 0, boxes: 0 };
  const parts = raw.map((part) => parsePart(what, part, 1, count));
  if (count.boxes === 0) throw new Invalid(`${what}: mindestens ein Würfel nötig`);
  return parts;
}

/** Hut oder Buddy (`kind` nur für die Meldung): dasselbe Format, beim Buddy zählt `anchor` nicht. */
function parseHat(json: Record<string, unknown>, kind = "Hut"): CatalogHat {
  const id = parseId(json);
  if (id === "none") throw new Invalid(`id "none" ist vergeben (kein ${kind})`);
  const names = parseNames(json, id);
  if (json.anchor !== undefined && json.anchor !== "head" && json.anchor !== "shoulder") {
    throw new Invalid(`${id}: anchor unbekannt`);
  }
  return {
    id,
    names,
    anchor: json.anchor === "shoulder" ? "shoulder" : "head",
    scale: json.scale === undefined ? 1 : number(json.scale, 0.1, 3, `${id}: scale`),
    glow: json.glow === true,
    bob: json.bob === undefined ? 0 : number(json.bob, 0, 4, `${id}: bob`),
    sway: json.sway === undefined ? 0 : number(json.sway, 0, 45, `${id}: sway`),
    parts: parseParts(id, json.parts),
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

/** Bewegungen je Körperteil (`{"right_arm": {…}, …}`). */
function parseTracks(id: string, raw: unknown): EmoteTrack[] {
  if (!isObject(raw)) throw new Invalid(`${id}: tracks fehlt`);
  return Object.entries(raw).map(([name, track]) => {
    if (!(EMOTE_PARTS as readonly string[]).includes(name)) throw new Invalid(`${id}: Körperteil ${name} unbekannt`);
    return parseTrack(id, name as EmotePart, track);
  });
}

function parseProp(id: string, raw: unknown): EmoteProp {
  const where = `${id}: props`;
  if (!isObject(raw)) throw new Invalid(`${where}: kein Objekt`);
  if (typeof raw.on !== "string" || !(EMOTE_LIMBS as readonly string[]).includes(raw.on)) throw new Invalid(`${where}: on unbekannt`);
  const from = raw.from === undefined ? 0 : number(raw.from, 0, MAX_SECONDS, `${where}: from`);
  const to = raw.to === undefined ? null : number(raw.to, 0, MAX_SECONDS, `${where}: to`);
  if (to !== null && to <= from) throw new Invalid(`${where}: to muss nach from liegen`);
  return {
    on: raw.on as EmoteLimb,
    from,
    to,
    at: raw.at === undefined ? [0, 0, 0] : triple(raw.at, MAX_PIXELS, `${where}: at`),
    rot: raw.rot === undefined ? [0, 0, 0] : triple(raw.rot, MAX_HAT_DEGREES, `${where}: rot`),
    scale: raw.scale === undefined ? 1 : number(raw.scale, 0.1, 3, `${where}: scale`),
    parts: parseParts(where, raw.parts),
  };
}

function parseThrow(id: string, raw: unknown): EmoteThrow {
  const where = `${id}: throw`;
  if (!isObject(raw)) throw new Invalid(`${where}: kein Objekt`);
  const t = number(raw.t, 0, MAX_SECONDS, `${where}: t`);
  // Tempo, Schwung und Fallen prüfen, auch wenn der Shop nichts fliegen lässt: sonst zeigte er ein Emote, das es im Spiel nicht gibt
  if (raw.speed !== undefined) number(raw.speed, 0.1, 3, `${where}: speed`);
  if (raw.up !== undefined) number(raw.up, 0, 1, `${where}: up`);
  if (raw.gravity !== undefined) number(raw.gravity, 0, 0.2, `${where}: gravity`);
  if (raw.spin !== undefined) number(raw.spin, 0, 90, `${where}: spin`);
  if (raw.impact !== undefined && (typeof raw.impact !== "string" || !IMPACTS.includes(raw.impact))) {
    throw new Invalid(`${where}: impact unbekannt`);
  }
  return {
    t,
    scale: raw.scale === undefined ? 1 : number(raw.scale, 0.1, 3, `${where}: scale`),
    parts: parseParts(where, raw.parts),
  };
}

/** Geräusche spielt der Shop nicht ab – geprüft werden sie trotzdem, wie im Spiel. */
function checkSounds(id: string, raw: unknown): void {
  if (!Array.isArray(raw) || raw.length > MAX_SOUNDS) throw new Invalid(`${id}: sounds: höchstens ${MAX_SOUNDS}`);
  for (const entry of raw) {
    if (!isObject(entry) || typeof entry.sound !== "string" || !SOUNDS.includes(entry.sound)) {
      throw new Invalid(`${id}: sounds: sound unbekannt`);
    }
    number(entry.t, 0, MAX_SECONDS, `${id}: sounds: t`);
  }
}

function parseCostume(id: string, raw: unknown): EmoteCostume {
  const where = `${id}: costume`;
  if (!isObject(raw)) throw new Invalid(`${where}: kein Objekt`);
  if (!Array.isArray(raw.parts) || raw.parts.length === 0) throw new Invalid(`${where}: parts fehlt`);
  const count: ModelCount = { parts: 0, boxes: 0 };
  const parts = raw.parts.map((rawPart): CostumePart => {
    const part = parsePart(where, rawPart, 1, count);
    const follow = (rawPart as Record<string, unknown>).follow;
    if (follow !== undefined && (typeof follow !== "string" || !(EMOTE_PARTS as readonly string[]).includes(follow))) {
      throw new Invalid(`${where}: follow unbekannt`);
    }
    return { ...part, follow: (follow as EmotePart | undefined) ?? null };
  });
  if (count.boxes === 0) throw new Invalid(`${where}: mindestens ein Würfel nötig`);
  const hop = raw.hop === undefined ? 0 : number(raw.hop, 0, 8, `${where}: hop`);
  return {
    scale: raw.scale === undefined ? 1 : number(raw.scale, 0.1, 3, `${where}: scale`),
    hop,
    hopCycle: raw.hop_cycle === undefined ? 0.5 : number(raw.hop_cycle, 0.1, MAX_SECONDS, `${where}: hop_cycle`),
    parts,
    tracks: raw.tracks === undefined ? [] : parseTracks(where, raw.tracks),
  };
}

function parseEmote(json: Record<string, unknown>): CatalogEmote {
  const id = parseId(json);
  const names = parseNames(json, id);
  const loop = json.loop === true;
  const duration = loop ? LOOP_PREVIEW_SECONDS : number(json.duration, 0.2, MAX_SECONDS, `${id}: duration`);
  const tracks = parseTracks(id, json.tracks);
  if (tracks.length === 0) throw new Invalid(`${id}: mindestens ein Körperteil nötig`);
  // ohne Angabe: das zweite Schlüsselbild der ersten bewegten Spur – meist der Ausschlag der Bewegung
  const moving = tracks.find((track) => track.keys.length > 1);
  const iconTime = json.icon === undefined ? (moving ? moving.keys[1].t : 0) : number(json.icon, 0, MAX_SECONDS, `${id}: icon`);
  // was das Emote mitbringt (ab Spielversion 1.8.5): ein Fehler darin lässt das ganze Emote weg, wie im Spiel
  let props: EmoteProp[] = [];
  if (json.props !== undefined) {
    if (!Array.isArray(json.props) || json.props.length > MAX_PROPS) throw new Invalid(`${id}: props: höchstens ${MAX_PROPS}`);
    props = json.props.map((prop) => parseProp(id, prop));
  }
  const thrown = json.throw === undefined ? null : parseThrow(id, json.throw);
  if (json.sounds !== undefined) checkSounds(id, json.sounds);
  const costume = json.costume === undefined ? null : parseCostume(id, json.costume);
  return { id, names, duration, loop, iconTime, tracks, props, thrown, costume };
}

/** Hüte aus einem Katalog (`{"hats": […]}`); wirft, wenn die Liste fehlt. */
export const parseHatCatalog = (root: unknown): CatalogHat[] => parseList(root, "hats", (json) => parseHat(json));

/** Buddys aus einem Katalog (`{"buddies": […]}`); wirft, wenn die Liste fehlt. */
export const parseBuddyCatalog = (root: unknown): CatalogBuddy[] => parseList(root, "buddies", (json) => parseHat(json, "Buddy"));

/** Emotes aus einem Katalog (`{"emotes": […]}`); wirft, wenn die Liste fehlt. */
export const parseEmoteCatalog = (root: unknown): CatalogEmote[] => parseList(root, "emotes", parseEmote);

// ------------------------------------------------------------------ laden

async function load<T>(kind: "hats" | "buddies" | "emotes", parse: (root: unknown) => T[], bundled: unknown): Promise<CosmeticsCatalog<T>> {
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

/** Alle Buddys, vom Cape-Server oder (ohne Verbindung) aus dem Launcher. */
export const getBuddyCatalog = () => load("buddies", parseBuddyCatalog, bundledBuddies);

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
