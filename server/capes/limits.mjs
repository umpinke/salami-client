/**
 * Grenzen gegen Überlastung, gemeinsam für server.mjs und friends.mjs.
 *
 * - ipBucket: Schlüssel für alle Grenzen „pro IP“ – IPv6 zählt pro /64-Netz, weil ein Anschluss
 *   meist ein ganzes /64 bekommt und sonst mit jeder Anfrage eine neue Adresse nehmen könnte.
 * - Slots: begrenzte Plätze für teure gleichzeitige Arbeit (z. B. große Anfragen im Speicher).
 */
import { isIPv4, isIPv6 } from "node:net";

/** Ausgeschriebene IPv6-Adresse als 8 Gruppen (ohne führende Nullen), eingebettetes IPv4 umgerechnet. */
function ipv6Groups(address) {
  let text = address.toLowerCase();
  const v4 = /(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(text);
  if (v4) {
    const [a, b, c, d] = v4.slice(1).map(Number);
    text = `${text.slice(0, v4.index)}${((a << 8) | b).toString(16)}:${((c << 8) | d).toString(16)}`;
  }
  const double = text.indexOf("::");
  const head = (double >= 0 ? text.slice(0, double) : text).split(":").filter(Boolean);
  const tail = double >= 0 ? text.slice(double + 2).split(":").filter(Boolean) : [];
  const fill = double >= 0 ? 8 - head.length - tail.length : 0;
  return [...head, ...Array(Math.max(0, fill)).fill("0"), ...tail].map((g) => (parseInt(g, 16) || 0).toString(16));
}

/**
 * Schlüssel für Grenzen pro Anschluss: IPv4 unverändert, IPv6 nur die ersten 64 Bit („2001:db8:1:2::/64“).
 * IPv4 in IPv6 („::ffff:1.2.3.4“, so meldet Node IPv4 auf Dual-Stack-Sockets) zählt als IPv4.
 */
export function ipBucket(ip) {
  if (typeof ip !== "string") return "unknown";
  let address = ip.trim();
  if (address.startsWith("[") && address.endsWith("]")) address = address.slice(1, -1);
  const zone = address.indexOf("%");
  if (zone >= 0) address = address.slice(0, zone);
  const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/i.exec(address);
  if (mapped && isIPv4(mapped[1])) return mapped[1];
  if (isIPv4(address)) return address;
  if (!isIPv6(address)) return address || "unknown";
  return `${ipv6Groups(address).slice(0, 4).join(":")}::/64`;
}

/**
 * Begrenzte Plätze: höchstens `max` gleichzeitig und `perKey` pro Schlüssel (IP oder Spieler).
 * acquire(key) → Freigabe-Funktion (mehrfacher Aufruf schadet nicht) oder null, wenn voll.
 */
export class Slots {
  constructor(max, perKey = Infinity) {
    this.max = max;
    this.perKey = perKey;
    this.used = 0;
    this.byKey = new Map();
  }

  acquire(key) {
    const mine = this.byKey.get(key) ?? 0;
    if (this.used >= this.max || mine >= this.perKey) return null;
    this.used += 1;
    this.byKey.set(key, mine + 1);
    let released = false;
    return () => {
      if (released) return;
      released = true;
      this.used -= 1;
      const left = (this.byKey.get(key) ?? 1) - 1;
      if (left > 0) this.byKey.set(key, left);
      else this.byKey.delete(key);
    };
  }
}
