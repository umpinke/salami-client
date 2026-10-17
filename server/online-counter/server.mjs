#!/usr/bin/env node
/**
 * Salami Online-Zähler: zählt, wie viele Salami-Launcher gerade offen sind.
 *
 * Jeder Launcher schickt alle 30 s ein Lebenszeichen mit einer zufälligen ID, die
 * bei jedem Start neu erzeugt wird (kein Name, kein Account). Wer sich länger als
 * TTL_SECONDS nicht meldet, gilt als offline. Alles liegt nur im Arbeitsspeicher –
 * nach einem Neustart ist die Zahl nach spätestens 30 s wieder richtig.
 *
 *   POST /v1/heartbeat  {"id":"<uuid>"}  → {"online":42}
 *   POST /v1/leave      {"id":"<uuid>"}  → {"online":41}
 *   GET  /v1/online                      → {"online":41}   (CORS offen, z. B. für die Website)
 *   GET  /health                         → ok
 *
 * Node 18+, keine Abhängigkeiten. Einstellungen per Umgebungsvariable (siehe README.md).
 */
import { createServer } from "node:http";

const env = (name, fallback) => {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
};

const PORT = env("PORT", 8787);
const HOST = process.env.HOST || "0.0.0.0";
const TTL_MS = env("TTL_SECONDS", 90) * 1000;
/** 1 = hinter Caddy/nginx: echte IP aus X-Forwarded-For (letzter Eintrag, den der Proxy anhängt). */
const TRUST_PROXY = process.env.TRUST_PROXY === "1";
/** Höchstens so viele gleichzeitige Launcher pro IP – damit niemand die Zahl künstlich aufbläht. */
const MAX_PER_IP = env("MAX_PER_IP", 10);
const MAX_SESSIONS = env("MAX_SESSIONS", 100_000);
/** Anfragen pro IP und Minute, danach 429. */
const RATE_PER_MINUTE = env("RATE_PER_MINUTE", 30);

const ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_BODY_BYTES = 256;

/** id → { ip, lastSeen } */
const sessions = new Map();
/** ip → Anzahl Sessions */
const perIp = new Map();
/** ip → { windowStart, count } */
const rate = new Map();

function addIp(ip, delta) {
  const next = (perIp.get(ip) ?? 0) + delta;
  if (next <= 0) perIp.delete(ip);
  else perIp.set(ip, next);
}

function removeSession(id) {
  const s = sessions.get(id);
  if (!s) return;
  sessions.delete(id);
  addIp(s.ip, -1);
}

function sweep(now = Date.now()) {
  for (const [id, s] of sessions) {
    if (now - s.lastSeen > TTL_MS) removeSession(id);
  }
  for (const [ip, r] of rate) {
    if (now - r.windowStart > 60_000) rate.delete(ip);
  }
}

function heartbeat(id, ip, now = Date.now()) {
  const s = sessions.get(id);
  if (s) {
    if (s.ip !== ip) {
      // IP-Wechsel (z. B. neues WLAN): Session mitnehmen, wenn die neue IP noch Platz hat.
      if ((perIp.get(ip) ?? 0) >= MAX_PER_IP) return;
      addIp(s.ip, -1);
      addIp(ip, 1);
      s.ip = ip;
    }
    s.lastSeen = now;
    return;
  }
  if (sessions.size >= MAX_SESSIONS || (perIp.get(ip) ?? 0) >= MAX_PER_IP) return;
  sessions.set(id, { ip, lastSeen: now });
  addIp(ip, 1);
}

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

function rateLimited(ip, now = Date.now()) {
  const r = rate.get(ip);
  if (!r || now - r.windowStart > 60_000) {
    rate.set(ip, { windowStart: now, count: 1 });
    return false;
  }
  r.count += 1;
  return r.count > RATE_PER_MINUTE;
}

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": "*",
    "Content-Length": Buffer.byteLength(json),
  });
  res.end(json);
}

function readId(req) {
  return new Promise((resolve) => {
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        resolve(null);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        const { id } = JSON.parse(Buffer.concat(chunks).toString("utf-8"));
        resolve(typeof id === "string" && ID_PATTERN.test(id) ? id.toLowerCase() : null);
      } catch {
        resolve(null);
      }
    });
    req.on("error", () => resolve(null));
  });
}

const server = createServer(async (req, res) => {
  const path = (req.url ?? "/").split("?")[0];

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET",
      "Access-Control-Max-Age": "86400",
    });
    return res.end();
  }

  if (req.method === "GET" && path === "/health") {
    res.writeHead(200, { "Content-Type": "text/plain" });
    return res.end("ok");
  }

  if (req.method === "GET" && path === "/v1/online") {
    return send(res, 200, { online: sessions.size });
  }

  if (req.method === "POST" && (path === "/v1/heartbeat" || path === "/v1/leave")) {
    const ip = clientIp(req);
    if (rateLimited(ip)) return send(res, 429, { error: "too many requests" });
    const id = await readId(req);
    if (!id) return send(res, 400, { error: "invalid id" });
    if (path === "/v1/heartbeat") heartbeat(id, ip);
    else removeSession(id);
    return send(res, 200, { online: sessions.size });
  }

  send(res, 404, { error: "not found" });
});

// Langsame oder hängende Verbindungen nicht ewig offen halten.
server.requestTimeout = 5_000;
server.headersTimeout = 5_000;
server.keepAliveTimeout = 5_000;

setInterval(sweep, 10_000).unref();

server.listen(PORT, HOST, () => {
  console.log(
    `[salami-online] listening on http://${HOST}:${PORT} (ttl ${TTL_MS / 1000}s, trust proxy ${TRUST_PROXY})`,
  );
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
