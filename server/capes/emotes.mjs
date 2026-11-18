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

import { CatalogError, number, object, parseId, parseList, parseNames } from "./catalog.mjs";

export const EMOTE_PARTS = ["head", "right_arm", "left_arm", "right_leg", "left_leg"];
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

/** Prüft ein Emote und gibt es bereinigt zurück (unbekannte Felder fallen weg); wirft bei Fehlern. */
export function parseEmote(raw) {
  object(raw, "Emote");
  const emote = { id: parseId(raw.id), names: parseNames(raw.names) };

  if (raw.loop === true) emote.loop = true;
  else emote.duration = number(raw.duration, 0.2, MAX_SECONDS, "duration");
  if (raw.icon !== undefined) emote.icon = number(raw.icon, 0, MAX_SECONDS, "icon");

  if (!raw.tracks || typeof raw.tracks !== "object" || Array.isArray(raw.tracks)) throw new CatalogError("tracks fehlt");
  emote.tracks = {};
  for (const [part, track] of Object.entries(raw.tracks)) {
    if (!EMOTE_PARTS.includes(part)) throw new CatalogError(`Körperteil ${part} unbekannt`);
    emote.tracks[part] = parseTrack(part, track);
  }
  if (Object.keys(emote.tracks).length === 0) throw new CatalogError("mindestens ein Körperteil nötig");
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
