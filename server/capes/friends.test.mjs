// Tests für Freunde und Chat: `node --test "server/capes/*test.mjs"` (zusammen mit test.mjs)
// Startet server.mjs wie test.mjs mit einem Schein-Mojang (hasJoined) und einem leeren Datenordner.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, test } from "node:test";

/** Gnadenfrist bis „offline“, im Test kurz. */
const GRACE_MS = 400;
const PLAYERS = {
  Salamander: "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
  Pepper_99: "11111111222233334444555555555555",
  Brotzeit: "22222222333344445555666666666666",
  Kaese: "33333333444455556666777777777777",
};

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

let base;
let child;
const dataDir = mkdtempSync(join(tmpdir(), "salami-friends-test-"));
const friendsFile = join(dataDir, "friends.json");

function listen(server) {
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));
}

async function freePort() {
  const probe = createServer();
  const port = await listen(probe);
  await new Promise((resolve) => probe.close(resolve));
  return port;
}

async function startServer(port, mojangPort) {
  child = spawn(process.execPath, [fileURLToPath(new URL("./server.mjs", import.meta.url))], {
    env: {
      ...process.env,
      PORT: String(port),
      HOST: "127.0.0.1",
      DATA_DIR: dataDir,
      ADMIN_TOKENS: "leo:test-admin-token-1234567890",
      MOJANG_SESSION_URL: `http://127.0.0.1:${mojangPort}`,
      RATE_PER_MINUTE: "1000",
      FRIENDS_RATE_PER_MINUTE: "10000",
      FRIENDS_OFFLINE_GRACE_MS: String(GRACE_MS),
      FRIENDS_IMAGE_MB: "1",
      FRIENDS_CLIP_MB: "1",
      FRIENDS_MEDIA_QUOTA_MB: "1",
      MAX_STREAMS_PER_IP: "50",
      // Die Tests fragen dieselben Spieler mehrfach an; Sperrfrist und Stundengrenze prüft limits.test.mjs
      FRIENDS_REQUEST_COOLDOWN_MS: "0",
    },
    stdio: ["ignore", "pipe", "inherit"],
  });
  child.stdout.resume();
  for (let i = 0; i < 100; i++) {
    try {
      if ((await fetch(`${base}/health`)).ok) return;
    } catch {}
    await sleep(50);
  }
  throw new Error("server did not start");
}

async function stopServer() {
  const exited = new Promise((resolve) => child.once("exit", resolve));
  child.kill("SIGTERM");
  await exited;
}

let serverPort;
let mojangPort;
before(async () => {
  mojangPort = await listen(mojang);
  serverPort = await freePort();
  base = `http://127.0.0.1:${serverPort}`;
  await startServer(serverPort, mojangPort);
});

after(async () => {
  await stopServer();
  mojang.close();
  rmSync(dataDir, { recursive: true, force: true });
});

// ------------------------------------------------------------------ Hilfen

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const uuidOf = (name) => PLAYERS[name].replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, "$1-$2-$3-$4-$5");

async function call(method, path, body, token) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, body: await response.json() };
}

/** Wie salami-core: Challenge holen, bei „Mojang“ anmelden, Freunde-Sitzung holen. */
async function login(name, { join = true } = {}) {
  const { body } = await call("POST", "/v1/auth/challenge");
  if (join) joined.add(`${name}|${body.serverId}`);
  return call("POST", "/v1/friends/session", { name, serverId: body.serverId });
}

/** Angemeldeter Spieler mit Abkürzungen für die API. */
async function player(name) {
  const res = await login(name);
  assert.equal(res.status, 200);
  const p = {
    ...res.body,
    get: (path) => call("GET", path, undefined, p.token),
    post: (path, body) => call("POST", path, body, p.token),
    events: () => openEvents(p.token),
  };
  return p;
}

/** Live-Verbindung wie salami-core: sammelt die Ereignisse, `next` wartet auf ein passendes. */
async function openEvents(token) {
  const controller = new AbortController();
  const response = await fetch(`${base}/v1/friends/events`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal: controller.signal,
  });
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
  }
  return {
    response,
    events,
    close: () => controller.abort(),
    async next(match, ms = 2_000) {
      for (const deadline = Date.now() + ms; Date.now() < deadline; ) {
        const index = events.findIndex(match);
        if (index >= 0) return events.splice(index, 1)[0];
        await sleep(20);
      }
      throw new Error("kein passendes Live-Ereignis");
    },
  };
}

const presenceOf = (uuid) => (e) => e.type === "presence" && e.uuid === uuid;
const ofType = (type) => (e) => e.type === type;
const NOT_LOGGED_IN = { status: 401, body: { error: "nicht angemeldet" } };

/** Werden in den Tests nacheinander aufgebaut (die Tests laufen der Reihe nach). */
let sal;
let pep;
let brot;

// ------------------------------------------------------------------ Tests

test("Sitzung: Token nur mit Mojang-Anmeldung, ohne gültiges Token 401", async () => {
  assert.equal((await login("Salamander", { join: false })).status, 401);

  sal = await player("Salamander");
  assert.match(sal.token, /^[A-Za-z0-9_-]{43}$/); // 32 Bytes base64url
  assert.equal(sal.uuid, uuidOf("Salamander"));
  assert.equal(sal.name, "Salamander");

  assert.deepEqual(await call("GET", "/v1/friends"), NOT_LOGGED_IN);
  assert.deepEqual(await call("GET", "/v1/friends", undefined, "falsch"), NOT_LOGGED_IN);
  assert.deepEqual(await call("POST", "/v1/friends/request", { name: "Pepper_99" }, "falsch"), NOT_LOGGED_IN);
  const events = await openEvents();
  assert.equal(events.response.status, 401);
  assert.deepEqual(await events.response.json(), { error: "nicht angemeldet" });
  assert.equal((await sal.get("/v1/friends/gibtsnicht")).status, 404);

  assert.deepEqual((await sal.get("/v1/friends")).body, {
    me: { uuid: sal.uuid, name: "Salamander" },
    friends: [],
    incoming: [],
    outgoing: [],
    unread: {},
  });

  // höchstens 5 Tokens pro Spieler: das älteste fällt weg
  const tokens = [sal.token];
  for (let i = 0; i < 5; i++) tokens.push((await login("Salamander")).body.token);
  assert.deepEqual(await call("GET", "/v1/friends", undefined, tokens[0]), NOT_LOGGED_IN);
  for (const token of tokens.slice(1)) assert.equal((await call("GET", "/v1/friends", undefined, token)).status, 200);
  sal.token = tokens[5];

  pep = await player("Pepper_99");
  brot = await player("Brotzeit");
});

test("Freundesanfrage per Name: unbekannt 404, Groß-/Kleinschreibung egal, ablehnen und zurückziehen", async () => {
  // Kaese hat sich noch nie angemeldet
  assert.deepEqual(await sal.post("/v1/friends/request", { name: "Kaese" }), {
    status: 404,
    body: { error: "Kaese nutzt den Salami Client nicht oder war noch nie online" },
  });
  assert.equal((await sal.post("/v1/friends/request", { name: "SALAMANDER" })).status, 400);
  assert.equal((await sal.post("/v1/friends/request", {})).status, 400);
  assert.equal((await sal.post("/v1/friends/request", { name: "kein name!" })).status, 400);

  const requested = { status: 200, body: { status: "requested", uuid: pep.uuid, name: "Pepper_99" } };
  assert.deepEqual(await sal.post("/v1/friends/request", { name: "pepper_99" }), requested);
  assert.deepEqual(await sal.post("/v1/friends/request", { name: "PEPPER_99" }), requested, "nochmal: nichts Neues");

  const mine = (await sal.get("/v1/friends")).body;
  assert.equal(mine.outgoing.length, 1);
  assert.deepEqual(Object.keys(mine.outgoing[0]).sort(), ["at", "name", "uuid"]);
  assert.equal(mine.outgoing[0].uuid, pep.uuid);
  assert.equal(mine.outgoing[0].name, "Pepper_99");
  assert.ok(!Number.isNaN(Date.parse(mine.outgoing[0].at)));
  const theirs = (await pep.get("/v1/friends")).body;
  assert.deepEqual(theirs.incoming, [{ uuid: sal.uuid, name: "Salamander", at: mine.outgoing[0].at }]);

  // ablehnen
  assert.deepEqual((await pep.post("/v1/friends/deny", { uuid: sal.uuid })).body, { status: "none" });
  assert.deepEqual((await sal.get("/v1/friends")).body.outgoing, []);
  assert.deepEqual((await pep.get("/v1/friends")).body.incoming, []);
  assert.deepEqual(await pep.post("/v1/friends/deny", { uuid: sal.uuid }), { status: 200, body: { status: "none" } });
  assert.equal((await pep.post("/v1/friends/deny", { uuid: "kaputt" })).status, 400);

  // zurückziehen (UUID in Großbuchstaben geht auch)
  await sal.post("/v1/friends/request", { name: "Pepper_99" });
  assert.deepEqual((await sal.post("/v1/friends/deny", { uuid: pep.uuid.toUpperCase() })).body, { status: "none" });
  assert.deepEqual((await pep.get("/v1/friends")).body.incoming, []);

  // annehmen nur mit offener Anfrage
  assert.equal((await pep.post("/v1/friends/accept", { uuid: sal.uuid })).status, 404);
  assert.equal((await pep.post("/v1/friends/accept", {})).status, 400);
  await sal.post("/v1/friends/request", { name: "Pepper_99" });
  assert.equal((await sal.post("/v1/friends/accept", { uuid: pep.uuid })).status, 404, "eigene Anfrage nicht annehmbar");
  assert.deepEqual(await pep.post("/v1/friends/accept", { uuid: sal.uuid }), {
    status: 200,
    body: { status: "friends", uuid: sal.uuid, name: "Salamander" },
  });

  const view = (await sal.get("/v1/friends")).body;
  assert.equal(view.friends.length, 1);
  assert.deepEqual(Object.keys(view.friends[0]).sort(), ["name", "online", "since", "status", "uuid"]);
  assert.equal(view.friends[0].uuid, pep.uuid);
  assert.equal(view.friends[0].online, false);
  assert.deepEqual(view.outgoing, []);
  assert.deepEqual((await pep.get("/v1/friends")).body.friends.map((f) => f.uuid), [sal.uuid]);
  assert.deepEqual((await pep.get("/v1/friends")).body.incoming, []);

  // schon befreundet
  assert.equal((await sal.post("/v1/friends/request", { name: "Pepper_99" })).status, 409);
  assert.equal((await pep.post("/v1/friends/request", { name: "salamander" })).status, 409);
});

test("gegenseitige Anfrage: sofort befreundet", async () => {
  assert.equal((await brot.post("/v1/friends/request", { name: "Salamander" })).body.status, "requested");
  assert.deepEqual((await sal.post("/v1/friends/request", { name: "BROTZEIT" })).body, {
    status: "friends",
    uuid: brot.uuid,
    name: "Brotzeit",
  });
  const view = (await sal.get("/v1/friends")).body;
  assert.deepEqual(view.friends.map((f) => f.name), ["Brotzeit", "Pepper_99"], "nach Namen sortiert");
  assert.deepEqual(view.incoming, []);
  assert.deepEqual(view.outgoing, []);
  assert.deepEqual((await brot.get("/v1/friends")).body.outgoing, []);
});

test("Nachrichten: nur unter Freunden, bereinigt, 1–256 Zeichen, ungelesen", async () => {
  // Pepper und Brotzeit sind keine Freunde
  const notFriends = { status: 403, body: { error: "ihr seid keine Freunde" } };
  assert.deepEqual(await pep.post("/v1/friends/messages", { to: brot.uuid, text: "hi" }), notFriends);
  assert.deepEqual(await pep.get(`/v1/friends/messages?with=${brot.uuid}`), notFriends);
  assert.equal((await pep.get("/v1/friends/messages")).status, 400);

  const first = await sal.post("/v1/friends/messages", { to: pep.uuid, text: " §Hallo\u0000 Welt\u001f!\u007f\u0085\n " });
  assert.equal(first.status, 200);
  const { message } = first.body;
  assert.deepEqual(Object.keys(message).sort(), ["at", "from", "id", "text", "to"]);
  assert.equal(message.text, "Hallo Welt!");
  assert.equal(message.from, sal.uuid);
  assert.equal(message.to, pep.uuid);
  assert.match(message.id, /^[0-9a-f]{12}$/);
  assert.ok(!Number.isNaN(Date.parse(message.at)));

  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: "Grüße 😀" })).body.message.text, "Grüße 😀");
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: "§\u0000\n " })).status, 400, "leer");
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: "x".repeat(257) })).status, 400, "zu lang");
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: 5 })).status, 400);
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid })).status, 400);
  const longest = "§".repeat(10) + "x".repeat(256); // nach dem Bereinigen genau 256
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: longest })).status, 200);

  // ungelesen beim Empfänger, Verlauf älteste zuerst, Abrufen setzt zurück
  assert.deepEqual((await pep.get("/v1/friends")).body.unread, { [sal.uuid]: 3 });
  assert.deepEqual((await sal.get("/v1/friends")).body.unread, {});
  const history = (await pep.get(`/v1/friends/messages?with=${sal.uuid}`)).body.messages;
  assert.deepEqual(history.map((m) => m.text), ["Hallo Welt!", "Grüße 😀", "x".repeat(256)]);
  assert.deepEqual(history[0], message);
  assert.equal(new Set(history.map((m) => m.id)).size, 3);
  assert.deepEqual((await pep.get("/v1/friends")).body.unread, {});
  // derselbe Verlauf von der anderen Seite
  assert.deepEqual((await sal.get(`/v1/friends/messages?with=${pep.uuid}`)).body.messages, history);

  // als gelesen markieren
  await pep.post("/v1/friends/messages", { to: sal.uuid, text: "Servus" });
  assert.deepEqual((await sal.get("/v1/friends")).body.unread, { [pep.uuid]: 1 });
  assert.deepEqual(await sal.post("/v1/friends/read", { with: pep.uuid }), { status: 200, body: { ok: true } });
  assert.deepEqual((await sal.get("/v1/friends")).body.unread, {});
});

test("Nachrichten: höchstens 8 in 10 Sekunden", async () => {
  for (let i = 0; i < 8; i++) {
    assert.equal((await brot.post("/v1/friends/messages", { to: sal.uuid, text: `Nachricht ${i}` })).status, 200);
  }
  assert.deepEqual(await brot.post("/v1/friends/messages", { to: sal.uuid, text: "eine zu viel" }), {
    status: 429,
    body: { error: "zu schnell, kurz warten" },
  });
  assert.deepEqual((await sal.get("/v1/friends")).body.unread, { [brot.uuid]: 8 });
});

test("Live: ready, Anfragen, Freundschaft, Nachrichten an beide Seiten, removed", async () => {
  const salLive = await sal.events();
  const opened = [salLive];
  try {
    assert.equal(salLive.response.status, 200);
    assert.match(salLive.response.headers.get("content-type"), /text\/event-stream/);
    assert.equal(salLive.response.headers.get("x-accel-buffering"), "no");
    assert.deepEqual(await salLive.next(() => true), { type: "ready" }, "erstes Ereignis ist ready");

    // Pepper kommt online → Salamander (Freund) erfährt es
    const pepLive = await pep.events();
    opened.push(pepLive);
    assert.deepEqual(await pepLive.next(() => true), { type: "ready" });
    assert.deepEqual(await salLive.next(presenceOf(pep.uuid)), { type: "presence", uuid: pep.uuid, name: "Pepper_99", online: true, status: "game" });
    // online zuerst, dann nach Namen
    const list = (await sal.get("/v1/friends")).body.friends;
    assert.deepEqual(list.map((f) => [f.name, f.online]), [["Pepper_99", true], ["Brotzeit", false]]);
    // wer online ist, sehen nur Freunde
    assert.deepEqual((await brot.get("/v1/friends")).body.friends.map((f) => [f.name, f.online]), [["Salamander", true]]);

    // Freundesanfrage kommt live an
    await brot.post("/v1/friends/request", { name: "Pepper_99" });
    assert.deepEqual(await pepLive.next(ofType("request")), { type: "request", uuid: brot.uuid, name: "Brotzeit" });

    const brotLive = await brot.events();
    opened.push(brotLive);
    await brotLive.next(ofType("ready"));
    assert.deepEqual(await salLive.next(presenceOf(brot.uuid)), { type: "presence", uuid: brot.uuid, name: "Brotzeit", online: true, status: "game" });

    // annehmen → beide Seiten, mit Online-Status des anderen
    await pep.post("/v1/friends/accept", { uuid: brot.uuid });
    assert.deepEqual(await pepLive.next(ofType("friends")), { type: "friends", uuid: brot.uuid, name: "Brotzeit", online: true, status: "game" });
    assert.deepEqual(await brotLive.next(ofType("friends")), { type: "friends", uuid: pep.uuid, name: "Pepper_99", online: true, status: "game" });
    assert.equal(pepLive.events.some(ofType("presence")), false, "vor der Freundschaft kein Online-Status");

    // Nachricht: an den Empfänger und an alle Verbindungen des Absenders, sonst niemanden
    const salSecond = await sal.events();
    opened.push(salSecond);
    await salSecond.next(ofType("ready"));
    const { message } = (await sal.post("/v1/friends/messages", { to: pep.uuid, text: "live?" })).body;
    const expected = { type: "message", message, name: "Salamander" };
    assert.deepEqual(await pepLive.next(ofType("message")), expected);
    assert.deepEqual(await salLive.next(ofType("message")), expected);
    assert.deepEqual(await salSecond.next(ofType("message")), expected);
    assert.equal(brotLive.events.some(ofType("message")), false);

    // Freundschaft beenden → removed beim anderen
    assert.deepEqual((await pep.post("/v1/friends/remove", { uuid: brot.uuid })).body, { status: "none" });
    assert.deepEqual(await brotLive.next(ofType("removed")), { type: "removed", uuid: pep.uuid });
    assert.equal(pepLive.events.some(ofType("removed")), false);

    // Anfrage ablehnen → removed beim Anfragenden
    await brot.post("/v1/friends/request", { name: "Pepper_99" });
    await pepLive.next(ofType("request"));
    await pep.post("/v1/friends/deny", { uuid: brot.uuid });
    assert.deepEqual(await brotLive.next(ofType("removed")), { type: "removed", uuid: pep.uuid });

    // Anfrage zurückziehen → removed beim Angefragten
    await brot.post("/v1/friends/request", { name: "Pepper_99" });
    await pepLive.next(ofType("request"));
    await brot.post("/v1/friends/deny", { uuid: pep.uuid });
    assert.deepEqual(await pepLive.next(ofType("removed")), { type: "removed", uuid: brot.uuid });
  } finally {
    for (const live of opened) live.close();
    await sleep(GRACE_MS + 300); // alle wieder offline
  }
});

test("Live: offline erst nach der Gnadenfrist, kurzes Neuverbinden flackert nicht", async () => {
  const salLive = await sal.events();
  let pepLive = await pep.events();
  try {
    await salLive.next(ofType("ready"));
    assert.equal((await salLive.next(presenceOf(pep.uuid))).online, true);

    const closedAt = Date.now();
    pepLive.close();
    await assert.rejects(salLive.next(presenceOf(pep.uuid), GRACE_MS / 2), "noch in der Gnadenfrist");
    assert.equal((await sal.get("/v1/friends")).body.friends.find((f) => f.uuid === pep.uuid).online, true);
    assert.deepEqual(await salLive.next(presenceOf(pep.uuid)), { type: "presence", uuid: pep.uuid, name: "Pepper_99", online: false, status: null });
    assert.ok(Date.now() - closedAt >= GRACE_MS - 50, "offline kam zu früh");
    assert.equal((await sal.get("/v1/friends")).body.friends.find((f) => f.uuid === pep.uuid).online, false);

    // wieder online, Verbindung bricht ab und kommt gleich wieder: keine Meldung
    pepLive = await pep.events();
    assert.equal((await salLive.next(presenceOf(pep.uuid))).online, true);
    pepLive.close();
    pepLive = await pep.events();
    await pepLive.next(ofType("ready"));
    await assert.rejects(salLive.next(presenceOf(pep.uuid), GRACE_MS + 300), "kein Flackern");
    assert.equal((await sal.get("/v1/friends")).body.friends.find((f) => f.uuid === pep.uuid).online, true);

    pepLive.close();
    assert.equal((await salLive.next(presenceOf(pep.uuid))).online, false);
  } finally {
    salLive.close();
    pepLive.close();
    await sleep(GRACE_MS + 300);
  }
});

test("Live: höchstens 5 Verbindungen pro Spieler", async () => {
  const opened = [];
  try {
    for (let i = 0; i < 5; i++) {
      const live = await brot.events();
      opened.push(live);
      assert.equal(live.response.status, 200);
    }
    const sixth = await brot.events();
    assert.equal(sixth.response.status, 503);
    assert.deepEqual(await sixth.response.json(), { error: "zu viele Verbindungen" });
    // andere Spieler betrifft das nicht
    const other = await pep.events();
    opened.push(other);
    assert.equal(other.response.status, 200);
  } finally {
    for (const live of opened) live.close();
    await sleep(GRACE_MS + 300);
  }
});

test("Live: Cape- und Freunde-Verbindungen bleiben getrennt", async () => {
  const friendsLive = await sal.events();
  const controller = new AbortController();
  const capes = await fetch(`${base}/v1/capes/events`, { signal: controller.signal });
  const reader = capes.body.getReader();
  let capeText = "";
  const reading = (async () => {
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) return;
        capeText += new TextDecoder().decode(value);
      }
    } catch {}
  })();
  try {
    await friendsLive.next(ofType("ready"));
    // Cape-Wechsel: nur auf der Cape-Verbindung
    const { body } = await call("POST", "/v1/auth/challenge");
    joined.add(`Salamander|${body.serverId}`);
    await call("PUT", "/v1/capes/me", { name: "Salamander", serverId: body.serverId, cape: { builtin: "salami" } });
    for (let i = 0; i < 50 && !capeText.includes(sal.uuid); i++) await sleep(20);
    assert.match(capeText, /data: \{"uuid":"[^"]+","cape":\{"builtin":"salami"\}\}/);
    // Nachricht: nur auf der Freunde-Verbindung
    await sal.post("/v1/friends/messages", { to: pep.uuid, text: "nur für Freunde" });
    await friendsLive.next(ofType("message"));
    assert.equal(friendsLive.events.length, 0, "Cape-Ereignis auf der Freunde-Verbindung");
    assert.equal(capeText.includes("nur für Freunde"), false);
  } finally {
    friendsLive.close();
    controller.abort();
    await reading;
    await sleep(GRACE_MS + 300);
  }
});

test("Entfernen löscht das Gespräch auf beiden Seiten", async () => {
  assert.ok((await sal.get(`/v1/friends/messages?with=${pep.uuid}`)).body.messages.length > 0);
  await pep.post("/v1/friends/messages", { to: sal.uuid, text: "bis dann" });
  await sal.post("/v1/friends/messages", { to: pep.uuid, text: "tschüss" });
  assert.equal((await sal.get("/v1/friends")).body.unread[pep.uuid], 1);

  assert.deepEqual(await sal.post("/v1/friends/remove", { uuid: pep.uuid }), { status: 200, body: { status: "none" } });
  assert.deepEqual((await sal.get("/v1/friends")).body.friends.map((f) => f.name), ["Brotzeit"]);
  assert.deepEqual((await pep.get("/v1/friends")).body.friends, []);
  assert.equal((await sal.get("/v1/friends")).body.unread[pep.uuid], undefined);
  assert.deepEqual((await pep.get("/v1/friends")).body.unread, {});
  assert.equal((await sal.get(`/v1/friends/messages?with=${pep.uuid}`)).status, 403);
  assert.equal((await sal.post("/v1/friends/messages", { to: pep.uuid, text: "hallo?" })).status, 403);
  assert.deepEqual(await sal.post("/v1/friends/remove", { uuid: pep.uuid }), { status: 200, body: { status: "none" } }, "nochmal: egal");

  // wieder befreundet: das alte Gespräch bleibt weg
  await sal.post("/v1/friends/request", { name: "Pepper_99" });
  await pep.post("/v1/friends/accept", { uuid: sal.uuid });
  assert.deepEqual((await sal.get(`/v1/friends/messages?with=${pep.uuid}`)).body.messages, []);
  assert.deepEqual((await pep.get(`/v1/friends/messages?with=${sal.uuid}`)).body.messages, []);
});

test("Daten überstehen einen Neustart, Tokens nur als Hash, Altes wird aufgeräumt", async () => {
  await sleep(700); // Speichern ist um 500 ms verzögert
  await stopServer();

  const raw = readFileSync(friendsFile, "utf-8");
  assert.equal(raw.includes(sal.token), false, "Token im Klartext gespeichert");
  assert.ok(raw.includes(createHash("sha256").update(sal.token).digest("hex")));

  // Altes hineinschmuggeln: abgelaufenes Token, 40 Tage alte Nachricht und Anfrage
  const stored = JSON.parse(raw);
  const old = new Date(Date.now() - 40 * 86_400_000).toISOString();
  stored.sessions[createHash("sha256").update("abgelaufen").digest("hex")] = { uuid: sal.uuid, expires: Date.now() - 1_000 };
  const key = [sal.uuid, brot.uuid].sort().join("|");
  stored.messages[key].unshift({ id: "000000000001", from: brot.uuid, to: sal.uuid, text: "uralt", at: old });
  stored.requests.push({ from: pep.uuid, to: brot.uuid, at: old });
  writeFileSync(friendsFile, JSON.stringify(stored));

  await startServer(serverPort, mojangPort);

  // alte Tokens gelten weiter, Freunde, Ungelesenes und Verläufe sind noch da
  const view = (await sal.get("/v1/friends")).body;
  assert.deepEqual(view.friends.map((f) => f.name), ["Brotzeit", "Pepper_99"]);
  assert.equal(view.unread[brot.uuid], 8);
  const history = (await sal.get(`/v1/friends/messages?with=${brot.uuid}`)).body.messages;
  assert.deepEqual(history.map((m) => m.text), Array.from({ length: 8 }, (_, i) => `Nachricht ${i}`));
  assert.deepEqual(await call("GET", "/v1/friends", undefined, "abgelaufen"), NOT_LOGGED_IN);
  assert.deepEqual((await brot.get("/v1/friends")).body.incoming, []);

  // neue Nachrichten-IDs bleiben eindeutig
  const { message } = (await brot.post("/v1/friends/messages", { to: sal.uuid, text: "wieder da" })).body;
  assert.ok(history.every((m) => m.id < message.id));
});

// ------------------------------------------------------------------ Bilder und Clips

const PNG_HEAD = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const MP4_HEAD = Buffer.from([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d]);
/** Datei mit passendem Anfang und `size` Bytes. */
const fileOf = (head, size) => Buffer.concat([head, Buffer.alloc(size - head.length, 7)]);

async function upload(p, to, type, bytes, name = "screenshot.png") {
  const response = await fetch(`${base}/v1/friends/media?to=${to}&name=${encodeURIComponent(name)}`, {
    method: "POST",
    headers: { "Content-Type": type, ...(p ? { Authorization: `Bearer ${p.token}` } : {}) },
    body: bytes,
  });
  return { status: response.status, body: await response.json() };
}

async function download(p, id) {
  const response = await fetch(`${base}/v1/friends/media/${id}`, { headers: { Authorization: `Bearer ${p.token}` } });
  return { status: response.status, type: response.headers.get("content-type"), bytes: Buffer.from(await response.arrayBuffer()) };
}

const mediaFiles = () => readdirSync(join(dataDir, "friends-media"));

test("Bilder und Clips: nur an Freunde, Typ und Größe geprüft, nur die beiden können sie laden", async () => {
  const kaese = await player("Kaese");
  const png = fileOf(PNG_HEAD, 2_000);

  assert.equal((await upload(null, pep.uuid, "image/png", png)).status, 401);
  assert.deepEqual(await upload(kaese, sal.uuid, "image/png", png), { status: 403, body: { error: "ihr seid keine Freunde" } });
  assert.equal((await upload(sal, "kein-uuid", "image/png", png)).status, 400);
  assert.deepEqual(await upload(sal, pep.uuid, "application/zip", png), { status: 415, body: { error: "nur PNG, JPEG, WebP, MP4 und WebM" } });
  assert.deepEqual(await upload(sal, pep.uuid, "image/jpeg", png), {
    status: 400,
    body: { error: "die Datei passt nicht zum angegebenen Typ" },
  });
  // im Test ist 1 MB die Grenze
  const big = await upload(sal, pep.uuid, "image/png", fileOf(PNG_HEAD, 1024 * 1024 + 1));
  assert.deepEqual(big, { status: 413, body: { error: "Bild ist zu groß (höchstens 1 MB)" } });
  assert.deepEqual(mediaFiles(), [], "abgelehnte Uploads hinterlassen keine Dateien");

  const pepLive = await pep.events();
  try {
    await pepLive.next(ofType("ready"));
    const sent = await upload(sal, pep.uuid, "image/png", png, "..\\mein §bild.png");
    assert.equal(sent.status, 200);
    const { message } = sent.body;
    assert.deepEqual(Object.keys(message).sort(), ["at", "from", "id", "media", "text", "to"]);
    assert.match(message.media.id, /^[0-9a-f]{32}$/);
    assert.deepEqual(message.media, { id: message.media.id, kind: "image", mime: "image/png", size: 2_000, name: "..mein bild.png" });
    assert.equal(message.text, "[Bild] ..mein bild.png", "Text für Clients ohne Anhänge");
    assert.deepEqual(await pepLive.next(ofType("message")), { type: "message", message, name: "Salamander" });
    assert.equal((await pep.get("/v1/friends")).body.unread[sal.uuid], 1);

    for (const p of [sal, pep]) {
      const got = await download(p, message.media.id);
      assert.equal(got.status, 200);
      assert.equal(got.type, "image/png");
      assert.ok(got.bytes.equals(png));
    }
    assert.equal((await download(brot, message.media.id)).status, 404, "Freund von Salamander, aber nicht im Gespräch");
    assert.equal((await download(kaese, message.media.id)).status, 404);
    assert.equal((await fetch(`${base}/v1/friends/media/${message.media.id}`)).status, 401);
    assert.equal((await download(sal, "0".repeat(32))).status, 404);

    const clip = await upload(pep, sal.uuid, "video/mp4", fileOf(MP4_HEAD, 5_000), "clip.mp4");
    assert.equal(clip.status, 200);
    assert.equal(clip.body.message.media.kind, "clip");
    assert.equal(clip.body.message.text, "[Clip] clip.mp4");
    assert.deepEqual(mediaFiles().sort(), [`${message.media.id}.png`, `${clip.body.message.media.id}.mp4`].sort());

    // im Verlauf stehen die Anhänge mit drin
    const history = (await pep.get(`/v1/friends/messages?with=${sal.uuid}`)).body.messages;
    assert.deepEqual(history.slice(-2).map((m) => m.media?.kind), ["image", "clip"]);
  } finally {
    pepLive.close();
    await sleep(GRACE_MS + 300);
  }
});

test("Bilder und Clips: eigenes Kontingent – die ältesten laufen ab, die Nachricht bleibt", async () => {
  // im Test 1 MB pro Spieler
  const first = (await upload(brot, sal.uuid, "image/png", fileOf(PNG_HEAD, 600_000), "eins.png")).body.message;
  assert.equal((await download(sal, first.media.id)).status, 200);
  const second = (await upload(brot, sal.uuid, "image/png", fileOf(PNG_HEAD, 600_000), "zwei.png")).body.message;
  assert.equal((await download(sal, first.media.id)).status, 404, "das ältere musste Platz machen");
  assert.equal((await download(sal, second.media.id)).status, 200);
  const history = (await sal.get(`/v1/friends/messages?with=${brot.uuid}`)).body.messages;
  assert.equal(history.find((m) => m.id === first.id).media.expired, true);
  assert.equal(history.find((m) => m.id === second.id).media.expired, undefined);
  assert.equal(mediaFiles().includes(`${first.media.id}.png`), false);
});

test("Online-Status: im Launcher oder im Spiel, das Spiel hat Vorrang", async () => {
  const live = async (p, query = "") => {
    const controller = new AbortController();
    const response = await fetch(`${base}/v1/friends/events${query}`, {
      headers: { Authorization: `Bearer ${p.token}` },
      signal: controller.signal,
    });
    assert.equal(response.status, 200);
    response.body.getReader().read().catch(() => {});
    return () => controller.abort();
  };
  const statusOfPep = async () => (await sal.get("/v1/friends")).body.friends.find((f) => f.uuid === pep.uuid).status;
  const salLive = await sal.events();
  let closeLauncher = () => {};
  let closeGame = () => {};
  try {
    await salLive.next(ofType("ready"));
    assert.equal(await statusOfPep(), null);

    closeLauncher = await live(pep, "?client=launcher");
    assert.deepEqual(await salLive.next(presenceOf(pep.uuid)), {
      type: "presence", uuid: pep.uuid, name: "Pepper_99", online: true, status: "launcher",
    });
    assert.equal(await statusOfPep(), "launcher");

    closeGame = await live(pep);
    assert.equal((await salLive.next(presenceOf(pep.uuid))).status, "game");
    assert.equal(await statusOfPep(), "game");

    closeGame();
    assert.deepEqual(await salLive.next(presenceOf(pep.uuid)), {
      type: "presence", uuid: pep.uuid, name: "Pepper_99", online: true, status: "launcher",
    });

    closeLauncher();
    assert.deepEqual(await salLive.next(presenceOf(pep.uuid)), {
      type: "presence", uuid: pep.uuid, name: "Pepper_99", online: false, status: null,
    });
  } finally {
    closeLauncher();
    closeGame();
    salLive.close();
    await sleep(GRACE_MS + 300);
  }
});

test("Bilder und Clips überstehen einen Neustart; Entfernen löscht sie mit dem Gespräch", async () => {
  const before = mediaFiles();
  assert.equal(before.length, 3); // Bild und Clip (Salamander/Pepper), ein Bild von Brotzeit
  await sleep(700);
  await stopServer();
  // eine verwaiste Datei (abgebrochener Upload) verschwindet beim Start
  writeFileSync(join(dataDir, "friends-media", `${"f".repeat(32)}.part`), "rest");
  await startServer(serverPort, mojangPort);
  assert.deepEqual(mediaFiles().sort(), before.sort());

  const history = (await sal.get(`/v1/friends/messages?with=${pep.uuid}`)).body.messages;
  const image = history.find((m) => m.media?.kind === "image");
  assert.equal((await download(pep, image.media.id)).status, 200);

  await pep.post("/v1/friends/remove", { uuid: sal.uuid });
  assert.equal((await download(pep, image.media.id)).status, 404);
  assert.equal(mediaFiles().length, 1, "nur das Bild von Brotzeit bleibt");
});
