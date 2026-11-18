/**
 * Hut-Katalog prüfen – wird von server.mjs eingebunden. Die Hüte stehen als Daten in hats.json (Würfel
 * mit Farbe), damit ein neuer Hut kein Launcher-Update braucht: Datei auf dem Server ändern, der Server
 * lädt sie neu und meldet es allen Spielen. Das Symbol im Menü zeichnet das Spiel selbst aus dem Modell.
 *
 * Ein Hut:
 *   {
 *     "id": "horns",                                   a–z, 0–9, _ (höchstens 24 Zeichen)
 *     "names": { "en_us": "Horns", "de_de": "Hörner" }, Anzeigename je Sprache (en_us ist der Rückfall)
 *     "anchor": "head",                                head (Standard): sitzt auf dem Kopf und folgt ihm ·
 *                                                      shoulder: schwebt über der Schulter
 *     "scale": 1,                                      optional: Größe (0.1–3)
 *     "glow": true,                                    optional: leuchtet auch im Dunkeln
 *     "bob": 0.5,                                      optional: schwebt so viele Pixel auf und ab (0–4)
 *     "sway": 8,                                       optional: pendelt so viele Grad hin und her (0–45)
 *     "parts": [                                       Teile: Würfel einer Farbe, gemeinsam verschoben/gedreht
 *       {
 *         "color": "#96181e",                          Farbe der Würfel (#rrggbb)
 *         "at": [2.5, -7.5, -1.5],                     optional: Drehpunkt [x, y, z]
 *         "rot": [-14, 0, 20],                         optional: Drehung um den Drehpunkt [x, y, z] in Grad
 *         "boxes": [[-1, -2.5, -1, 2, 2.5, 2]],        Würfel [x, y, z, Breite, Höhe, Tiefe] ab dem Drehpunkt
 *         "parts": [ … ]                               optional: Unterteile, hängen an diesem Teil
 *       }
 *     ]
 *   }
 *
 * Maße in Pixeln des Spielermodells. Beim Kopf (anchor head) liegt der Nullpunkt mitten im Hals: der
 * Kopf reicht von x −4 bis 4, von y −8 (Scheitel) bis 0 und von z −4 (Gesicht) bis 4 – oben ist also
 * negativ. Bei shoulder ist y = 0 die Unterkante des Hutes über der Schulter.
 *
 * Dieselben Grenzen prüft das Spiel beim Laden noch einmal (salami-core: cosmetics/Hat.java).
 */
import { CatalogError, number, object, parseId, parseList, parseNames } from "./catalog.mjs";

export const MAX_HATS = 64;
const ANCHORS = ["head", "shoulder"];
const COLOR = /^#[0-9a-fA-F]{6}$/;
const MAX_BOXES = 64;
const MAX_PARTS = 32;
const MAX_DEPTH = 3;
const MAX_PIXELS = 32;
const MAX_DEGREES = 360;

function triple(raw, min, max, what) {
  if (!Array.isArray(raw) || raw.length !== 3) throw new CatalogError(`${what} braucht [x, y, z]`);
  return raw.map((value) => number(value, min, max, what));
}

/** Prüft ein Teil samt Unterteilen; `count` zählt Teile und Würfel des ganzen Hutes mit. */
function parsePart(raw, depth, count) {
  object(raw, "Teil");
  if (depth > MAX_DEPTH) throw new CatalogError(`Teile höchstens ${MAX_DEPTH} Ebenen tief`);
  if (++count.parts > MAX_PARTS) throw new CatalogError(`mehr als ${MAX_PARTS} Teile`);
  const part = {};
  if (raw.at !== undefined) part.at = triple(raw.at, -MAX_PIXELS, MAX_PIXELS, "at");
  if (raw.rot !== undefined) part.rot = triple(raw.rot, -MAX_DEGREES, MAX_DEGREES, "rot");
  const boxes = raw.boxes ?? [];
  if (!Array.isArray(boxes)) throw new CatalogError("boxes ist keine Liste");
  if (boxes.length > 0) {
    if (typeof raw.color !== "string" || !COLOR.test(raw.color)) throw new CatalogError("color fehlt (#rrggbb)");
    part.color = raw.color.toLowerCase();
    part.boxes = boxes.map((box) => {
      if (!Array.isArray(box) || box.length !== 6) throw new CatalogError("Würfel braucht [x, y, z, Breite, Höhe, Tiefe]");
      if (++count.boxes > MAX_BOXES) throw new CatalogError(`mehr als ${MAX_BOXES} Würfel`);
      return [
        ...box.slice(0, 3).map((value) => number(value, -MAX_PIXELS, MAX_PIXELS, "Würfel-Position")),
        ...box.slice(3).map((value) => number(value, 0.05, MAX_PIXELS, "Würfel-Größe")),
      ];
    });
  }
  if (raw.parts !== undefined) {
    if (!Array.isArray(raw.parts)) throw new CatalogError("parts ist keine Liste");
    if (raw.parts.length > 0) part.parts = raw.parts.map((child) => parsePart(child, depth + 1, count));
  }
  if (!part.boxes && !part.parts) throw new CatalogError("Teil ohne Würfel und ohne Unterteile");
  return part;
}

/** Prüft einen Hut und gibt ihn bereinigt zurück (unbekannte Felder fallen weg); wirft bei Fehlern. */
export function parseHat(raw) {
  object(raw, "Hut");
  const hat = { id: parseId(raw.id), names: parseNames(raw.names) };
  if (hat.id === "none") throw new CatalogError('id "none" ist vergeben (kein Hut)');
  if (raw.anchor !== undefined && raw.anchor !== "head") {
    if (!ANCHORS.includes(raw.anchor)) throw new CatalogError("anchor unbekannt");
    hat.anchor = raw.anchor;
  }
  if (raw.scale !== undefined && raw.scale !== 1) hat.scale = number(raw.scale, 0.1, 3, "scale");
  if (raw.glow === true) hat.glow = true;
  if (raw.bob !== undefined && raw.bob !== 0) hat.bob = number(raw.bob, 0, 4, "bob");
  if (raw.sway !== undefined && raw.sway !== 0) hat.sway = number(raw.sway, 0, 45, "sway");
  if (!Array.isArray(raw.parts) || raw.parts.length === 0) throw new CatalogError("parts fehlt");
  const count = { parts: 0, boxes: 0 };
  hat.parts = raw.parts.map((part) => parsePart(part, 1, count));
  if (count.boxes === 0) throw new CatalogError("mindestens ein Würfel nötig");
  return hat;
}

/** Prüft den ganzen Katalog ({"hats":[…]}); fehlerhafte oder doppelte Einträge stehen in `skipped`. */
export function parseHatCatalog(root) {
  const { items, skipped } = parseList(root, "hats", MAX_HATS, parseHat);
  return { hats: items, skipped };
}
