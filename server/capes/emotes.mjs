/**
 * Emote-Katalog prüfen – wird von server.mjs eingebunden. Die Emotes stehen als Daten in emotes.json
 * (Haltungen als Schlüsselbilder), damit ein neues Emote kein Launcher-Update braucht: Datei auf dem
 * Server ändern, der Server lädt sie neu und meldet es allen Spielen.
 *
 * Ein Emote:
 *   {
 *     "id": "wave",                                   a–z, 0–9, _ (höchstens 24 Zeichen)
 *     "names": { "en_us": "Wave", "de_de": "Winken" }, Anzeigename je Sprache (en_us ist der Rückfall)
 *     "duration": 2.4,                                Sekunden (0.2–15) – oder "loop": true: läuft, bis
 *                                                     sich der Spieler bewegt (höchstens 15 s)
 *     "icon": 0.3,                                    optional: Zeitpunkt für das Symbol im Menü
 *     "tracks": {                                     je Körperteil: head, right_arm, left_arm, right_leg, left_leg
 *       "right_arm": {
 *         "base": "none",                             none: Winkel gelten direkt · current: zur normalen
 *                                                     Haltung addiert · head: zur Blickrichtung addiert
 *         "cycle": 0.57,                              optional: Schlüsselbilder wiederholen sich so oft (s);
 *                                                     ohne cycle laufen sie einmal und halten das letzte
 *         "ease": "sine",                             sine (weich, Standard) oder linear
 *         "keys": [{ "t": 0, "rot": [0, 0, 129] }, { "t": 0.285, "rot": [0, 0, 169] }]
 *       }                                             t in Sekunden (aufsteigend), rot = [x, y, z] in Grad
 *     },
 *     "props": [                                      optional: Requisiten an einem Körperteil (höchstens 4)
 *       {
 *         "on": "right_arm",                          head, body, right_arm, left_arm, right_leg, left_leg
 *         "from": 0, "to": 0.6,                       optional: nur in dieser Zeit zu sehen (Sekunden)
 *         "at": [-1, 10, 0],                          optional: Platz im Körperteil in Pixeln ab dessen Gelenk – die
 *                                                     Hand ist beim rechten Arm bei [-1, 10, 0], beim linken [1, 10, 0]
 *         "rot": [0, 0, 0], "scale": 1,               optional: gedreht (Grad), Größe (0.1–3)
 *         "parts": [ … ]                              Würfel wie bei einem Hut (hats.mjs), ab "at"
 *       }
 *     ],
 *     "throw": {                                      optional: etwas fliegt los – nur zum Ansehen, es trifft niemanden
 *       "t": 0.62,                                    Zeitpunkt des Wurfs
 *       "speed": 1, "up": 0.1, "gravity": 0.03,       optional: Tempo (Blöcke je Tick, 0.1–3), Schwung nach oben (0–1),
 *                                                     wie schnell es fällt (0–0.2)
 *       "scale": 1, "spin": 20,                       optional: Größe (0.1–3), Drehung je Tick in Grad (0–90)
 *       "impact": "snow",                             optional: none, snow oder poof – was beim Aufprall zu sehen ist
 *       "parts": [ … ]                                Würfel wie bei einem Hut, um den Nullpunkt herum
 *     },
 *     "sounds": [{ "t": 0.62, "sound": "snowball" }], optional: Geräusche (snowball, snow, bell, pop), höchstens 8
 *     "costume": {                                    optional: der Spieler wird zu einer Figur, solange das Emote läuft
 *       "scale": 1,                                   optional: Größe (0.1–3)
 *       "hop": 1, "hop_cycle": 0.5,                   optional: hüpft so viele Pixel (0–8) in diesem Takt (s)
 *       "parts": [                                    Teile wie bei einem Hut, im Raum des Spielers: Nullpunkt mitten im
 *         { "follow": "right_leg", "at": [ … ], … }   Hals, der Boden liegt bei y 24, vorn ist −z. Ein Teil der obersten
 *       ],                                            Ebene mit "follow" dreht sich um sein "at" wie dieses Körperteil
 *       "tracks": { … }                               optional: eigene Bewegungen der Figur, Format wie "tracks" oben;
 *     }                                               "follow": "head" schaut außerdem dorthin, wohin der Spieler schaut
 *   }
 *
 * Requisiten, Wurf, Geräusche und Verwandlung kennt das Spiel ab 1.8.5; ältere Versionen spielen nur die Bewegung.
 *
 * Dieselben Grenzen prüft das Spiel beim Laden noch einmal (salami-core: emotes/Emote.java).
 */

import { CatalogError, number, object, parseId, parseList, parseNames } from "./catalog.mjs";
import { parsePart, parseParts } from "./hats.mjs";

export const EMOTE_PARTS = ["head", "right_arm", "left_arm", "right_leg", "left_leg"];
/** Woran eine Requisite hängen kann: die Körperteile, die ein Emote bewegt, und der Rumpf. */
const LIMBS = ["head", "body", ...EMOTE_PARTS.slice(1)];
const IMPACTS = ["none", "snow", "poof"];
const SOUNDS = ["snowball", "snow", "bell", "pop"];
const MAX_PROPS = 4;
const MAX_SOUNDS = 8;
const MAX_PIXELS = 32;
const BASES = ["none", "current", "head"];
const EASES = ["sine", "linear"];
export const MAX_EMOTES = 64;
const MAX_KEYS = 32;
const MAX_SECONDS = 15;
const MAX_DEGREES = 720;

function parseTrack(part, raw) {
  object(raw, part);
  const track = {};
  if (raw.base !== undefined && raw.base !== "none") {
    if (!BASES.includes(raw.base)) throw new CatalogError(`${part}: base unbekannt`);
    track.base = raw.base;
  }
  if (raw.cycle !== undefined) track.cycle = number(raw.cycle, 0.1, MAX_SECONDS, `${part}: cycle`);
  if (raw.ease !== undefined && raw.ease !== "sine") {
    if (!EASES.includes(raw.ease)) throw new CatalogError(`${part}: ease unbekannt`);
    track.ease = raw.ease;
  }
  if (!Array.isArray(raw.keys) || raw.keys.length === 0 || raw.keys.length > MAX_KEYS) {
    throw new CatalogError(`${part}: 1 bis ${MAX_KEYS} Schlüsselbilder nötig`);
  }
  let last = -1;
  track.keys = raw.keys.map((key, i) => {
    if (!key || typeof key !== "object" || !Array.isArray(key.rot) || key.rot.length !== 3) {
      throw new CatalogError(`${part}: Schlüsselbild ${i + 1} braucht t und rot [x, y, z]`);
    }
    const t = number(key.t, 0, MAX_SECONDS, `${part}: t`);
    if (t <= last) throw new CatalogError(`${part}: Zeiten müssen aufsteigen`);
    if (track.cycle !== undefined && t >= track.cycle) throw new CatalogError(`${part}: t liegt hinter cycle`);
    last = t;
    return { t, rot: key.rot.map((angle) => number(angle, -MAX_DEGREES, MAX_DEGREES, `${part}: Winkel`)) };
  });
  return track;
}

/** Bewegungen je Körperteil ({"right_arm": {…}, …}). */
function parseTracks(raw, what) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new CatalogError(`${what} fehlt`);
  const tracks = {};
  for (const [part, track] of Object.entries(raw)) {
    if (!EMOTE_PARTS.includes(part)) throw new CatalogError(`Körperteil ${part} unbekannt`);
    tracks[part] = parseTrack(part, track);
  }
  return tracks;
}

function triple(raw, limit, what) {
  if (!Array.isArray(raw) || raw.length !== 3) throw new CatalogError(`${what} braucht [x, y, z]`);
  return raw.map((value) => number(value, -limit, limit, what));
}

/** Requisite: ein kleines Modell, das während des Emotes an einem Körperteil hängt. */
function parseProp(raw) {
  object(raw, "Requisite");
  if (!LIMBS.includes(raw.on)) throw new CatalogError("props: on unbekannt");
  const prop = { on: raw.on };
  if (raw.from !== undefined && raw.from !== 0) prop.from = number(raw.from, 0, MAX_SECONDS, "props: from");
  if (raw.to !== undefined) {
    prop.to = number(raw.to, 0, MAX_SECONDS, "props: to");
    if (prop.to <= (prop.from ?? 0)) throw new CatalogError("props: to muss nach from liegen");
  }
  if (raw.at !== undefined) prop.at = triple(raw.at, MAX_PIXELS, "props: at");
  if (raw.rot !== undefined) prop.rot = triple(raw.rot, 360, "props: rot");
  if (raw.scale !== undefined && raw.scale !== 1) prop.scale = number(raw.scale, 0.1, 3, "props: scale");
  prop.parts = parseParts(raw.parts);
  return prop;
}

/** Wurf: ein Modell fliegt zu einem Zeitpunkt los. Nur Optik – das Spiel lässt es an Blöcken zerplatzen. */
function parseThrow(raw) {
  object(raw, "throw");
  const thrown = { t: number(raw.t, 0, MAX_SECONDS, "throw: t") };
  if (raw.speed !== undefined && raw.speed !== 1) thrown.speed = number(raw.speed, 0.1, 3, "throw: speed");
  if (raw.up !== undefined) thrown.up = number(raw.up, 0, 1, "throw: up");
  if (raw.gravity !== undefined) thrown.gravity = number(raw.gravity, 0, 0.2, "throw: gravity");
  if (raw.scale !== undefined && raw.scale !== 1) thrown.scale = number(raw.scale, 0.1, 3, "throw: scale");
  if (raw.spin !== undefined && raw.spin !== 0) thrown.spin = number(raw.spin, 0, 90, "throw: spin");
  if (raw.impact !== undefined && raw.impact !== "none") {
    if (!IMPACTS.includes(raw.impact)) throw new CatalogError("throw: impact unbekannt");
    thrown.impact = raw.impact;
  }
  thrown.parts = parseParts(raw.parts);
  return thrown;
}

function parseSounds(raw) {
  if (!Array.isArray(raw) || raw.length > MAX_SOUNDS) throw new CatalogError(`sounds: höchstens ${MAX_SOUNDS}`);
  return raw.map((entry) => {
    object(entry, "sounds");
    if (!SOUNDS.includes(entry.sound)) throw new CatalogError("sounds: sound unbekannt");
    return { t: number(entry.t, 0, MAX_SECONDS, "sounds: t"), sound: entry.sound };
  });
}

/** Verwandlung: eine Figur aus Würfeln steht anstelle des Spielers, ihre Teile können Körperteilen folgen. */
function parseCostume(raw) {
  object(raw, "costume");
  const costume = {};
  if (raw.scale !== undefined && raw.scale !== 1) costume.scale = number(raw.scale, 0.1, 3, "costume: scale");
  if (raw.hop !== undefined && raw.hop !== 0) {
    costume.hop = number(raw.hop, 0, 8, "costume: hop");
    costume.hop_cycle = number(raw.hop_cycle ?? 0.5, 0.1, MAX_SECONDS, "costume: hop_cycle");
  }
  if (!Array.isArray(raw.parts) || raw.parts.length === 0) throw new CatalogError("costume: parts fehlt");
  const count = { parts: 0, boxes: 0 };
  costume.parts = raw.parts.map((rawPart) => {
    const part = parsePart(rawPart, 1, count);
    if (rawPart.follow !== undefined) {
      if (!EMOTE_PARTS.includes(rawPart.follow)) throw new CatalogError("costume: follow unbekannt");
      part.follow = rawPart.follow;
    }
    return part;
  });
  if (count.boxes === 0) throw new CatalogError("costume: mindestens ein Würfel nötig");
  if (raw.tracks !== undefined) costume.tracks = parseTracks(raw.tracks, "costume: tracks");
  return costume;
}

/** Prüft ein Emote und gibt es bereinigt zurück (unbekannte Felder fallen weg); wirft bei Fehlern. */
export function parseEmote(raw) {
  object(raw, "Emote");
  const emote = { id: parseId(raw.id), names: parseNames(raw.names) };

  if (raw.loop === true) emote.loop = true;
  else emote.duration = number(raw.duration, 0.2, MAX_SECONDS, "duration");
  if (raw.icon !== undefined) emote.icon = number(raw.icon, 0, MAX_SECONDS, "icon");

  emote.tracks = parseTracks(raw.tracks, "tracks");
  if (Object.keys(emote.tracks).length === 0) throw new CatalogError("mindestens ein Körperteil nötig");

  if (raw.props !== undefined) {
    if (!Array.isArray(raw.props) || raw.props.length > MAX_PROPS) throw new CatalogError(`props: höchstens ${MAX_PROPS}`);
    if (raw.props.length > 0) emote.props = raw.props.map(parseProp);
  }
  if (raw.throw !== undefined) emote.throw = parseThrow(raw.throw);
  if (raw.sounds !== undefined) {
    const sounds = parseSounds(raw.sounds);
    if (sounds.length > 0) emote.sounds = sounds;
  }
  if (raw.costume !== undefined) emote.costume = parseCostume(raw.costume);
  return emote;
}

/**
 * Prüft den ganzen Katalog ({"emotes":[…]}). Fehlerhafte oder doppelte Einträge werden übersprungen
 * und in `skipped` genannt, damit ein Tippfehler in einem Emote nicht alle anderen mitnimmt.
 */
export function parseCatalog(root) {
  const { items, skipped } = parseList(root, "emotes", MAX_EMOTES, parseEmote);
  return { emotes: items, skipped };
}
