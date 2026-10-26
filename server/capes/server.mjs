#!/usr/bin/env node
/**
 * Salami Cape-Server: welches Cape jeder Salami-Spieler trägt – damit alle mit dem
 * Client die Capes der anderen sehen – und die Prüfung eigener Capes durch Admins.
 *
 * Mitgelieferte Capes (salami, pepperoni, board) sind sofort für alle sichtbar. Eigene
 * Bilder landen erst in der Warteschlange und sind für andere unsichtbar, bis ein Admin
 * sie auf der Prüf-Webseite (/admin) freigibt.
 *
 * Spiel (salami-core):
 *   POST /v1/auth/challenge                         → {"serverId":"…"}
 *   PUT  /v1/capes/me  {"name","serverId","cape"}   → {"status":"approved"|"pending"|"rejected", …}
 *        cape: {"builtin":"salami"} | {"png":"<base64>"} | null (kein Cape)
 *        Anmeldung wie beim Betreten eines Minecraft-Servers: das Spiel meldet die
 *        serverId bei Mojang an (joinServer), dieser Server fragt Mojang (hasJoined).
 *        Das Zugangstoken des Spielers bekommt der Cape-Server nie zu sehen.
 *   POST /v1/capes/lookup {"uuids":[…]}             → {"capes":{"<uuid>":{"builtin":"salami"}|{"id":"<capeId>"}}}
 *        nur sichtbare Capes: mitgelieferte und freigegebene
 *   GET  /v1/capes/img/<capeId>.png                 → freigegebenes Cape-Bild
 *   GET  /v1/capes/events                           → Server-Sent Events, eine Zeile pro Änderung:
 *        data: {"uuid":"…","cape":{"builtin":"salami"}|{"id":"…"}|null}
 *        (Cape gewechselt oder von einem Admin freigegeben/gesperrt) – so sehen alle
 *        Mitspieler einen Wechsel sofort.
 * Launcher:
 *   POST /v1/capes/status {"ids":[…]}               → {"status":{"<capeId>":{"status":"pending"|…,"reason"?}}}
 *   POST /v1/capes/submit {"name","serverId","png"} → {"status":"approved"|"pending"|"rejected","id","reason"?}
 *        eigenes Cape gleich nach dem Hinzufügen zur Prüfung einreichen, ohne das getragene
 *        Cape zu ändern. Anmeldung wie bei PUT /v1/capes/me. Solche Capes bleiben in der
 *        Warteschlange, auch wenn sie (noch) keiner trägt.
 * Admins (Authorization: Bearer <token>, siehe ADMIN_TOKENS):
 *   GET  /admin/                                    → Prüf-Webseite
 *   GET  /v1/admin/me                               → {"name":"…"}
 *   GET  /v1/admin/capes?status=pending             → {"capes":[…],"counts":{…}}
 *   GET  /v1/admin/img/<capeId>.png                 → Bild, egal welcher Status
 *   POST /v1/admin/capes/<capeId>/approve
 *   POST /v1/admin/capes/<capeId>/reject {"reason":"…"}
 *   GET  /health                                    → ok
 *
 * Node 18+, keine Abhängigkeiten. Einstellungen per Umgebungsvariable (siehe README.md).
 */
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync, existsSync } from "node:fs";
import { createServer } from "node:http";
import { join } from "node:path";
import { capeId, decodeCape, encodeRgba, PngError } from "./png.mjs";

const env = (name, fallback) => {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
};

const PORT = env("PORT", 8788);
const HOST = process.env.HOST || "0.0.0.0";
/** 1 = hinter Caddy/nginx: echte IP aus X-Forwarded-For (letzter Eintrag, den der Proxy anhängt). */
const TRUST_PROXY = process.env.TRUST_PROXY === "1";
const DATA_DIR = process.env.DATA_DIR || join(process.cwd(), "data");
const IMAGE_DIR = join(DATA_DIR, "images");
const DB_FILE = join(DATA_DIR, "capes.json");
/** Nur zum Testen ändern. */
const MOJANG_SESSION_URL = (process.env.MOJANG_SESSION_URL || "https://sessionserver.mojang.com").replace(/\/$/, "");
/** Anfragen pro IP und Minute (ohne Lookups), danach 429. */
const RATE_PER_MINUTE = env("RATE_PER_MINUTE", 30);
/** Lookups (Capes der Mitspieler) pro IP und Minute. */
const LOOKUP_RATE_PER_MINUTE = env("LOOKUP_RATE_PER_MINUTE", 120);
/** Neue eigene Capes pro Spieler und Tag. */
const UPLOADS_PER_DAY = env("UPLOADS_PER_DAY", 10);
/** Höchstens so viele Capes warten gleichzeitig auf Prüfung. */
const MAX_PENDING = env("MAX_PENDING", 500);
/** Gleichzeitig offene Live-Verbindungen (eine pro laufendem Spiel). */
const MAX_STREAMS = env("MAX_STREAMS", 5_000);
const MAX_STREAMS_PER_IP = env("MAX_STREAMS_PER_IP", 10);
/** Lebenszeichen auf offenen Live-Verbindungen, damit Proxy und Spiel tote Verbindungen erkennen. */
const HEARTBEAT_MS = 20_000;

const BUILTIN = new Set(["salami", "pepperoni", "board"]);
const STATUSES = ["pending", "approved", "rejected"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const CAPE_ID = /^[0-9a-f]{64}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;
const MAX_PNG_BYTES = 4 * 1024 * 1024;
const MAX_UPLOAD_BODY = Math.ceil((MAX_PNG_BYTES * 4) / 3) + 4096;
const MAX_SMALL_BODY = 16 * 1024;
const CHALLENGE_TTL_MS = 60_000;
const MAX_CHALLENGES = 10_000;
const MAX_LOOKUP = 200;
const MAX_REASON = 200;

/** ADMIN_TOKENS="leo:langes-geheimnis,max:anderes-geheimnis" – Name nur fürs Protokoll. */
const ADMINS = (process.env.ADMIN_TOKENS || "")
  .split(",")
  .map((entry) => entry.trim())
  .filter(Boolean)
  .map((entry) => {
    const at = entry.indexOf(":");
    const name = at > 0 ? entry.slice(0, at).trim() : "admin";
    const token = at > 0 ? entry.slice(at + 1).trim() : entry;
    return { name, digest: createHash("sha256").update(token).digest(), weak: token.length < 16 };
  });

// ------------------------------------------------------------------ Speicher

/**
 * players: uuid → { name, cape: {builtin} | {id}, updatedAt }
 * capes:   capeId → { status, width, height, submittedAt, submittedBy, submittedName,
 *                     viaLauncher?, reviewedAt?, reviewedBy?, reason? }
 *          viaLauncher: im Launcher eingereicht – bleibt in der Warteschlange, auch ungetragen
 */
let db = { players: {}, capes: {} };

function loadDb() {
  mkdirSync(IMAGE_DIR, { recursive: true });
  if (!existsSync(DB_FILE)) return;
  const parsed = JSON.parse(readFileSync(DB_FILE, "utf-8"));
  db = { players: parsed.players ?? {}, capes: parsed.capes ?? {} };
}

let saveTimer = null;
function saveNow() {
  clearTimeout(saveTimer);
  saveTimer = null;
  const tmp = `${DB_FILE}.tmp`;
  writeFileSync(tmp, JSON.stringify(db));
  renameSync(tmp, DB_FILE);
}
function scheduleSave() {
  if (!saveTimer) saveTimer = setTimeout(saveNow, 500);
}

const imagePath = (id) => join(IMAGE_DIR, `${id}.png`);

/**
 * Warteschlange sauber halten: ungeprüfte Capes, die niemand mehr trägt, verschwinden –
 * außer sie wurden ausdrücklich im Launcher eingereicht.
 */
function dropIfOrphanedPending(id) {
  const cape = db.capes[id];
  if (!cape || cape.status !== "pending" || cape.viaLauncher) return;
  if (Object.values(db.players).some((p) => p.cape?.id === id)) return;
  delete db.capes[id];
  try {
    unlinkSync(imagePath(id));
  } catch {}
}

/** Was andere Spieler von diesem Spieler sehen (oder null). */
function visibleCape(player) {
  if (!player?.cape) return null;
  if (player.cape.builtin) return { builtin: player.cape.builtin };
  if (player.cape.id && db.capes[player.cape.id]?.status === "approved") return { id: player.cape.id };
  return null;
}

// ------------------------------------------------------------------ Hilfen

function clientIp(req) {
  if (TRUST_PROXY) {
    const xff = req.headers["x-forwarded-for"];
    if (typeof xff === "string" && xff.length > 0) {
      const parts = xff.split(",");
      return parts[parts.length - 1].trim();
    }
  }
  return req.socket.remoteAddress ?? "unknown";
}

/** bucket:ip → { windowStart, count } */
const rate = new Map();
function rateLimited(bucket, ip, limit, windowMs = 60_000, now = Date.now()) {
  const key = `${bucket}:${ip}`;
  const r = rate.get(key);
  if (!r || now - r.windowStart > windowMs) {
    rate.set(key, { windowStart: now, count: 1, windowMs });
    return false;
  }
  r.count += 1;
  return r.count > limit;
}

/** uuid → Zeitpunkte neuer Einreichungen (letzte 24 h) */
const uploads = new Map();
function uploadAllowed(uuid, now = Date.now()) {
  const recent = (uploads.get(uuid) ?? []).filter((t) => now - t < 86_400_000);
  uploads.set(uuid, recent);
  return recent.length < UPLOADS_PER_DAY;
}

/** serverId → Ablaufzeit */
const challenges = new Map();

function sweep(now = Date.now()) {
  for (const [id, expires] of challenges) if (expires < now) challenges.delete(id);
  for (const [key, r] of rate) if (now - r.windowStart > r.windowMs) rate.delete(key);
  for (const [uuid, list] of uploads) if (!list.some((t) => now - t < 86_400_000)) uploads.delete(uuid);
  for (const [ip, f] of adminFailures) if (now - f.since >= ADMIN_LOCK_MS) adminFailures.delete(ip);
}

const SECURITY_HEADERS = {
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "X-Frame-Options": "DENY",
};

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, {
    ...SECURITY_HEADERS,
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "Content-Length": Buffer.byteLength(json),
  });
  res.end(json);
}

function sendFile(res, data, type, cache) {
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    "Content-Type": type,
    "Cache-Control": cache,
    "Content-Length": data.length,
  });
  res.end(data);
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers["content-length"]);
    if (Number.isFinite(declared) && declared > limit) {
      reject(new HttpError(413, "Anfrage zu groß"));
      req.resume();
      return;
    }
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new HttpError(413, "Anfrage zu groß"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", () => reject(new HttpError(400, "Anfrage abgebrochen")));
  });
}

async function readJson(req, limit) {
  const body = await readBody(req, limit);
  try {
    const value = JSON.parse(body.toString("utf-8"));
    if (value && typeof value === "object" && !Array.isArray(value)) return value;
  } catch {}
  throw new HttpError(400, "ungültiges JSON");
}

const dashed = (hex) =>
  `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`.toLowerCase();

// ------------------------------------------------------------------ Anmeldung (Mojang)

function newChallenge() {
  if (challenges.size >= MAX_CHALLENGES) throw new HttpError(503, "gerade zu viele Anmeldungen");
  const serverId = randomBytes(20).toString("hex");
  challenges.set(serverId, Date.now() + CHALLENGE_TTL_MS);
  return serverId;
}

/** Prüft bei Mojang, ob dieser Spieler die serverId gerade angemeldet hat. → { uuid, name } */
async function verifyPlayer(name, serverId) {
  if (typeof name !== "string" || !PLAYER_NAME.test(name) || typeof serverId !== "string") {
    throw new HttpError(400, "name oder serverId fehlt");
  }
  const expires = challenges.get(serverId);
  challenges.delete(serverId); // nur einmal verwendbar
  if (!expires || expires < Date.now()) throw new HttpError(401, "Anmeldung abgelaufen, bitte neu versuchen");

  const url = `${MOJANG_SESSION_URL}/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${serverId}`;
  let response;
  try {
    response = await fetch(url, { signal: AbortSignal.timeout(8_000) });
  } catch {
    throw new HttpError(503, "Mojang ist gerade nicht erreichbar");
  }
  if (response.status === 204 || response.status === 403) throw new HttpError(401, "Anmeldung bei Mojang fehlgeschlagen");
  if (!response.ok) throw new HttpError(503, "Mojang ist gerade nicht erreichbar");
  const profile = await response.json().catch(() => null);
  if (!profile || typeof profile.id !== "string" || !/^[0-9a-fA-F]{32}$/.test(profile.id)) {
    throw new HttpError(401, "Anmeldung bei Mojang fehlgeschlagen");
  }
  return { uuid: dashed(profile.id), name: typeof profile.name === "string" ? profile.name : name };
}

// ------------------------------------------------------------------ Spieler-API

/** Prüft ein eingereichtes Bild (Base64-PNG) und legt es bei Bedarf neu in die Warteschlange. → capeId */
function storeCape(pngBase64, uuid, name, now) {
  const png = Buffer.from(pngBase64, "base64");
  if (png.length === 0 || png.length > MAX_PNG_BYTES) throw new HttpError(413, "Bild größer als 4 MiB");
  let decoded;
  try {
    decoded = decodeCape(png);
  } catch (e) {
    throw new HttpError(422, e instanceof PngError ? e.message : "Bild konnte nicht gelesen werden");
  }
  const id = capeId(decoded.width, decoded.height, decoded.rgba);
  if (!db.capes[id]) {
    if (!uploadAllowed(uuid)) throw new HttpError(429, "heute schon zu viele neue Capes eingereicht");
    const pending = Object.values(db.capes).filter((c) => c.status === "pending").length;
    if (pending >= MAX_PENDING) throw new HttpError(503, "Prüf-Warteschlange ist voll, bitte später nochmal");
    writeFileSync(imagePath(id), encodeRgba(decoded.width, decoded.height, decoded.rgba));
    db.capes[id] = {
      status: "pending",
      width: decoded.width,
      height: decoded.height,
      submittedAt: now,
      submittedBy: uuid,
      submittedName: name,
    };
    uploads.get(uuid).push(Date.now());
    console.log(`[salami-capes] neues Cape ${id.slice(0, 12)} von ${name} wartet auf Prüfung`);
  }
  return id;
}

/** Antwort mit dem Prüfstatus eines Capes. */
function capeResult(id) {
  const entry = db.capes[id];
  return { status: entry.status, id, ...(entry.status === "rejected" && entry.reason ? { reason: entry.reason } : {}) };
}

async function handleSetCape(req, res) {
  const body = await readJson(req, MAX_UPLOAD_BODY);
  const { uuid, name } = await verifyPlayer(body.name, body.serverId);
  const previous = db.players[uuid]?.cape?.id;
  const visibleBefore = JSON.stringify(visibleCape(db.players[uuid]));
  const cape = body.cape ?? null;
  const now = new Date().toISOString();
  let result;

  if (cape === null) {
    delete db.players[uuid];
    result = { status: "none" };
  } else if (typeof cape.builtin === "string") {
    if (!BUILTIN.has(cape.builtin)) throw new HttpError(400, "unbekanntes Cape");
    db.players[uuid] = { name, cape: { builtin: cape.builtin }, updatedAt: now };
    result = { status: "approved", builtin: cape.builtin };
  } else if (typeof cape.png === "string") {
    const id = storeCape(cape.png, uuid, name, now);
    db.players[uuid] = { name, cape: { id }, updatedAt: now };
    result = capeResult(id);
  } else {
    throw new HttpError(400, "cape muss null, {builtin} oder {png} sein");
  }

  if (previous && previous !== result.id) dropIfOrphanedPending(previous);
  scheduleSave();
  const visibleAfter = visibleCape(db.players[uuid]);
  if (JSON.stringify(visibleAfter) !== visibleBefore) broadcast(uuid, visibleAfter);
  send(res, 200, result);
}

/** Launcher: eigenes Cape gleich nach dem Hinzufügen einreichen – das getragene Cape bleibt. */
async function handleSubmit(req, res) {
  const body = await readJson(req, MAX_UPLOAD_BODY);
  if (typeof body.png !== "string") throw new HttpError(400, "png fehlt");
  const { uuid, name } = await verifyPlayer(body.name, body.serverId);
  const id = storeCape(body.png, uuid, name, new Date().toISOString());
  const cape = db.capes[id];
  if (cape.status === "pending") cape.viaLauncher = true;
  scheduleSave();
  send(res, 200, capeResult(id));
}

async function handleLookup(req, res) {
  const body = await readJson(req, MAX_SMALL_BODY);
  if (!Array.isArray(body.uuids) || body.uuids.length > MAX_LOOKUP) throw new HttpError(400, "uuids fehlt oder zu lang");
  const capes = {};
  for (const raw of body.uuids) {
    if (typeof raw !== "string") continue;
    const uuid = raw.toLowerCase();
    if (!UUID.test(uuid)) continue;
    const cape = visibleCape(db.players[uuid]);
    if (cape) capes[uuid] = cape;
  }
  send(res, 200, { capes });
}

async function handleStatus(req, res) {
  const body = await readJson(req, MAX_SMALL_BODY);
  if (!Array.isArray(body.ids) || body.ids.length > 100) throw new HttpError(400, "ids fehlt oder zu lang");
  const status = {};
  for (const id of body.ids) {
    const cape = typeof id === "string" && CAPE_ID.test(id) ? db.capes[id] : undefined;
    if (cape) status[id] = { status: cape.status, ...(cape.status === "rejected" && cape.reason ? { reason: cape.reason } : {}) };
  }
  send(res, 200, { status });
}

function handleImage(res, id, admin) {
  const cape = db.capes[id];
  if (!cape || (!admin && cape.status !== "approved")) return send(res, 404, { error: "nicht gefunden" });
  let data;
  try {
    data = readFileSync(imagePath(id));
  } catch {
    return send(res, 404, { error: "nicht gefunden" });
  }
  // Freigegebene Bilder ändern sich nie (ID = Inhalt); Admin-Bilder nicht zwischenspeichern.
  sendFile(res, data, "image/png", admin ? "private, no-store" : "public, max-age=86400");
}

// ------------------------------------------------------------------ Admin-API

/** ip → { since, count }: nach 10 falschen Schlüsseln ist die IP 10 Minuten gesperrt. */
const adminFailures = new Map();
const ADMIN_LOCK_MS = 600_000;

function authenticateAdmin(req, ip, now = Date.now()) {
  const failures = adminFailures.get(ip);
  if (failures && now - failures.since < ADMIN_LOCK_MS && failures.count >= 10) {
    throw new HttpError(429, "zu viele Fehlversuche, bitte 10 Minuten warten");
  }
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || ADMINS.length === 0) throw new HttpError(401, "nicht angemeldet");
  const digest = createHash("sha256").update(token).digest();
  const admin = ADMINS.find((a) => timingSafeEqual(a.digest, digest));
  if (!admin) {
    if (!failures || now - failures.since >= ADMIN_LOCK_MS) adminFailures.set(ip, { since: now, count: 1 });
    else failures.count += 1;
    throw new HttpError(401, "falscher Admin-Schlüssel");
  }
  return admin.name;
}

function adminList(status) {
  const users = new Map();
  for (const [uuid, player] of Object.entries(db.players)) {
    if (!player.cape?.id) continue;
    if (!users.has(player.cape.id)) users.set(player.cape.id, []);
    users.get(player.cape.id).push({ uuid, name: player.name });
  }
  const counts = Object.fromEntries(STATUSES.map((s) => [s, 0]));
  const list = [];
  for (const [id, cape] of Object.entries(db.capes)) {
    counts[cape.status] = (counts[cape.status] ?? 0) + 1;
    if (cape.status === status) list.push({ id, ...cape, users: users.get(id) ?? [] });
  }
  list.sort((a, b) =>
    status === "pending"
      ? a.submittedAt.localeCompare(b.submittedAt)
      : (b.reviewedAt ?? b.submittedAt).localeCompare(a.reviewedAt ?? a.submittedAt),
  );
  return { capes: list.slice(0, 200), counts };
}

async function handleReview(req, res, admin, id, action) {
  const cape = db.capes[id];
  if (!cape) return send(res, 404, { error: "Cape nicht gefunden" });
  const body = action === "reject" ? await readJson(req, MAX_SMALL_BODY).catch(() => ({})) : {};
  const wearers = Object.entries(db.players).filter(([, player]) => player.cape?.id === id);
  const visibleBefore = new Map(wearers.map(([uuid, player]) => [uuid, JSON.stringify(visibleCape(player))]));
  cape.status = action === "approve" ? "approved" : "rejected";
  cape.reviewedAt = new Date().toISOString();
  cape.reviewedBy = admin;
  if (action === "reject" && typeof body.reason === "string" && body.reason.trim()) {
    cape.reason = body.reason.trim().slice(0, MAX_REASON);
  } else {
    delete cape.reason;
  }
  scheduleSave();
  // Alle, die das Cape tragen: Mitspieler sehen die Freigabe (oder Sperre) sofort
  for (const [uuid, player] of wearers) {
    const visible = visibleCape(player);
    if (JSON.stringify(visible) !== visibleBefore.get(uuid)) broadcast(uuid, visible);
  }
  console.log(`[salami-capes] ${admin}: Cape ${id.slice(0, 12)} ${cape.status}`);
  send(res, 200, { id, status: cape.status });
}

// ------------------------------------------------------------------ Live-Änderungen (Server-Sent Events)

/** Offene Live-Verbindungen: res → ip */
const streams = new Map();

function openEvents(req, res, ip) {
  let fromIp = 0;
  for (const other of streams.values()) if (other === ip) fromIp++;
  if (streams.size >= MAX_STREAMS || fromIp >= MAX_STREAMS_PER_IP) throw new HttpError(503, "zu viele Verbindungen");
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-store",
    // nginx: nicht puffern, sonst kommen Änderungen erst verspätet an
    "X-Accel-Buffering": "no",
  });
  res.write("retry: 5000\n: verbunden\n\n");
  streams.set(res, ip);
  const drop = () => streams.delete(res);
  req.on("close", drop);
  res.on("error", drop);
}

/** Meldet allen offenen Live-Verbindungen, was andere jetzt von diesem Spieler sehen. */
function broadcast(uuid, cape) {
  if (streams.size === 0) return;
  const event = `data: ${JSON.stringify({ uuid, cape })}\n\n`;
  for (const res of streams.keys()) res.write(event);
}

setInterval(() => {
  for (const res of streams.keys()) res.write(": ping\n\n");
}, HEARTBEAT_MS).unref();

// ------------------------------------------------------------------ Admin-Webseite

const ADMIN_FILES = {
  "index.html": "text/html; charset=utf-8",
  "app.js": "text/javascript; charset=utf-8",
  "app.css": "text/css; charset=utf-8",
};
const adminFiles = Object.fromEntries(
  Object.entries(ADMIN_FILES).map(([file, type]) => [file, { type, data: readFileSync(new URL(`./admin/${file}`, import.meta.url)) }]),
);
const ADMIN_CSP =
  "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

function serveAdminFile(res, file) {
  const entry = adminFiles[file];
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    "Content-Type": entry.type,
    "Content-Security-Policy": ADMIN_CSP,
    "Cache-Control": "no-cache",
    "Content-Length": entry.data.length,
  });
  res.end(entry.data);
}

// ------------------------------------------------------------------ Routing

async function route(req, res) {
  const url = new URL(req.url ?? "/", "http://localhost");
  const path = url.pathname;
  const ip = clientIp(req);

  if (req.method === "GET" && path === "/health") {
    res.writeHead(200, { "Content-Type": "text/plain" });
    return res.end("ok");
  }

  if (req.method === "GET" && path === "/admin") {
    res.writeHead(301, { Location: "admin/" });
    return res.end();
  }
  if (req.method === "GET" && path.startsWith("/admin/")) {
    const file = path === "/admin/" ? "index.html" : path.slice("/admin/".length);
    if (Object.hasOwn(ADMIN_FILES, file)) return serveAdminFile(res, file);
    return send(res, 404, { error: "nicht gefunden" });
  }

  const image = /^\/v1\/(capes|admin)\/img\/([0-9a-f]{64})\.png$/.exec(path);
  if (req.method === "GET" && image) {
    if (image[1] === "admin") {
      authenticateAdmin(req, ip);
      return handleImage(res, image[2], true);
    }
    if (rateLimited("lookup", ip, LOOKUP_RATE_PER_MINUTE)) throw new HttpError(429, "zu viele Anfragen");
    return handleImage(res, image[2], false);
  }

  if (req.method === "GET" && path === "/v1/capes/events") {
    if (rateLimited("events", ip, RATE_PER_MINUTE)) throw new HttpError(429, "zu viele Anfragen");
    return openEvents(req, res, ip);
  }

  if (req.method === "POST" && path === "/v1/capes/lookup") {
    if (rateLimited("lookup", ip, LOOKUP_RATE_PER_MINUTE)) throw new HttpError(429, "zu viele Anfragen");
    return handleLookup(req, res);
  }

  if (path.startsWith("/v1/admin/")) {
    const admin = authenticateAdmin(req, ip);
    if (req.method === "GET" && path === "/v1/admin/me") return send(res, 200, { name: admin });
    if (req.method === "GET" && path === "/v1/admin/capes") {
      const status = url.searchParams.get("status") ?? "pending";
      if (!STATUSES.includes(status)) throw new HttpError(400, "unbekannter Status");
      return send(res, 200, adminList(status));
    }
    const review = /^\/v1\/admin\/capes\/([0-9a-f]{64})\/(approve|reject)$/.exec(path);
    if (req.method === "POST" && review) return handleReview(req, res, admin, review[1], review[2]);
    return send(res, 404, { error: "nicht gefunden" });
  }

  if (rateLimited("api", ip, RATE_PER_MINUTE)) throw new HttpError(429, "zu viele Anfragen");
  if (req.method === "POST" && path === "/v1/auth/challenge") return send(res, 200, { serverId: newChallenge() });
  if (req.method === "PUT" && path === "/v1/capes/me") return handleSetCape(req, res);
  if (req.method === "POST" && path === "/v1/capes/submit") return handleSubmit(req, res);
  if (req.method === "POST" && path === "/v1/capes/status") return handleStatus(req, res);

  send(res, 404, { error: "nicht gefunden" });
}

loadDb();
if (ADMINS.length === 0) console.warn("[salami-capes] ADMIN_TOKENS ist leer – niemand kann Capes prüfen.");
for (const admin of ADMINS) if (admin.weak) console.warn(`[salami-capes] Admin-Schlüssel von ${admin.name} ist kürzer als 16 Zeichen.`);

const server = createServer((req, res) => {
  route(req, res).catch((e) => {
    if (res.headersSent) return res.destroy();
    if (e instanceof HttpError) return send(res, e.status, { error: e.message });
    console.error("[salami-capes] Fehler:", e);
    send(res, 500, { error: "interner Fehler" });
  });
});

// Uploads bis ~6 MiB brauchen etwas Zeit, hängende Verbindungen trotzdem nicht ewig offen halten.
server.requestTimeout = 30_000;
server.headersTimeout = 10_000;
server.keepAliveTimeout = 5_000;

setInterval(sweep, 30_000).unref();

server.listen(PORT, HOST, () => {
  console.log(
    `[salami-capes] listening on http://${HOST}:${PORT} (data ${DATA_DIR}, ${ADMINS.length} admins, trust proxy ${TRUST_PROXY})`,
  );
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    if (saveTimer) saveNow();
    for (const res of streams.keys()) res.end();
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 2_000).unref();
  });
}
