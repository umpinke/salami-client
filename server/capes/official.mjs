/**
 * Katalog der offiziellen Capes prüfen – wird von server.mjs eingebunden. Ein offizielles Cape ist ein
 * Bild, das der Server selbst mitbringt: ein Eintrag in official-capes.json und das Bild als
 * official-capes/<id>.png daneben (Cape-Layout wie bei eigenen Capes: PNG, Breite = 2 × Höhe, 64–2048
 * Pixel breit). So braucht ein neues Cape kein Launcher-Update: Bild und Eintrag auf den Server legen,
 * der Server lädt beides von selbst und meldet es allen Spielen.
 *
 * Ein Cape:
 *   {
 *     "id": "frost",                                   a–z, 0–9, _ (höchstens 24 Zeichen) = Dateiname
 *     "names": { "en_us": "Frost", "de_de": "Frost" }  Anzeigename je Sprache (en_us ist der Rückfall)
 *   }
 *
 * Offizielle Capes gelten als freigegeben, solange sie in der Liste stehen: Jeder kann sie tragen
 * (Spiel: Cosmetics → Capes, Launcher: Galerie) und alle sehen sie sofort.
 */
import { object, parseId, parseList, parseNames } from "./catalog.mjs";

export const MAX_OFFICIAL_CAPES = 64;

export function parseOfficialCape(raw) {
  object(raw, "Cape");
  return { id: parseId(raw.id), names: parseNames(raw.names) };
}

/** Prüft die Liste ({"capes":[…]}); fehlerhafte oder doppelte Einträge stehen in `skipped`. */
export function parseOfficialCatalog(root) {
  const { items, skipped } = parseList(root, "capes", MAX_OFFICIAL_CAPES, parseOfficialCape);
  return { capes: items, skipped };
}
