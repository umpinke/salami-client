/**
 * Freunde und Chat für den Salami Client – wird von server.mjs eingebunden (createFriends).
 *
 * Freundesanfragen per Minecraft-Name, wer von den Freunden gerade online ist und kurze
 * Nachrichten zwischen Freunden, live über Server-Sent Events. Anmeldung wie bei den Capes:
 * Challenge holen, joinServer bei Mojang, dann POST /v1/friends/session → Token. Alle anderen
 * Anfragen schicken „Authorization: Bearer <token>“. Gespeichert wird nur der SHA-256 des
 * Tokens. Antworten, Ereignisse und Grenzen: README.md („Freunde und Chat“).
 *
 * Gespeichert in DATA_DIR/friends.json:
 *   users:       uuid → { name, lastSeen }                  wer sich je angemeldet hat, letzter Name
 *   friendships: "<a>|<b>" (sortiert) → { since }
 *   requests:    [{ from, to, at }]                         offene Anfragen, älteste zuerst
 *   messages:    "<a>|<b>" → [{ id, from, to, text, at, media? }]  älteste zuerst, höchstens 100;
 *                media = { id, kind: "image"|"clip", mime, size, name, expired? }
 *   unread:      uuid → { vonUuid: Anzahl }
 *   sessions:    sha256(token) → { uuid, expires }          expires in ms seit 1970
 * Bilder und Clips liegen als Dateien in DATA_DIR/friends-media/<id>.<Endung>.
 */
import { createHash, randomBytes } from "node:crypto";
import {
  createReadStream,
  createWriteStream,
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";

const DAY_MS = 86_400_000;
/** So lange gilt ein Token, danach meldet sich der Client neu an. */
const SESSION_TTL_MS = 30 * DAY_MS;
/** Gültige Tokens pro Spieler (Spiel, Launcher, zweiter PC …) – beim nächsten fällt das älteste weg. */
const MAX_SESSIONS = 5;
const MAX_FRIENDS = 100;
const MAX_OUTGOING = 50;
const MAX_INCOMING = 100;
/** Live-Verbindungen pro Spieler. */
const MAX_USER_STREAMS = 5;
/** Nachrichten pro Gespräch (die neuesten bleiben). */
const MAX_MESSAGES = 100;
const MAX_TEXT = 256;
/** Höchstens 8 Nachrichten in 10 Sekunden pro Spieler. */
const MESSAGE_BURST = 8;
const MESSAGE_WINDOW_MS = 10_000;
/** Nachrichten und offene Anfragen verschwinden nach 30 Tagen. */
const KEEP_MS = 30 * DAY_MS;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;
/** Steuerzeichen (C0, DEL, C1) und das Minecraft-Formatierungszeichen § */
const STRIP = /[\u0000-\u001f\u007f-\u009f§]/g;

/** Bilder und Clips im Chat: erlaubte Typen, erkannt am Content-Type und an den ersten Bytes. */
const MEDIA_TYPES = {
  "image/png": { kind: "image", ext: "png" },
  "image/jpeg": { kind: "image", ext: "jpg" },
  "image/webp": { kind: "image", ext: "webp" },
  "video/mp4": { kind: "clip", ext: "mp4" },
  "video/webm": { kind: "clip", ext: "webm" },
};
const MEDIA_ID = /^[0-9a-f]{32}$/;
const MEDIA_PATH = /^\/v1\/friends\/media\/([0-9a-f]{32})$/;
/** Höchstens 10 Bilder oder Clips in 10 Minuten pro Spieler. */
const MEDIA_BURST = 10;
const MEDIA_WINDOW_MS = 600_000;
const MAX_MEDIA_NAME = 80;
const MB = 1024 * 1024;

/** Passen die ersten Bytes zum angegebenen Typ? (PNG, JPEG, WebP, MP4, WebM) */
function looksLike(mime, head) {
  const ascii = (from, to) => head.subarray(from, to).toString("latin1");
  switch (mime) {
    case "image/png":
      return head.length >= 8 && head.readUInt32BE(0) === 0x89504e47 && head.readUInt32BE(4) === 0x0d0a1a0a;
    case "image/jpeg":
      return head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff;
    case "image/webp":
      return head.length >= 12 && ascii(0, 4) === "RIFF" && ascii(8, 12) === "WEBP";
    case "video/mp4":
      return head.length >= 8 && ascii(4, 8) === "ftyp";
    case "video/webm":
      return head.length >= 4 && head.readUInt32BE(0) === 0x1a45dfa3;
    default:
      return false;
  }
}

/** Dateiname nur zur Anzeige: ohne Pfad, Steuerzeichen und Anführungszeichen, höchstens 80 Zeichen. */
function mediaName(raw, fallback) {
  const name = (typeof raw === "string" ? raw : "")
    .replace(STRIP, "")
    .replace(/[\\/:*?"<>|]/g, "")
    .trim()
    .slice(0, MAX_MEDIA_NAME);
  return name || fallback;
}

const pairKey = (a, b) => (a < b ? `${a}|${b}` : `${b}|${a}`);
const hashToken = (token) => createHash("sha256").update(token).digest("hex");
const byName = (a, b) => {
  const x = a.name.toLowerCase();
  const y = b.name.toLowerCase();
  return x < y ? -1 : x > y ? 1 : 0;
};

/**
 * Baut die Freunde-API. Gemeinsames kommt aus server.mjs, damit Anmeldung, Fehler,
 * Ratenbegrenzung und Live-Verbindungen dort wie bei den Capes funktionieren.
 *
 * @param {object} deps
 * @param {string} deps.dataDir                Datenordner (friends.json)
 * @param {(name: string, fallback: number) => number} deps.env
 * @param {(name: unknown, serverId: unknown) => Promise<{uuid: string, name: string}>} deps.verifyPlayer
 * @param {new (status: number, message: string) => Error} deps.HttpError
 * @param {(res: object, status: number, body: object) => void} deps.send
 * @param {(req: object, limit: number) => Promise<object>} deps.readJson
 * @param {(bucket: string, ip: string, limit: number) => boolean} deps.rateLimited
 * @param {(req: object, res: object, ip: string, kind: string, onClose: () => void) => void} deps.openStream
 * @param {number} deps.apiRatePerMinute       Bucket „api“ für die Anmeldung
 * @param {number} deps.maxBody                größte JSON-Anfrage in Bytes
 * @returns {{ route: Function, sweep: Function, flush: Function }}
 */
export function createFriends({ dataDir, env, verifyPlayer, HttpError, send, readJson, rateLimited, openStream, apiRatePerMinute, maxBody }) {
  /** Anfragen pro IP und Minute an /v1/friends/* (ohne Anmeldung). */
  const FRIENDS_RATE_PER_MINUTE = env("FRIENDS_RATE_PER_MINUTE", 240);
  /** So lange nach der letzten Live-Verbindung gilt ein Spieler noch als online. */
  const OFFLINE_GRACE_MS = env("FRIENDS_OFFLINE_GRACE_MS", 10_000);
  const FILE = join(dataDir, "friends.json");
  const MEDIA_DIR = join(dataDir, "friends-media");
  const MAX_IMAGE_BYTES = env("FRIENDS_IMAGE_MB", 10) * MB;
  const MAX_CLIP_BYTES = env("FRIENDS_CLIP_MB", 50) * MB;
  /** So viel dürfen die Bilder und Clips eines Spielers zusammen belegen; die ältesten laufen sonst ab. */
  const MEDIA_QUOTA_BYTES = Math.max(env("FRIENDS_MEDIA_QUOTA_MB", 500) * MB, MAX_CLIP_BYTES);
  /** Obergrenze für alle Bilder und Clips zusammen. */
  const MEDIA_TOTAL_BYTES = env("FRIENDS_MEDIA_TOTAL_MB", 20_000) * MB;

  // ---------------------------------------------------------------- Speicher

  let data = { users: {}, friendships: {}, requests: [], messages: {}, unread: {}, sessions: {} };
  mkdirSync(dataDir, { recursive: true });
  if (existsSync(FILE)) {
    const parsed = JSON.parse(readFileSync(FILE, "utf-8"));
    data = {
      users: parsed.users ?? {},
      friendships: parsed.friendships ?? {},
      requests: Array.isArray(parsed.requests) ? parsed.requests : [],
      messages: parsed.messages ?? {},
      unread: parsed.unread ?? {},
      sessions: parsed.sessions ?? {},
    };
  }

  let saveTimer = null;
  function saveNow() {
    clearTimeout(saveTimer);
    saveTimer = null;
    const tmp = `${FILE}.tmp`;
    writeFileSync(tmp, JSON.stringify(data));
    renameSync(tmp, FILE);
  }
  function scheduleSave() {
    if (!saveTimer) saveTimer = setTimeout(saveNow, 500);
  }

  /** uuid → Set der Freunde, aus data.friendships */
  const friendIndex = new Map();
  const NOBODY = new Set();
  function indexAdd(a, b) {
    for (const [x, y] of [[a, b], [b, a]]) {
      if (!friendIndex.has(x)) friendIndex.set(x, new Set());
      friendIndex.get(x).add(y);
    }
  }
  function indexRemove(a, b) {
    for (const [x, y] of [[a, b], [b, a]]) {
      const set = friendIndex.get(x);
      set?.delete(y);
      if (set?.size === 0) friendIndex.delete(x);
    }
  }
  for (const key of Object.keys(data.friendships)) indexAdd(...key.split("|"));

  /** Nachrichten-IDs: Millisekunden-Zeitstempel in Hex, streng steigend – eindeutig und sortierbar. */
  let lastMessageId = 0;
  for (const list of Object.values(data.messages)) {
    for (const m of list) lastMessageId = Math.max(lastMessageId, parseInt(m.id, 16) || 0);
  }

  const friendsOf = (uuid) => friendIndex.get(uuid) ?? NOBODY;
  const areFriends = (a, b) => friendsOf(a).has(b);
  const nameOf = (uuid) => data.users[uuid]?.name ?? "?";
  const findRequest = (from, to) => data.requests.findIndex((r) => r.from === from && r.to === to);

  function dropRequest(from, to) {
    const index = findRequest(from, to);
    if (index < 0) return false;
    data.requests.splice(index, 1);
    return true;
  }

  function clearUnread(uuid, from) {
    const counts = data.unread[uuid];
    if (!counts || !Object.hasOwn(counts, from)) return false;
    delete counts[from];
    if (Object.keys(counts).length === 0) delete data.unread[uuid];
    return true;
  }

  /** Spieler per Name (Groß-/Kleinschreibung egal). Hatten zwei denselben Namen, gilt der zuletzt gesehene. */
  function findUser(name) {
    const wanted = name.toLowerCase();
    let found = null;
    for (const [uuid, user] of Object.entries(data.users)) {
      if (user.name.toLowerCase() === wanted && (!found || user.lastSeen > data.users[found].lastSeen)) found = uuid;
    }
    return found;
  }

  function uuidParam(raw, field) {
    const uuid = typeof raw === "string" ? raw.trim().toLowerCase() : "";
    if (!UUID.test(uuid)) throw new HttpError(400, `${field} fehlt oder ungültig`);
    return uuid;
  }

  // ---------------------------------------------------------------- Live-Verbindungen und Online-Status

  /** uuid → Set offener Live-Verbindungen (res) */
  const live = new Map();
  /** Wer für Freunde als online gilt – auch noch in der Gnadenfrist nach der letzten Verbindung. */
  const online = new Set();
  /** uuid → Timer, der „offline“ meldet, wenn bis dahin keine neue Verbindung kommt */
  const leaving = new Map();
  /** Verbindung → "game" (salami-core) oder "launcher" */
  const clientOf = new WeakMap();
  /** uuid → "game" | "launcher": was Freunde zuletzt gesehen haben, solange der Spieler online ist */
  const shown = new Map();

  /** Im Spiel schlägt Launcher: wer beides offen hat, gilt als „im Spiel“. */
  function whereIs(uuid) {
    let where = null;
    for (const res of live.get(uuid) ?? []) {
      if (clientOf.get(res) === "game") return "game";
      where = "launcher";
    }
    return where;
  }
  const statusOf = (uuid) => (online.has(uuid) ? (shown.get(uuid) ?? "game") : null);

  /** Schickt allen offenen Live-Verbindungen dieses Spielers ein Ereignis. */
  function emit(uuid, event) {
    const set = live.get(uuid);
    if (!set) return;
    const line = `data: ${JSON.stringify(event)}\n\n`;
    for (const res of set) res.write(line);
  }

  /** Online-Status an alle Freunde, die gerade verbunden sind – sonst sieht ihn niemand. */
  function announce(uuid, isOnline) {
    const event = { type: "presence", uuid, name: nameOf(uuid), online: isOnline, status: statusOf(uuid) };
    for (const friend of friendsOf(uuid)) emit(friend, event);
  }

  function openEvents(req, res, ip, me, url) {
    const set = live.get(me.uuid) ?? new Set();
    if (set.size >= MAX_USER_STREAMS) throw new HttpError(503, "zu viele Verbindungen");
    openStream(req, res, ip, "friends", () => closed(me.uuid, res));
    // ?client=launcher kommt vom Launcher; alles andere ist das Spiel (auch ältere Versionen ohne Angabe)
    clientOf.set(res, url.searchParams.get("client") === "launcher" ? "launcher" : "game");
    set.add(res);
    live.set(me.uuid, set);
    res.write(`data: ${JSON.stringify({ type: "ready" })}\n\n`);
    const timer = leaving.get(me.uuid);
    if (timer) {
      // schnell wieder da: Freunde haben nichts vom Abbruch gemerkt
      clearTimeout(timer);
      leaving.delete(me.uuid);
    }
    const where = whereIs(me.uuid);
    if (!online.has(me.uuid)) {
      online.add(me.uuid);
      shown.set(me.uuid, where);
      announce(me.uuid, true);
    } else if (shown.get(me.uuid) !== where) {
      // z. B. Launcher war offen, jetzt startet das Spiel
      shown.set(me.uuid, where);
      announce(me.uuid, true);
    }
  }

  function closed(uuid, res) {
    const set = live.get(uuid);
    if (!set?.delete(res)) return;
    if (set.size > 0) {
      const where = whereIs(uuid);
      if (shown.get(uuid) !== where) {
        // z. B. Spiel beendet, Launcher noch offen
        shown.set(uuid, where);
        announce(uuid, true);
      }
      return;
    }
    live.delete(uuid);
    const timer = setTimeout(() => {
      leaving.delete(uuid);
      if (live.has(uuid)) return;
      online.delete(uuid);
      shown.delete(uuid);
      announce(uuid, false);
    }, OFFLINE_GRACE_MS);
    timer.unref();
    leaving.set(uuid, timer);
  }

  // ---------------------------------------------------------------- Anmeldung

  async function createSession(req, res) {
    const body = await readJson(req, maxBody);
    const { uuid, name } = await verifyPlayer(body.name, body.serverId);
    const token = randomBytes(32).toString("base64url");
    const now = Date.now();
    data.sessions[hashToken(token)] = { uuid, expires: now + SESSION_TTL_MS };
    // älteste zuerst (stabil sortiert: bei Gleichstand die zuerst angelegte)
    const mine = Object.entries(data.sessions)
      .filter(([, session]) => session.uuid === uuid)
      .sort((a, b) => a[1].expires - b[1].expires);
    for (const [hash] of mine.slice(0, Math.max(0, mine.length - MAX_SESSIONS))) delete data.sessions[hash];
    data.users[uuid] = { name, lastSeen: new Date(now).toISOString() };
    scheduleSave();
    send(res, 200, { token, uuid, name });
  }

  /** → { uuid, name } oder 401 */
  function authenticate(req) {
    const header = req.headers.authorization ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    const hash = token ? hashToken(token) : "";
    const session = hash && Object.hasOwn(data.sessions, hash) ? data.sessions[hash] : null;
    if (!session || session.expires <= Date.now() || !data.users[session.uuid]) {
      throw new HttpError(401, "nicht angemeldet");
    }
    return { uuid: session.uuid, name: data.users[session.uuid].name };
  }

  // ---------------------------------------------------------------- Freundschaften

  function overview(me) {
    const friends = [...friendsOf(me.uuid)]
      .map((uuid) => ({
        uuid,
        name: nameOf(uuid),
        online: online.has(uuid),
        status: statusOf(uuid),
        since: data.friendships[pairKey(me.uuid, uuid)].since,
      }))
      .sort((a, b) => Number(b.online) - Number(a.online) || byName(a, b));
    const incoming = [];
    const outgoing = [];
    for (let i = data.requests.length - 1; i >= 0; i--) {
      const r = data.requests[i]; // neueste zuerst
      if (r.to === me.uuid) incoming.push({ uuid: r.from, name: nameOf(r.from), at: r.at });
      else if (r.from === me.uuid) outgoing.push({ uuid: r.to, name: nameOf(r.to), at: r.at });
    }
    const unread = {};
    for (const [from, count] of Object.entries(data.unread[me.uuid] ?? {})) {
      if (count > 0 && areFriends(me.uuid, from)) unread[from] = count;
    }
    return { me: { uuid: me.uuid, name: me.name }, friends, incoming, outgoing, unread };
  }

  /** Macht zwei Spieler zu Freunden und sagt es beiden. */
  function befriend(me, other) {
    if (friendsOf(me).size >= MAX_FRIENDS) throw new HttpError(409, "Freundesliste ist voll");
    if (friendsOf(other).size >= MAX_FRIENDS) throw new HttpError(409, `Freundesliste von ${nameOf(other)} ist voll`);
    dropRequest(me, other);
    dropRequest(other, me);
    data.friendships[pairKey(me, other)] = { since: new Date().toISOString() };
    indexAdd(me, other);
    scheduleSave();
    emit(me, { type: "friends", uuid: other, name: nameOf(other), online: online.has(other), status: statusOf(other) });
    emit(other, { type: "friends", uuid: me, name: nameOf(me), online: online.has(me), status: statusOf(me) });
    return { status: "friends", uuid: other, name: nameOf(other) };
  }

  async function handleRequest(req, res, me) {
    const body = await readJson(req, maxBody);
    const name = typeof body.name === "string" ? body.name.trim() : "";
    if (!PLAYER_NAME.test(name)) throw new HttpError(400, "name fehlt oder ungültig");
    const other = findUser(name);
    if (!other) throw new HttpError(404, `${name} nutzt den Salami Client nicht oder war noch nie online`);
    if (other === me.uuid) throw new HttpError(400, "du kannst dich nicht selbst als Freund hinzufügen");
    if (areFriends(me.uuid, other)) throw new HttpError(409, `du bist schon mit ${nameOf(other)} befreundet`);
    const requested = { status: "requested", uuid: other, name: nameOf(other) };
    if (findRequest(me.uuid, other) >= 0) return send(res, 200, requested);
    // Hat der andere mich schon angefragt, sind wir jetzt Freunde.
    if (findRequest(other, me.uuid) >= 0) return send(res, 200, befriend(me.uuid, other));

    if (friendsOf(me.uuid).size >= MAX_FRIENDS) throw new HttpError(409, "Freundesliste ist voll");
    if (friendsOf(other).size >= MAX_FRIENDS) throw new HttpError(409, `Freundesliste von ${nameOf(other)} ist voll`);
    let outgoing = 0;
    let incoming = 0;
    for (const r of data.requests) {
      if (r.from === me.uuid) outgoing++;
      if (r.to === other) incoming++;
    }
    if (outgoing >= MAX_OUTGOING) throw new HttpError(429, "zu viele offene Freundesanfragen");
    if (incoming >= MAX_INCOMING) throw new HttpError(429, `${nameOf(other)} hat zu viele offene Freundesanfragen`);

    data.requests.push({ from: me.uuid, to: other, at: new Date().toISOString() });
    scheduleSave();
    emit(other, { type: "request", uuid: me.uuid, name: me.name });
    send(res, 200, requested);
  }

  async function handleAccept(req, res, me) {
    const other = uuidParam((await readJson(req, maxBody)).uuid, "uuid");
    // doppelt angenommen (zweiter Client): einfach bestätigen
    if (areFriends(me.uuid, other)) return send(res, 200, { status: "friends", uuid: other, name: nameOf(other) });
    if (findRequest(other, me.uuid) < 0) throw new HttpError(404, "keine offene Anfrage von diesem Spieler");
    send(res, 200, befriend(me.uuid, other));
  }

  /** Anfrage ablehnen (an mich) oder zurückziehen (von mir) – egal welche es gibt. */
  async function handleDeny(req, res, me) {
    const other = uuidParam((await readJson(req, maxBody)).uuid, "uuid");
    const denied = dropRequest(other, me.uuid);
    const cancelled = dropRequest(me.uuid, other);
    if (denied || cancelled) {
      scheduleSave();
      emit(other, { type: "removed", uuid: me.uuid });
    }
    // Eine bestehende Freundschaft beendet nur /remove.
    if (areFriends(me.uuid, other)) return send(res, 200, { status: "friends", uuid: other, name: nameOf(other) });
    send(res, 200, { status: "none" });
  }

  /** Freundschaft beenden: das Gespräch und die Ungelesen-Zähler verschwinden für beide. */
  async function handleRemove(req, res, me) {
    const other = uuidParam((await readJson(req, maxBody)).uuid, "uuid");
    let changed = false;
    const key = pairKey(me.uuid, other);
    if (Object.hasOwn(data.friendships, key)) {
      delete data.friendships[key];
      indexRemove(me.uuid, other);
      changed = true;
    }
    dropMedia(data.messages[key] ?? []);
    delete data.messages[key];
    changed = clearUnread(me.uuid, other) || changed;
    changed = clearUnread(other, me.uuid) || changed;
    changed = dropRequest(other, me.uuid) || changed;
    changed = dropRequest(me.uuid, other) || changed;
    if (changed) {
      scheduleSave();
      emit(other, { type: "removed", uuid: me.uuid });
    }
    send(res, 200, { status: "none" });
  }

  // ---------------------------------------------------------------- Nachrichten

  /** uuid → Zeitpunkte der letzten Nachrichten (für 8 in 10 s) */
  const sent = new Map();

  function handleHistory(res, me, url) {
    const other = uuidParam(url.searchParams.get("with"), "with");
    if (!areFriends(me.uuid, other)) throw new HttpError(403, "ihr seid keine Freunde");
    const messages = data.messages[pairKey(me.uuid, other)] ?? [];
    if (clearUnread(me.uuid, other)) scheduleSave();
    send(res, 200, { messages: messages.slice(-MAX_MESSAGES) });
  }

  async function handleSend(req, res, me) {
    const body = await readJson(req, maxBody);
    const other = uuidParam(body.to, "to");
    if (!areFriends(me.uuid, other)) throw new HttpError(403, "ihr seid keine Freunde");
    if (typeof body.text !== "string") throw new HttpError(400, "text fehlt");
    const text = body.text.replace(STRIP, "").trim();
    if (!text) throw new HttpError(400, "Nachricht ist leer");
    if (text.length > MAX_TEXT) throw new HttpError(400, `Nachricht ist zu lang (höchstens ${MAX_TEXT} Zeichen)`);

    const now = Date.now();
    const recent = (sent.get(me.uuid) ?? []).filter((t) => now - t < MESSAGE_WINDOW_MS);
    sent.set(me.uuid, recent);
    if (recent.length >= MESSAGE_BURST) throw new HttpError(429, "zu schnell, kurz warten");
    recent.push(now);

    send(res, 200, { message: addMessage(me, other, text) });
  }

  /** Legt eine Nachricht an (Text, optional mit Anhang), zählt sie als ungelesen und meldet sie beiden. */
  function addMessage(me, other, text, extra = {}) {
    const now = Date.now();
    lastMessageId = Math.max(lastMessageId + 1, now);
    const message = {
      id: lastMessageId.toString(16).padStart(12, "0"),
      from: me.uuid,
      to: other,
      text,
      at: new Date(now).toISOString(),
      ...extra,
    };
    const key = pairKey(me.uuid, other);
    const list = (data.messages[key] ??= []);
    list.push(message);
    if (list.length > MAX_MESSAGES) dropMedia(list.splice(0, list.length - MAX_MESSAGES));
    const counts = (data.unread[other] ??= {});
    counts[me.uuid] = Math.min((counts[me.uuid] ?? 0) + 1, MAX_MESSAGES);
    scheduleSave();

    // an den Empfänger und an alle Verbindungen des Absenders (zweiter Client sieht's auch)
    const event = { type: "message", message, name: me.name };
    emit(other, event);
    emit(me.uuid, event);
    return message;
  }

  async function handleRead(req, res, me) {
    const other = uuidParam((await readJson(req, maxBody)).with, "with");
    if (clearUnread(me.uuid, other)) scheduleSave();
    send(res, 200, { ok: true });
  }

  // ---------------------------------------------------------------- Bilder und Clips

  /** id → { from, to, size, mime, file, message } für alle Dateien, die noch da sind */
  const media = new Map();
  let mediaBytes = 0;
  mkdirSync(MEDIA_DIR, { recursive: true });
  for (const list of Object.values(data.messages)) {
    for (const message of list) {
      const m = message.media;
      if (!m || m.expired) continue;
      const type = MEDIA_TYPES[m.mime];
      const file = type && MEDIA_ID.test(m.id) ? join(MEDIA_DIR, `${m.id}.${type.ext}`) : null;
      if (file && existsSync(file)) {
        media.set(m.id, { from: message.from, to: message.to, size: m.size, mime: m.mime, file, message });
        mediaBytes += m.size;
      } else {
        m.expired = true; // Datei fehlt (z. B. Datenordner ohne Medien kopiert)
      }
    }
  }
  // Dateien ohne Nachricht (abgebrochene Uploads, Absturz zwischen Löschen und Speichern)
  for (const name of readdirSync(MEDIA_DIR)) {
    if (!media.has(name.split(".")[0])) rmSync(join(MEDIA_DIR, name), { force: true });
  }

  /** Löscht die Datei einer Nachricht; die Nachricht selbst bleibt und zeigt „abgelaufen“. */
  function expireMedia(message) {
    const m = message.media;
    if (!m || m.expired) return;
    const entry = media.get(m.id);
    if (entry) {
      rmSync(entry.file, { force: true });
      media.delete(m.id);
      mediaBytes -= entry.size;
    }
    m.expired = true;
  }

  /** Für Nachrichten, die ganz verschwinden: ihre Dateien gleich mit löschen. */
  function dropMedia(messages) {
    for (const message of messages) expireMedia(message);
  }

  /** Nimmt die Datei entgegen und schreibt sie direkt auf die Platte (nicht in den Speicher). */
  function receive(req, file, limit) {
    return new Promise((resolve, reject) => {
      const out = createWriteStream(file, { flags: "wx" });
      let size = 0;
      let head = Buffer.alloc(0);
      let settled = false;
      const fail = (error) => {
        if (settled) return;
        settled = true;
        out.once("close", () => rmSync(file, { force: true }));
        out.destroy();
        reject(error);
      };
      req.on("data", (chunk) => {
        if (settled) return;
        size += chunk.length;
        if (size > limit) {
          fail(new HttpError(413, "Datei ist zu groß"));
          req.resume();
          return;
        }
        if (head.length < 16) head = Buffer.concat([head, chunk]).subarray(0, 16);
        if (!out.write(chunk)) {
          req.pause();
          out.once("drain", () => req.resume());
        }
      });
      req.on("end", () => {
        if (settled) return;
        out.end(() => {
          settled = true;
          resolve({ size, head });
        });
      });
      req.on("error", () => fail(new HttpError(400, "Anfrage abgebrochen")));
      req.on("close", () => {
        if (!req.complete) fail(new HttpError(400, "Anfrage abgebrochen"));
      });
      out.on("error", () => fail(new HttpError(500, "Datei konnte nicht gespeichert werden")));
    });
  }

  /** uuid → Zeitpunkte der letzten Uploads (für 10 in 10 min) */
  const uploaded = new Map();

  /** POST /v1/friends/media?to=<uuid>&name=<Dateiname>, Körper = die Datei, Content-Type = ihr Typ */
  async function handleUpload(req, res, me, url) {
    // Erst alles prüfen, was ohne die Datei geht; bei einem Fehler den Körper verwerfen.
    let other;
    let type;
    let mime;
    let limit;
    try {
      other = uuidParam(url.searchParams.get("to"), "to");
      if (!areFriends(me.uuid, other)) throw new HttpError(403, "ihr seid keine Freunde");
      mime = String(req.headers["content-type"] ?? "").split(";")[0].trim().toLowerCase();
      type = Object.hasOwn(MEDIA_TYPES, mime) ? MEDIA_TYPES[mime] : null;
      if (!type) throw new HttpError(415, "nur PNG, JPEG, WebP, MP4 und WebM");
      limit = type.kind === "image" ? MAX_IMAGE_BYTES : MAX_CLIP_BYTES;
      const declared = Number(req.headers["content-length"]);
      if (!Number.isInteger(declared) || declared <= 0) throw new HttpError(411, "Content-Length fehlt");
      if (declared > limit) {
        throw new HttpError(413, `${type.kind === "image" ? "Bild" : "Clip"} ist zu groß (höchstens ${Math.floor(limit / MB)} MB)`);
      }
      if (mediaBytes + declared > MEDIA_TOTAL_BYTES) throw new HttpError(507, "der Speicher für Bilder und Clips ist voll");
      const now = Date.now();
      const recent = (uploaded.get(me.uuid) ?? []).filter((t) => now - t < MEDIA_WINDOW_MS);
      uploaded.set(me.uuid, recent);
      if (recent.length >= MEDIA_BURST) throw new HttpError(429, "zu viele Bilder und Clips, kurz warten");
      recent.push(now);
    } catch (e) {
      req.resume();
      throw e;
    }

    const id = randomBytes(16).toString("hex");
    const part = join(MEDIA_DIR, `${id}.part`);
    const { size, head } = await receive(req, part, limit);
    const reject = (status, message) => {
      rmSync(part, { force: true });
      return new HttpError(status, message);
    };
    if (size === 0 || !looksLike(mime, head)) throw reject(400, "die Datei passt nicht zum angegebenen Typ");
    // Während des Hochladens kann die Freundschaft geendet haben.
    if (!areFriends(me.uuid, other)) throw reject(403, "ihr seid keine Freunde");

    // Eigenes Kontingent: die ältesten eigenen Dateien machen Platz (ihre Nachrichten zeigen „abgelaufen“).
    const mine = [...media.values()].filter((entry) => entry.from === me.uuid).sort((a, b) => (a.message.id < b.message.id ? -1 : 1));
    let used = mine.reduce((sum, entry) => sum + entry.size, 0);
    for (const entry of mine) {
      if (used + size <= MEDIA_QUOTA_BYTES) break;
      used -= entry.size;
      expireMedia(entry.message);
    }

    const file = join(MEDIA_DIR, `${id}.${type.ext}`);
    renameSync(part, file);
    const name = mediaName(url.searchParams.get("name"), `${type.kind === "image" ? "bild" : "clip"}.${type.ext}`);
    // Der Text ist für Clients, die keine Anhänge kennen (ältere salami-core-Versionen).
    const message = addMessage(me, other, `${type.kind === "image" ? "[Bild]" : "[Clip]"} ${name}`, {
      media: { id, kind: type.kind, mime, size, name },
    });
    media.set(id, { from: me.uuid, to: other, size, mime, file, message });
    mediaBytes += size;
    send(res, 200, { message });
  }

  /** GET /v1/friends/media/<id>: nur für die beiden, zwischen denen die Datei verschickt wurde. */
  function handleDownload(res, me, id) {
    const entry = media.get(id);
    if (!entry || (entry.from !== me.uuid && entry.to !== me.uuid)) throw new HttpError(404, "nicht gefunden");
    res.writeHead(200, {
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
      "Content-Type": entry.mime,
      "Content-Length": entry.size,
      "Content-Disposition": `attachment; filename="${id}.${MEDIA_TYPES[entry.mime].ext}"`,
      "Cache-Control": "private, max-age=86400",
    });
    createReadStream(entry.file)
      .on("error", () => res.destroy())
      .pipe(res);
  }

  // ---------------------------------------------------------------- Aufräumen

  /** Abgelaufene Tokens, Nachrichten und Anfragen älter als 30 Tage. */
  function sweep(now = Date.now()) {
    let changed = false;
    for (const [hash, session] of Object.entries(data.sessions)) {
      if (session.expires <= now) {
        delete data.sessions[hash];
        changed = true;
      }
    }
    const cutoff = new Date(now - KEEP_MS).toISOString();
    const requests = data.requests.filter((r) => r.at >= cutoff);
    if (requests.length !== data.requests.length) {
      data.requests = requests;
      changed = true;
    }
    for (const [key, list] of Object.entries(data.messages)) {
      const keep = list.findIndex((m) => m.at >= cutoff); // älteste zuerst
      if (keep === 0) continue;
      changed = true;
      if (keep < 0) {
        dropMedia(list);
        delete data.messages[key];
      } else {
        dropMedia(list.splice(0, keep));
      }
      // ungelesen höchstens so viele, wie noch da sind
      const [a, b] = key.split("|");
      for (const [uuid, from] of [[a, b], [b, a]]) {
        const count = data.unread[uuid]?.[from];
        if (!count) continue;
        const left = keep < 0 ? 0 : list.filter((m) => m.from === from).length;
        if (left === 0) clearUnread(uuid, from);
        else if (count > left) data.unread[uuid][from] = left;
      }
    }
    for (const [uuid, times] of sent) if (!times.some((t) => now - t < MESSAGE_WINDOW_MS)) sent.delete(uuid);
    for (const [uuid, times] of uploaded) if (!times.some((t) => now - t < MEDIA_WINDOW_MS)) uploaded.delete(uuid);
    if (changed) scheduleSave();
  }
  sweep(); // nach längerer Pause gleich aufräumen

  // ---------------------------------------------------------------- Routing

  /** Handler bekommen (req, res, me, url, ip); me = { uuid, name } des angemeldeten Spielers. */
  const routes = new Map([
    ["GET /v1/friends", (req, res, me) => send(res, 200, overview(me))],
    ["POST /v1/friends/request", handleRequest],
    ["POST /v1/friends/accept", handleAccept],
    ["POST /v1/friends/deny", handleDeny],
    ["POST /v1/friends/remove", handleRemove],
    ["GET /v1/friends/messages", (req, res, me, url) => handleHistory(res, me, url)],
    ["POST /v1/friends/messages", handleSend],
    ["POST /v1/friends/read", handleRead],
    ["POST /v1/friends/media", handleUpload],
    ["GET /v1/friends/events", (req, res, me, url, ip) => openEvents(req, res, ip, me, url)],
  ]);

  /** Alles unter /v1/friends. */
  async function route(req, res, url, ip) {
    if (req.method === "POST" && url.pathname === "/v1/friends/session") {
      if (rateLimited("api", ip, apiRatePerMinute)) throw new HttpError(429, "zu viele Anfragen");
      return createSession(req, res);
    }
    if (rateLimited("friends", ip, FRIENDS_RATE_PER_MINUTE)) throw new HttpError(429, "zu viele Anfragen");
    const download = req.method === "GET" ? MEDIA_PATH.exec(url.pathname) : null;
    if (download) return handleDownload(res, authenticate(req), download[1]);
    const handler = routes.get(`${req.method} ${url.pathname}`);
    if (!handler) return send(res, 404, { error: "nicht gefunden" });
    return handler(req, res, authenticate(req), url, ip);
  }

  /** Beim Beenden: ausstehendes Speichern sofort erledigen. */
  function flush() {
    if (saveTimer) saveNow();
  }

  return { route, sweep, flush };
}
