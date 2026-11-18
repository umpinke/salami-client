/**
 * Buddy-Katalog prüfen – wird von server.mjs eingebunden. Buddys sind kleine Begleiter (Salami-Kumpel,
 * Tannenbaum, Schneemann …): farbige Würfel wie bei den Hüten, als Daten in buddies.json. Ein neuer Buddy
 * braucht deshalb kein Launcher-Update: Datei auf dem Server ändern, der Server lädt sie neu und meldet es
 * allen Spielen. Das Symbol im Menü zeichnet das Spiel selbst aus dem Modell.
 *
 * Ein Buddy hat dasselbe Format wie ein Hut (hats.mjs), nur ohne "anchor": wo er ist – an der Hand, über
 * der Schulter, auf dem Kopf oder er läuft hinterher –, wählt der Spieler wie beim Mini Me.
 *
 *   {
 *     "id": "tree_buddy",
 *     "names": { "en_us": "Christmas tree buddy", "de_de": "Tannenbaum-Kumpel" },
 *     "scale": 1,                optional: Größe (0.1–3)
 *     "glow": true,              optional: leuchtet auch im Dunkeln
 *     "bob": 0.5,                optional: schwebt so viele Pixel auf und ab (über der Schulter)
 *     "sway": 10,                optional: pendelt so viele Grad hin und her
 *     "parts": [ … ]             Teile wie bei einem Hut
 *   }
 *
 * Maße in Pixeln des Spielermodells. Der Nullpunkt liegt unten in der Mitte des Buddys (zwischen den
 * Füßen): x 0 ist die Mitte, y 0 der Boden, oben ist negativ, vorn ist −z. So steht er auf dem Boden,
 * dem Kopf oder der Schulter, ohne dass man etwas verschieben muss.
 *
 * Dieselben Grenzen prüft das Spiel beim Laden noch einmal (salami-core: cosmetics/Hat.java).
 */
import { CatalogError, object, parseId, parseList, parseNames } from "./catalog.mjs";
import { parseLook, parseParts } from "./hats.mjs";

export const MAX_BUDDIES = 64;
/** Plätze eines Buddys in salami-core (dort `BuddyModule.Place`, ohne `none`) – wie beim Mini Me. */
export const BUDDY_PLACES = new Set(["hand", "shoulder", "head", "follow"]);

/** Prüft einen Buddy und gibt ihn bereinigt zurück (unbekannte Felder fallen weg); wirft bei Fehlern. */
export function parseBuddy(raw) {
  object(raw, "Buddy");
  const buddy = { id: parseId(raw.id), names: parseNames(raw.names) };
  if (buddy.id === "none") throw new CatalogError('id "none" ist vergeben (kein Buddy)');
  parseLook(raw, buddy);
  buddy.parts = parseParts(raw.parts);
  return buddy;
}

/** Prüft den ganzen Katalog ({"buddies":[…]}); fehlerhafte oder doppelte Einträge stehen in `skipped`. */
export function parseBuddyCatalog(root) {
  const { items, skipped } = parseList(root, "buddies", MAX_BUDDIES, parseBuddy);
  return { buddies: items, skipped };
}
