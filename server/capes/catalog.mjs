/**
 * Gemeinsame Prüfungen für die Cosmetics-Kataloge (emotes.mjs, hats.mjs, official.mjs). Alle drei sind
 * Daten in einer JSON-Datei neben server.mjs, damit Neues kein Launcher-Update braucht: Datei auf dem
 * Server ändern, der Server lädt sie von selbst neu und meldet es allen Spielen.
 *
 * Jeder Eintrag hat eine `id` (a–z, 0–9, _, höchstens 24 Zeichen) und `names` – den Anzeigenamen je
 * Sprache, `en_us` ist Pflicht und der Rückfall für alle anderen Sprachen.
 */

const ID = /^[a-z0-9_]{1,24}$/;
const LANG = /^[a-z]{2,3}_[a-z]{2,3}$/;
const MAX_NAMES = 12;
const MAX_NAME = 24;

/** Ein Eintrag ist fehlerhaft und wird übersprungen (die Meldung landet im Log). */
export class CatalogError extends Error {}

/** Zahl im erlaubten Bereich, auf drei Nachkommastellen gerundet. */
export function number(value, min, max, what) {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new CatalogError(`${what} muss eine Zahl von ${min} bis ${max} sein`);
  }
  return Math.round(value * 1000) / 1000;
}

export function object(raw, what) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new CatalogError(`${what}: kein Objekt`);
  return raw;
}

export function parseId(raw) {
  if (typeof raw !== "string" || !ID.test(raw)) throw new CatalogError("id fehlt oder ist ungültig");
  return raw;
}

/** Anzeigenamen je Sprache ({"en_us":"Wave","de_de":"Winken"}), bereinigt. */
export function parseNames(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new CatalogError("names fehlt");
  const entries = Object.entries(raw);
  if (entries.length === 0 || entries.length > MAX_NAMES) throw new CatalogError(`1 bis ${MAX_NAMES} Namen nötig`);
  const names = {};
  for (const [lang, name] of entries) {
    if (!LANG.test(lang)) throw new CatalogError(`Sprache ${lang} ungültig (z. B. de_de)`);
    if (typeof name !== "string" || name.trim().length === 0 || name.length > MAX_NAME || /[\u0000-\u001f§]/.test(name)) {
      throw new CatalogError(`Name für ${lang} ungültig`);
    }
    names[lang] = name.trim();
  }
  if (!names.en_us) throw new CatalogError("Name für en_us fehlt");
  return names;
}

/**
 * Prüft eine ganze Liste ({"<key>":[…]}). Fehlerhafte oder doppelte Einträge werden übersprungen und
 * in `skipped` genannt, damit ein Tippfehler in einem Eintrag nicht alle anderen mitnimmt.
 */
export function parseList(root, key, max, parseOne) {
  const list = root && Array.isArray(root[key]) ? root[key] : null;
  if (!list) throw new CatalogError(`"${key}" fehlt oder ist keine Liste`);
  const items = [];
  const skipped = [];
  const seen = new Set();
  for (const raw of list) {
    const label = raw && typeof raw.id === "string" ? raw.id : "?";
    try {
      const item = parseOne(raw);
      if (seen.has(item.id)) throw new CatalogError("id doppelt");
      if (items.length >= max) throw new CatalogError(`mehr als ${max} Einträge`);
      seen.add(item.id);
      items.push(item);
    } catch (e) {
      if (!(e instanceof CatalogError)) throw e;
      skipped.push(`${label}: ${e.message}`);
    }
  }
  return { items, skipped };
}
