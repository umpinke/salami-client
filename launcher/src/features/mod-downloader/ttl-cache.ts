/**
 * Kleiner Cache mit Ablaufzeit und Größenlimit für API-Antworten
 * (Suche, Versionslisten). Gleichzeitige Anfragen mit demselben Schlüssel
 * teilen sich ein Promise; Fehler werden nicht gecacht.
 */
export class TtlCache<T> {
  private readonly entries = new Map<string, { value: Promise<T>; expires: number }>();
  private readonly ttlMs: number;
  private readonly maxEntries: number;
  private readonly now: () => number;

  constructor(ttlMs: number, maxEntries: number, now: () => number = () => Date.now()) {
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
    this.now = now;
  }

  get(key: string, load: () => Promise<T>): Promise<T> {
    const time = this.now();
    const hit = this.entries.get(key);
    if (hit && hit.expires > time) {
      // LRU: zuletzt benutzt ans Ende
      this.entries.delete(key);
      this.entries.set(key, hit);
      return hit.value;
    }
    const value = load();
    this.entries.set(key, { value, expires: time + this.ttlMs });
    value.catch(() => {
      if (this.entries.get(key)?.value === value) this.entries.delete(key);
    });
    this.evict();
    return value;
  }

  invalidate(predicate?: (key: string) => boolean): void {
    if (!predicate) {
      this.entries.clear();
      return;
    }
    for (const key of [...this.entries.keys()]) {
      if (predicate(key)) this.entries.delete(key);
    }
  }

  get size(): number {
    return this.entries.size;
  }

  private evict(): void {
    while (this.entries.size > this.maxEntries) {
      const oldest = this.entries.keys().next().value as string;
      this.entries.delete(oldest);
    }
  }
}

/** Stabiler Schlüssel aus einem Parameter-Objekt (Reihenfolge der Felder egal). */
export function cacheKey(prefix: string, params: unknown): string {
  return `${prefix}:${stableStringify(params)}`;
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== "object") return JSON.stringify(value) ?? "undefined";
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  const keys = Object.keys(value as Record<string, unknown>)
    .filter((k) => (value as Record<string, unknown>)[k] !== undefined)
    .sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify((value as Record<string, unknown>)[k])}`).join(",")}}`;
}
