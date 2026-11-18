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
 *     }
 *   }
 *
 * Dieselben Grenzen prüft das Spiel beim Laden noch einmal (salami-core: emotes/Emote.java).
 */

export const EMOTE_PARTS = ["head", "right_arm", "left_arm", "right_leg", "left_leg"];
const BASES = ["none", "current", "head"];
const EASES = ["sine", "linear"];
const ID = /^[a-z0-9_]{1,24}$/;
const LANG = /^[a-z]{2,3}_[a-z]{2,3}$/;
export const MAX_EMOTES = 64;
const MAX_KEYS = 32;
const MAX_SECONDS = 15;
const MAX_NAMES = 12;
const MAX_NAME = 24;
const MAX_DEGREES = 720;

class EmoteError extends Error {}

function number(value, min, max, what) {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new EmoteError(`${what} muss eine Zahl von ${min} bis ${max} sein`);
  }
  return Math.round(value * 1000) / 1000;
}

function parseTrack(part, raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new EmoteError(`${part}: kein Objekt`);
  const track = {};
  if (raw.base !== undefined && raw.base !== "none") {
    if (!BASES.includes(raw.base)) throw new EmoteError(`${part}: base unbekannt`);
    track.base = raw.base;
  }
  if (raw.cycle !== undefined) track.cycle = number(raw.cycle, 0.1, MAX_SECONDS, `${part}: cycle`);
  if (raw.ease !== undefined && raw.ease !== "sine") {
    if (!EASES.includes(raw.ease)) throw new EmoteError(`${part}: ease unbekannt`);
    track.ease = raw.ease;
  }
  if (!Array.isArray(raw.keys) || raw.keys.length === 0 || raw.keys.length > MAX_KEYS) {
    throw new EmoteError(`${part}: 1 bis ${MAX_KEYS} Schlüsselbilder nötig`);
  }
  let last = -1;
  track.keys = raw.keys.map((key, i) => {
    if (!key || typeof key !== "object" || !Array.isArray(key.rot) || key.rot.length !== 3) {
      throw new EmoteError(`${part}: Schlüsselbild ${i + 1} braucht t und rot [x, y, z]`);
    }
    const t = number(key.t, 0, MAX_SECONDS, `${part}: t`);
    if (t <= last) throw new EmoteError(`${part}: Zeiten müssen aufsteigen`);
    if (track.cycle !== undefined && t >= track.cycle) throw new EmoteError(`${part}: t liegt hinter cycle`);
    last = t;
    return { t, rot: key.rot.map((angle) => number(angle, -MAX_DEGREES, MAX_DEGREES, `${part}: Winkel`)) };
  });
  return track;
}

/** Prüft ein Emote und gibt es bereinigt zurück (unbekannte Felder fallen weg); wirft bei Fehlern. */
export function parseEmote(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new EmoteError("kein Objekt");
  if (typeof raw.id !== "string" || !ID.test(raw.id)) throw new EmoteError("id fehlt oder ist ungültig");
  const emote = { id: raw.id, names: {} };

  if (!raw.names || typeof raw.names !== "object" || Array.isArray(raw.names)) throw new EmoteError("names fehlt");
  const names = Object.entries(raw.names);
  if (names.length === 0 || names.length > MAX_NAMES) throw new EmoteError(`1 bis ${MAX_NAMES} Namen nötig`);
  for (const [lang, name] of names) {
    if (!LANG.test(lang)) throw new EmoteError(`Sprache ${lang} ungültig (z. B. de_de)`);
    if (typeof name !== "string" || name.trim().length === 0 || name.length > MAX_NAME || /[\u0000-\u001f§]/.test(name)) {
      throw new EmoteError(`Name für ${lang} ungültig`);
    }
    emote.names[lang] = name.trim();
  }
  if (!emote.names.en_us) throw new EmoteError("Name für en_us fehlt");

  if (raw.loop === true) emote.loop = true;
  else emote.duration = number(raw.duration, 0.2, MAX_SECONDS, "duration");
  if (raw.icon !== undefined) emote.icon = number(raw.icon, 0, MAX_SECONDS, "icon");

  if (!raw.tracks || typeof raw.tracks !== "object" || Array.isArray(raw.tracks)) throw new EmoteError("tracks fehlt");
  emote.tracks = {};
  for (const [part, track] of Object.entries(raw.tracks)) {
    if (!EMOTE_PARTS.includes(part)) throw new EmoteError(`Körperteil ${part} unbekannt`);
    emote.tracks[part] = parseTrack(part, track);
  }
  if (Object.keys(emote.tracks).length === 0) throw new EmoteError("mindestens ein Körperteil nötig");
  return emote;
}

/**
 * Prüft den ganzen Katalog ({"emotes":[…]}). Fehlerhafte oder doppelte Einträge werden übersprungen
 * und in `skipped` genannt, damit ein Tippfehler in einem Emote nicht alle anderen mitnimmt.
 */
export function parseCatalog(root) {
  const list = root && Array.isArray(root.emotes) ? root.emotes : null;
  if (!list) throw new EmoteError('"emotes" fehlt oder ist keine Liste');
  const emotes = [];
  const skipped = [];
  const seen = new Set();
  for (const raw of list) {
    const label = raw && typeof raw.id === "string" ? raw.id : "?";
    try {
      const emote = parseEmote(raw);
      if (seen.has(emote.id)) throw new EmoteError("id doppelt");
      if (emotes.length >= MAX_EMOTES) throw new EmoteError(`mehr als ${MAX_EMOTES} Emotes`);
      seen.add(emote.id);
      emotes.push(emote);
    } catch (e) {
      if (!(e instanceof EmoteError)) throw e;
      skipped.push(`${label}: ${e.message}`);
    }
  }
  return { emotes, skipped };
}
