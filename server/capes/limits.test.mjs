// Tests für die Grenzen gegen Überlastung: `node --test "server/capes/*test.mjs"` (zusammen mit den anderen)
// Jeder Test startet seinen eigenen server.mjs mit kleinen Grenzen, einem Schein-Mojang und leerem Datenordner.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { randomBytes, randomUUID } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { createServer } from "node:http";
import net from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, test } from "node:test";
import { ipBucket, Slots } from "./limits.mjs";
import { encodeRgba } from "./png.mjs";

const PLAYERS = {
  Salamander: "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
  Pepper_99: "11111111222233334444555555555555",
  Brotzeit: "22222222333344445555666666666666",
  Kaese: "33333333444455556666777777777777",
};
const uuidOf = (name) => PLAYERS[name].replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, "$1-$2-$3-$4-$5");

// ------------------------------------------------------------------ Schein-Mojang

const joined = new Set();
const mojang = createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  const name = url.searchParams.get("username");
  const serverId = url.searchParams.get("serverId");
  if (url.pathname === "/session/minecraft/hasJoined" && joined.has(`${name}|${serverId}`)) {
    joined.delete(`${name}|${serverId}`);
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ id: PLAYERS[name], name, properties: [] }));
  }
  res.writeHead(204);
  res.end();
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const listen = (server) => new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));

let mojangPort;
before(async () => {
  mojangPort = await listen(mojang);
});
after(() => mojang.close());

async function freePort() {
  const probe = createServer();
  const port = await listen(probe);
  await new Promise((resolve) => probe.close(resolve));
  return port;
}

/** Startet server.mjs (oder ein anderes Skript) mit eigenen Einstellungen. */
async function start(env = {}, script = "./server.mjs") {
  const port = await freePort();
  const dataDir = mkdtempSync(join(tmpdir(), "salami-limits-test-"));
  const child = spawn(process.execPath, [fileURLToPath(new URL(script, import.meta.url))], {
    env: {
      ...process.env,
      PORT: String(port),
      HOST: "127.0.0.1",
      DATA_DIR: dataDir,
      MOJANG_SESSION_URL: `http://127.0.0.1:${mojangPort}`,
      RATE_PER_MINUTE: "10000",
      FRIENDS_RATE_PER_MINUTE: "10000",
      ...env,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let log = "";
  child.stdout.on("data", (d) => (log += d));
  child.stderr.on("data", (d) => (log += d));
  const base = `http://127.0.0.1:${port}`;
  for (let i = 0; ; i++) {
    try {
      if ((await fetch(`${base}/health`)).ok) break;
    } catch {}
    if (i > 100) throw new Error(`Server startet nicht:\n${log}`);
    await sleep(50);
  }
  return {
    base,
    port,
    dataDir,
    log: () => log,
    running: () => child.exitCode === null && child.signalCode === null,
    async stop() {
      if (child.exitCode === null && child.signalCode === null) {
        const exited = new Promise((resolve) => child.once("exit", resolve));
        child.kill("SIGTERM");
        await exited;
      }
      rmSync(dataDir, { recursive: true, force: true });
    },
  };
}

async function call(s, method, path, body, headers = {}) {
  const response = await fetch(`${s.base}${path}`, {
    method,
    headers: { ...(body !== undefined ? { "Content-Type": "application/json" } : {}), ...headers },
    body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {}
  return { status: response.status, body: json, headers: response.headers };
}

/** X-Forwarded-For – mit TRUST_PROXY=1 kommt die Anfrage so von dieser IP. */
const from = (ip) => ({ "X-Forwarded-For": ip });

/** Challenge holen und bei „Mojang“ anmelden. → serverId */
async function joinedChallenge(s, name, headers = {}) {
  const res = await call(s, "POST", "/v1/auth/challenge", undefined, headers);
  assert.equal(res.status, 200);
  joined.add(`${name}|${res.body.serverId}`);
  return res.body.serverId;
}

/** Wie die Clients: anmelden und `path` mit {name, serverId} aufrufen. */
async function login(s, name, path, headers = {}) {
  const serverId = await joinedChallenge(s, name, headers);
  return call(s, "POST", path, { name, serverId }, headers);
}

/** Cape-PNG, das sich kaum packen lässt – als Base64 deutlich größer als 16 KB (Weg „große Anfrage“). */
function bigCapePng() {
  const png = encodeRgba(256, 128, randomBytes(256 * 128 * 4)).toString("base64");
  assert.ok(png.length > 64 * 1024);
  return png;
}

/** Wie ältere Clients: Anmeldung nur im Körper. */
async function setCapeLegacy(s, name, png, headers = {}) {
  const serverId = await joinedChallenge(s, name, headers);
  return call(s, "PUT", "/v1/capes/me", { name, serverId, cape: { png } }, headers);
}

/** Wie neue Clients: Anmeldung zusätzlich als Kopfzeilen, der Server prüft sie vor dem Körper. */
async function setCapeWithHeaders(s, name, png, headers = {}) {
  const serverId = await joinedChallenge(s, name, headers);
  return call(s, "PUT", "/v1/capes/me", { name, serverId, cape: { png } }, {
    ...headers,
    "X-Salami-Name": name,
    "X-Salami-Server-Id": serverId,
  });
}

/** Schickt die Kopfzeilen und nur einen kleinen Teil des Körpers – wie ein absichtlich hängender Upload. */
function stalledRequest(s, method, path, length, headers = {}) {
  return new Promise((resolve) => {
    const socket = net.connect(s.port, "127.0.0.1");
    const closed = new Promise((r) => socket.once("close", r));
    socket.on("error", () => {});
    socket.resume();
    socket.once("connect", () => {
      const lines = Object.entries({ Host: "localhost", "Content-Length": String(length), ...headers })
        .map(([key, value]) => `${key}: ${value}\r\n`)
        .join("");
      socket.write(`${method} ${path} HTTP/1.1\r\n${lines}\r\n`);
      socket.write(Buffer.alloc(Math.min(8 * 1024, length - 1), 0x61));
      resolve({ socket, closed });
    });
  });
}

async function within(promise, ms, what) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${what}: nicht innerhalb von ${ms} ms`)), ms);
  });
  try {
    return await Promise.race([promise, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

/** Live-Verbindung öffnen; sammelt die data-Zeilen. */
async function openStream(s, path, headers = {}) {
  const controller = new AbortController();
  const response = await fetch(`${s.base}${path}`, { headers, signal: controller.signal });
  const events = [];
  if (response.ok) {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    (async () => {
      try {
        for (;;) {
          const { value, done } = await reader.read();
          if (done) return;
          buffer += decoder.decode(value, { stream: true });
          let end;
          while ((end = buffer.indexOf("\n\n")) >= 0) {
            for (const line of buffer.slice(0, end).split("\n")) {
              if (line.startsWith("data: ")) events.push(JSON.parse(line.slice(6)));
            }
            buffer = buffer.slice(end + 2);
          }
        }
      } catch {}
    })();
  } else {
    await response.text();
  }
  return { status: response.status, events, close: () => controller.abort() };
}

// ------------------------------------------------------------------ Bausteine

test("ipBucket: IPv4 bleibt, IPv6 zählt pro /64, IPv4 in IPv6 zählt als IPv4", () => {
  assert.equal(ipBucket("203.0.113.7"), "203.0.113.7");
  assert.equal(ipBucket("::ffff:203.0.113.7"), "203.0.113.7");
  assert.equal(ipBucket("2001:db8:1:2:aaaa:bbbb:cccc:dddd"), "2001:db8:1:2::/64");
  assert.equal(ipBucket("2001:0DB8:0001:0002::1"), "2001:db8:1:2::/64");
  assert.equal(ipBucket("2001:db8:1:2::"), "2001:db8:1:2::/64");
  assert.equal(ipBucket("2001:db8::1"), "2001:db8:0:0::/64");
  assert.equal(ipBucket("2001:db8:0:0:1::1"), ipBucket("2001:db8::ffff"));
  assert.equal(ipBucket("[2001:db8:1:3::5]"), "2001:db8:1:3::/64");
  assert.equal(ipBucket("fe80::1%eth0"), "fe80:0:0:0::/64");
  assert.equal(ipBucket("64:ff9b::192.0.2.1"), "64:ff9b:0:0::/64");
  assert.notEqual(ipBucket("2001:db8:1:2::1"), ipBucket("2001:db8:1:3::1"));
  assert.equal(ipBucket(undefined), "unknown");
  assert.equal(ipBucket("unknown"), "unknown");
});

test("Slots: insgesamt und pro Schlüssel begrenzt, Freigabe zählt nur einmal", () => {
  const slots = new Slots(2, 1);
  const a = slots.acquire("a");
  assert.ok(a);
  assert.equal(slots.acquire("a"), null);
  const b = slots.acquire("b");
  assert.ok(b);
  assert.equal(slots.acquire("c"), null);
  a();
  a();
  assert.equal(slots.used, 1);
  assert.ok(slots.acquire("c"));
  assert.equal(slots.acquire("d"), null);
});

// ------------------------------------------------------------------ Anmeldung

test("Challenges: Salami-Format, begrenzt pro IP bzw. /64, abgelaufene machen vor dem Abweisen Platz", async () => {
  const s = await start({ TRUST_PROXY: "1", MAX_CHALLENGES_PER_IP: "3", MAX_CHALLENGES: "7", CHALLENGE_TTL_MS: "1500" });
  try {
    const challenge = (ip) => call(s, "POST", "/v1/auth/challenge", undefined, from(ip));
    for (let i = 0; i < 3; i++) {
      const res = await challenge("198.51.100.1");
      assert.equal(res.status, 200);
      assert.match(res.body.serverId, /^salami-[0-9a-f]{32}$/);
    }
    assert.equal((await challenge("198.51.100.1")).status, 429);
    // IPv6: ein /64 ist ein Anschluss – neue Adressen darin helfen nicht
    for (const ip of ["2001:db8:1:2::1", "2001:db8:1:2::2", "2001:db8:1:2:ffff::3"]) assert.equal((await challenge(ip)).status, 200);
    assert.equal((await challenge("2001:db8:1:2::4")).status, 429);
    assert.equal((await challenge("2001:db8:1:3::1")).status, 200);
    // Tabelle voll (7): wer neu kommt, muss warten, bis welche ablaufen …
    assert.equal((await challenge("198.51.100.2")).status, 503);
    await sleep(1700);
    // … dann räumt der Server vor dem Abweisen auf, statt bis zum nächsten Aufräumen voll zu bleiben
    assert.equal((await challenge("198.51.100.2")).status, 200);
    assert.equal((await challenge("198.51.100.1")).status, 200);
    // und die Anmeldung damit klappt
    assert.equal((await login(s, "Salamander", "/v1/friends/session", from("198.51.100.3"))).status, 200);
  } finally {
    await s.stop();
  }
});

// ------------------------------------------------------------------ große Anfragen

test("große Anfragen: Anmeldung per Kopfzeilen vor dem Lesen, ohne sie wenige Plätze pro IP und ein Zeitlimit", async () => {
  const s = await start({ TRUST_PROXY: "1", BODY_TIMEOUT_MS: "1500", MAX_ANON_BIG_BODIES: "2", MAX_ANON_BIG_BODIES_PER_IP: "1" });
  const stalled = [];
  try {
    const png = bigCapePng();
    // Ein hängender Upload ohne Anmeldung belegt den einzigen Platz dieser IP …
    stalled.push(await stalledRequest(s, "PUT", "/v1/capes/me", 600_000, { "Content-Type": "application/json", ...from("198.51.100.10") }));
    await sleep(200);
    // … ein zweiter von derselben IP wird sofort abgewiesen, statt noch einmal Megabytes zu puffern
    assert.equal((await setCapeLegacy(s, "Salamander", png, from("198.51.100.10"))).status, 503);
    // eine andere IP bekommt den zweiten Platz
    const other = await setCapeLegacy(s, "Pepper_99", png, from("198.51.100.11"));
    assert.equal(other.status, 200);
    assert.equal(other.body.status, "pending");
    // Beide Plätze ohne Anmeldung belegt: ältere Clients müssen warten …
    stalled.push(await stalledRequest(s, "PUT", "/v1/capes/me", 600_000, { "Content-Type": "application/json", ...from("198.51.100.12") }));
    await sleep(200);
    assert.equal((await setCapeLegacy(s, "Brotzeit", png, from("198.51.100.13"))).status, 503);
    // … angemeldete (Kopfzeilen) haben eigene Plätze, auch von der IP mit dem hängenden Upload
    const viaHeaders = await setCapeWithHeaders(s, "Brotzeit", png, from("198.51.100.10"));
    assert.equal(viaHeaders.status, 200);
    assert.equal(viaHeaders.body.status, "pending");
    // falsche Anmeldung in den Kopfzeilen: 401, ohne dass der Körper gelesen werden muss
    const wrong = await call(s, "PUT", "/v1/capes/me", { cape: { png } }, {
      ...from("198.51.100.13"),
      "X-Salami-Name": "Brotzeit",
      "X-Salami-Server-Id": `salami-${"0".repeat(32)}`,
    });
    assert.equal(wrong.status, 401);
    // Nach BODY_TIMEOUT_MS trennt der Server die hängenden Uploads, die Plätze sind wieder frei
    await within(Promise.all(stalled.map((r) => r.closed)), 5_000, "hängende Uploads getrennt");
    assert.equal((await setCapeLegacy(s, "Salamander", png, from("198.51.100.10"))).status, 200);
    assert.equal(s.running(), true);
  } finally {
    for (const r of stalled) r.socket.destroy();
    await s.stop();
  }
});

// ------------------------------------------------------------------ Emotes

test("Emote-Anmeldungen: höchstens MAX_EMOTE_SESSIONS_PER_PLAYER je Spieler, bei vollem Speicher fällt die älteste weg", async () => {
  const s = await start({ MAX_EMOTE_SESSIONS_PER_PLAYER: "2", MAX_EMOTE_SESSIONS: "3" });
  try {
    const emoteId = (await call(s, "GET", "/v1/emotes/catalog")).body.emotes[0].id;
    const emote = (token) => call(s, "POST", "/v1/emotes", { emote: emoteId }, { Authorization: `Bearer ${token}` });
    const tokens = [];
    for (let i = 0; i < 3; i++) {
      const res = await login(s, "Salamander", "/v1/emotes/session");
      assert.equal(res.status, 200);
      tokens.push(res.body.token);
    }
    assert.equal((await emote(tokens[0])).status, 401);
    assert.equal((await emote(tokens[1])).status, 200);
    assert.equal((await emote(tokens[2])).status, 200);
    // Speicher voll (3): kein 503 für alle, sondern die älteste Anmeldung überhaupt fällt weg
    const pep = await login(s, "Pepper_99", "/v1/emotes/session");
    const brot = await login(s, "Brotzeit", "/v1/emotes/session");
    assert.equal(pep.status, 200);
    assert.equal(brot.status, 200);
    assert.equal((await emote(tokens[1])).status, 401);
    assert.equal((await emote(tokens[2])).status, 200);
    assert.equal((await emote(pep.body.token)).status, 200);
    assert.equal((await emote(brot.body.token)).status, 200);
  } finally {
    await s.stop();
  }
});

// ------------------------------------------------------------------ Live-Verbindungen

test("Live-Verbindungen: anonyme Cape-Verbindungen lassen Platz für angemeldete Freunde-Verbindungen", async () => {
  // MAX_STREAMS 5 → ohne Anmeldung höchstens 3
  const s = await start({ TRUST_PROXY: "1", MAX_STREAMS: "5" });
  const open = [];
  try {
    for (let i = 0; i < 3; i++) {
      const stream = await openStream(s, "/v1/capes/events", from(`198.51.100.${20 + i}`));
      open.push(stream);
      assert.equal(stream.status, 200);
    }
    assert.equal((await openStream(s, "/v1/capes/events", from("198.51.100.30"))).status, 503);
    const token = (await login(s, "Salamander", "/v1/friends/session", from("198.51.100.40"))).body.token;
    const friends = await openStream(s, "/v1/friends/events", { ...from("198.51.100.40"), Authorization: `Bearer ${token}` });
    open.push(friends);
    assert.equal(friends.status, 200);
    // geschlossene Verbindungen geben ihren Platz frei
    open.shift().close();
    await sleep(200);
    const again = await openStream(s, "/v1/capes/events", from("198.51.100.30"));
    open.push(again);
    assert.equal(again.status, 200);
  } finally {
    for (const stream of open) stream.close();
    await s.stop();
  }
});

// ------------------------------------------------------------------ Freundesanfragen

test("Freundesanfragen: Sperrfrist pro Paar nach Zurückziehen, Grenze pro Stunde – kein Klingel-Spam", async () => {
  const s = await start({ FRIENDS_REQUEST_COOLDOWN_MS: "60000", FRIENDS_REQUESTS_PER_HOUR: "2" });
  let live;
  try {
    const token = {};
    for (const name of Object.keys(PLAYERS)) token[name] = (await login(s, name, "/v1/friends/session")).body.token;
    const as = (name) => ({ Authorization: `Bearer ${token[name]}` });
    const request = (by, name) => call(s, "POST", "/v1/friends/request", { name }, as(by));
    live = await openStream(s, "/v1/friends/events", as("Pepper_99"));
    assert.equal(live.status, 200);

    assert.equal((await request("Salamander", "Pepper_99")).body.status, "requested");
    // Anfragen, zurückziehen, wieder anfragen …: nur die erste klingelt
    for (let i = 0; i < 5; i++) {
      assert.equal((await call(s, "POST", "/v1/friends/deny", { uuid: uuidOf("Pepper_99") }, as("Salamander"))).status, 200);
      const again = await request("Salamander", "Pepper_99");
      assert.equal(again.status, 429);
      assert.match(again.body.error, /gerade erst angefragt/);
    }
    await sleep(200);
    assert.equal(live.events.filter((e) => e.type === "request").length, 1);
    assert.equal(live.events.filter((e) => e.type === "removed").length, 1);

    // höchstens 2 neue Anfragen pro Stunde; eine offene zu wiederholen zählt nicht
    assert.equal((await request("Salamander", "Brotzeit")).body.status, "requested");
    assert.equal((await request("Salamander", "Brotzeit")).body.status, "requested");
    const third = await request("Salamander", "Kaese");
    assert.equal(third.status, 429);
    assert.match(third.body.error, /zu viele Freundesanfragen/);
    // andere Spieler und die Gegenrichtung sind nicht betroffen
    assert.equal((await request("Pepper_99", "Kaese")).body.status, "requested");
    assert.equal((await request("Pepper_99", "Salamander")).body.status, "requested");
  } finally {
    live?.close();
    await s.stop();
  }
});

// ------------------------------------------------------------------ Bilder und Clips im Chat

test("Bilder und Clips: hängende Uploads werden abgebrochen, gleichzeitige pro Spieler begrenzt", async () => {
  const s = await start({ FRIENDS_UPLOAD_IDLE_MS: "800", FRIENDS_MAX_UPLOADS_PER_PLAYER: "1" });
  let hanging;
  try {
    const token = {};
    for (const name of ["Salamander", "Pepper_99"]) token[name] = (await login(s, name, "/v1/friends/session")).body.token;
    const as = (name) => ({ Authorization: `Bearer ${token[name]}` });
    await call(s, "POST", "/v1/friends/request", { name: "Pepper_99" }, as("Salamander"));
    assert.equal((await call(s, "POST", "/v1/friends/accept", { uuid: uuidOf("Salamander") }, as("Pepper_99"))).status, 200);
    const path = `/v1/friends/media?to=${uuidOf("Pepper_99")}&name=bild.png`;
    hanging = await stalledRequest(s, "POST", path, 200_000, { ...as("Salamander"), "Content-Type": "image/png" });
    await sleep(200);
    const png = encodeRgba(64, 32, randomBytes(64 * 32 * 4));
    const upload = () =>
      fetch(`${s.base}${path}`, { method: "POST", headers: { ...as("Salamander"), "Content-Type": "image/png" }, body: png });
    assert.equal((await upload()).status, 503);
    // kein Byte mehr: nach FRIENDS_UPLOAD_IDLE_MS getrennt, die halbe Datei ist weg
    await within(hanging.closed, 5_000, "hängender Upload getrennt");
    await sleep(100);
    assert.deepEqual(readdirSync(join(s.dataDir, "friends-media")).filter((f) => f.endsWith(".part")), []);
    assert.equal((await upload()).status, 200);
  } finally {
    hanging?.socket.destroy();
    await s.stop();
  }
});

// ------------------------------------------------------------------ Speichern

test("Speichern: schlägt das Schreiben fehl (z. B. Platte voll), läuft der Server weiter und versucht es später nochmal", async () => {
  const s = await start({ SAVE_RETRY_MS: "300" });
  try {
    await sleep(800); // erstes Speichern nach dem Start abwarten
    // Ein Ordner, wo die temporäre Datei hin soll: jedes Schreiben schlägt fehl
    mkdirSync(join(s.dataDir, "capes.json.tmp"));
    mkdirSync(join(s.dataDir, "friends.json.tmp"));
    const serverId = await joinedChallenge(s, "Salamander");
    assert.equal((await call(s, "PUT", "/v1/capes/me", { name: "Salamander", serverId, cape: { builtin: "salami" } })).status, 200);
    assert.equal((await login(s, "Pepper_99", "/v1/friends/session")).status, 200);
    await sleep(1200);
    assert.equal(s.running(), true);
    assert.equal((await fetch(`${s.base}/health`)).status, 200);
    assert.match(s.log(), /capes\.json nicht gespeichert/);
    assert.match(s.log(), /friends\.json nicht gespeichert/);
    const saved = (file) => (existsSync(join(s.dataDir, file)) ? readFileSync(join(s.dataDir, file), "utf8") : "");
    assert.doesNotMatch(saved("capes.json"), new RegExp(uuidOf("Salamander")));
    // Platz wieder da: der nächste Versuch klappt, nichts ging verloren
    rmSync(join(s.dataDir, "capes.json.tmp"), { recursive: true });
    rmSync(join(s.dataDir, "friends.json.tmp"), { recursive: true });
    await sleep(1000);
    assert.match(saved("capes.json"), new RegExp(uuidOf("Salamander")));
    assert.match(saved("friends.json"), new RegExp(uuidOf("Pepper_99")));
  } finally {
    await s.stop();
  }
});

// ------------------------------------------------------------------ Online-Zähler

test("Online-Zähler: Lebenszeichen nur als JSON ohne Origin, CORS nur für GET /v1/online", async () => {
  const s = await start({}, "../online-counter/server.mjs");
  try {
    const body = JSON.stringify({ id: randomUUID() });
    const heartbeat = (headers) => fetch(`${s.base}/v1/heartbeat`, { method: "POST", headers, body });
    // Was jede Webseite per fetch(…, {mode: "no-cors"}) schicken kann
    assert.equal((await heartbeat({ "Content-Type": "text/plain;charset=UTF-8" })).status, 415);
    assert.equal((await heartbeat({ "Content-Type": "application/x-www-form-urlencoded" })).status, 415);
    assert.equal((await heartbeat({ "Content-Type": "application/json", Origin: "https://evil.example" })).status, 415);
    assert.deepEqual(await (await fetch(`${s.base}/v1/online`)).json(), { online: 0 });
    // Der Launcher (reqwest .json(), ohne Origin)
    const ok = await heartbeat({ "Content-Type": "application/json" });
    assert.equal(ok.status, 200);
    assert.equal(ok.headers.get("access-control-allow-origin"), null);
    assert.deepEqual(await ok.json(), { online: 1 });
    const online = await fetch(`${s.base}/v1/online`);
    assert.equal(online.headers.get("access-control-allow-origin"), "*");
    assert.deepEqual(await online.json(), { online: 1 });
  } finally {
    await s.stop();
  }
});
