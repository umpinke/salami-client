// Tests für den Cape-Server: `node --test server/capes/test.mjs`
// Startet server.mjs mit einem Schein-Mojang (hasJoined) und einem leeren Datenordner.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, test } from "node:test";
import zlib from "node:zlib";
import { capeId, decodeCape, encodeRgba } from "./png.mjs";

const ADMIN = "test-admin-token-1234567890";
const PLAYERS = {
  Salamander: "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
  Pepper_99: "11111111222233334444555555555555",
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
const dataDir = mkdtempSync(join(tmpdir(), "salami-capes-test-"));

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
      ADMIN_TOKENS: `leo:${ADMIN}`,
      MOJANG_SESSION_URL: `http://127.0.0.1:${mojangPort}`,
      RATE_PER_MINUTE: "1000",
      UPLOADS_PER_DAY: "3",
    },
    stdio: ["ignore", "pipe", "inherit"],
  });
  child.stdout.resume();
  for (let i = 0; i < 100; i++) {
    try {
      if ((await fetch(`${base}/health`)).ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 50));
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

async function call(method, path, body, headers = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(body !== undefined ? { "Content-Type": "application/json" } : {}), ...headers },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const type = response.headers.get("content-type") ?? "";
  return { status: response.status, body: type.includes("json") ? await response.json() : Buffer.from(await response.arrayBuffer()) };
}

const admin = (method, path, body) => call(method, path, body, { Authorization: `Bearer ${ADMIN}` });

/** Wie salami-core: Challenge holen, bei „Mojang“ anmelden, Cape setzen. */
async function setCape(name, cape, { join = true } = {}) {
  const { body } = await call("POST", "/v1/auth/challenge");
  if (join) joined.add(`${name}|${body.serverId}`);
  return call("PUT", "/v1/capes/me", { name, serverId: body.serverId, cape });
}

const uuidOf = (name) =>
  PLAYERS[name].replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, "$1-$2-$3-$4-$5");

/** Live-Verbindung wie salami-core: sammelt die Ereignisse, `next` wartet auf ein passendes. */
async function openEvents() {
  const controller = new AbortController();
  const response = await fetch(`${base}/v1/capes/events`, { signal: controller.signal });
  const events = [];
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
  return {
    response,
    events,
    close: () => controller.abort(),
    async next(match, ms = 2_000) {
      for (const deadline = Date.now() + ms; Date.now() < deadline; ) {
        const index = events.findIndex(match);
        if (index >= 0) return events.splice(index, 1)[0];
        await new Promise((r) => setTimeout(r, 20));
      }
      throw new Error("kein passendes Live-Ereignis");
    },
  };
}

function capePixels(width, seed) {
  const height = width / 2;
  const rgba = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    rgba[i * 4] = (i * 7 + seed) & 0xff;
    rgba[i * 4 + 1] = (i * 13 + seed * 3) & 0xff;
    rgba[i * 4 + 2] = (i * 29) & 0xff;
    rgba[i * 4 + 3] = i % 5 === 0 ? 0 : 255;
  }
  return { width, height, rgba };
}

/** PNG mit beliebigem Filter je Zeile und Farbtyp 2 oder 6 – prüft das Rückgängigmachen der Filter. */
function filteredPng(width, height, pixels, channels) {
  const stride = width * channels;
  const rows = [];
  for (let y = 0; y < height; y++) {
    const filter = y % 5;
    const row = Buffer.alloc(stride + 1);
    row[0] = filter;
    for (let x = 0; x < stride; x++) {
      const cur = pixels[y * stride + x];
      const a = x >= channels ? pixels[y * stride + x - channels] : 0;
      const b = y > 0 ? pixels[(y - 1) * stride + x] : 0;
      const c = x >= channels && y > 0 ? pixels[(y - 1) * stride + x - channels] : 0;
      const p = a + b - c;
      const pr = Math.abs(p - a) <= Math.abs(p - b) && Math.abs(p - a) <= Math.abs(p - c) ? a : Math.abs(p - b) <= Math.abs(p - c) ? b : c;
      const predict = [0, a, b, (a + b) >> 1, pr][filter];
      row[x + 1] = (cur - predict) & 0xff;
    }
    rows.push(row);
  }
  const png = encodeRgba(width, height, Buffer.alloc(width * height * 4));
  // IHDR/IEND aus encodeRgba wiederverwenden, Farbtyp und IDAT ersetzen
  const ihdr = Buffer.from(png.subarray(8, 8 + 25));
  ihdr[8 + 9] = channels === 4 ? 6 : 2;
  const crcData = ihdr.subarray(4, 21);
  ihdr.writeUInt32BE(crc(crcData), 21);
  const data = zlib.deflateSync(Buffer.concat(rows));
  const idat = Buffer.alloc(12 + data.length);
  idat.writeUInt32BE(data.length, 0);
  idat.write("IDAT", 4, "latin1");
  data.copy(idat, 8);
  idat.writeUInt32BE(crc(Buffer.concat([Buffer.from("IDAT"), data])), 8 + data.length);
  return Buffer.concat([png.subarray(0, 8), ihdr, idat, png.subarray(png.length - 12)]);
}

function crc(buf) {
  let c = 0xffffffff;
  for (const byte of buf) {
    c ^= byte;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return (c ^ 0xffffffff) >>> 0;
}

// ------------------------------------------------------------------ Tests

test("png: dekodiert alle Filter und RGB/RGBA gleich", () => {
  const { width, height, rgba } = capePixels(64, 1);
  const rgb = Buffer.alloc(width * height * 3);
  for (let i = 0; i < width * height; i++) rgba.copy(rgb, i * 3, i * 4, i * 4 + 3);
  const fromRgba = decodeCape(filteredPng(width, height, rgba, 4));
  assert.deepEqual(fromRgba.rgba, rgba);
  const fromRgb = decodeCape(filteredPng(width, height, rgb, 3));
  assert.equal(fromRgb.rgba[3], 255);
  assert.deepEqual(fromRgb.rgba.subarray(4, 7), rgba.subarray(4, 7));
  // Neu kodiert: gleiche Pixel → gleiche ID
  const again = decodeCape(encodeRgba(width, height, rgba));
  assert.equal(capeId(again.width, again.height, again.rgba), capeId(width, height, rgba));
});

test("png: lehnt Nicht-Capes und Müll ab", () => {
  assert.throws(() => decodeCape(Buffer.from("kein bild")), /keine PNG/);
  assert.throws(() => decodeCape(encodeRgba(64, 64, Buffer.alloc(64 * 64 * 4))), /kein Cape-Format/);
  assert.throws(() => decodeCape(encodeRgba(100, 50, Buffer.alloc(100 * 50 * 4))), /kein Cape-Format/);
  const broken = Buffer.from(encodeRgba(64, 32, Buffer.alloc(64 * 32 * 4)));
  broken[40] ^= 0xff;
  assert.throws(() => decodeCape(broken));
});

test("Anmeldung: ohne Mojang-Join und mit verbrauchter serverId abgelehnt", async () => {
  assert.equal((await setCape("Salamander", { builtin: "salami" }, { join: false })).status, 401);
  const { body } = await call("POST", "/v1/auth/challenge");
  joined.add(`Salamander|${body.serverId}`);
  assert.equal((await call("PUT", "/v1/capes/me", { name: "Salamander", serverId: body.serverId, cape: null })).status, 200);
  joined.add(`Salamander|${body.serverId}`);
  assert.equal((await call("PUT", "/v1/capes/me", { name: "Salamander", serverId: body.serverId, cape: null })).status, 401);
  assert.equal((await call("PUT", "/v1/capes/me", { name: "Salamander", serverId: "frei-erfunden", cape: null })).status, 401);
});

test("mitgelieferte Capes sind sofort für alle sichtbar", async () => {
  const res = await setCape("Salamander", { builtin: "pepperoni" });
  assert.deepEqual(res.body, { status: "approved", builtin: "pepperoni" });
  const lookup = await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Salamander"), uuidOf("Pepper_99"), "kaputt"] });
  assert.deepEqual(lookup.body.capes, { [uuidOf("Salamander")]: { builtin: "pepperoni" } });
  assert.equal((await setCape("Salamander", { builtin: "gibtsnicht" })).status, 400);
});

test("eigenes Cape: erst nach Freigabe sichtbar", async () => {
  const pixels = capePixels(128, 2);
  const png = encodeRgba(pixels.width, pixels.height, pixels.rgba).toString("base64");
  const id = capeId(pixels.width, pixels.height, pixels.rgba);

  const res = await setCape("Pepper_99", { png });
  assert.deepEqual(res.body, { status: "pending", id });
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Pepper_99")] })).body.capes, {});
  assert.equal((await call("GET", `/v1/capes/img/${id}.png`)).status, 404);
  assert.deepEqual((await call("POST", "/v1/capes/status", { ids: [id] })).body.status, { [id]: { status: "pending" } });

  // Admin-Bereich nur mit Schlüssel
  assert.equal((await call("GET", "/v1/admin/capes")).status, 401);
  assert.equal((await call("GET", "/v1/admin/capes", undefined, { Authorization: "Bearer falsch" })).status, 401);
  assert.equal((await call("GET", `/v1/admin/img/${id}.png`)).status, 401);
  const list = await admin("GET", "/v1/admin/capes?status=pending");
  assert.equal(list.body.counts.pending, 1);
  assert.equal(list.body.capes[0].id, id);
  assert.equal(list.body.capes[0].submittedName, "Pepper_99");
  assert.deepEqual(list.body.capes[0].users, [{ uuid: uuidOf("Pepper_99"), name: "Pepper_99" }]);
  assert.equal((await admin("GET", `/v1/admin/img/${id}.png`)).status, 200);

  // Freigeben
  assert.equal((await admin("POST", `/v1/admin/capes/${id}/approve`)).body.status, "approved");
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Pepper_99")] })).body.capes, {
    [uuidOf("Pepper_99")]: { id },
  });
  const image = await call("GET", `/v1/capes/img/${id}.png`);
  assert.equal(image.status, 200);
  const served = decodeCape(image.body);
  assert.equal(capeId(served.width, served.height, served.rgba), id);

  // Dasselbe Bild von jemand anderem: sofort freigegeben, keine zweite Prüfung
  assert.deepEqual((await setCape("Salamander", { png })).body, { status: "approved", id });

  // Sperren mit Grund
  await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "Bitte ohne Werbung" });
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Pepper_99")] })).body.capes, {});
  assert.deepEqual((await call("POST", "/v1/capes/status", { ids: [id] })).body.status[id], {
    status: "rejected",
    reason: "Bitte ohne Werbung",
  });
  assert.deepEqual((await setCape("Pepper_99", { png })).body, { status: "rejected", id, reason: "Bitte ohne Werbung" });
});

test("ungeprüfte Capes, die keiner mehr trägt, verschwinden aus der Warteschlange", async () => {
  const pixels = capePixels(64, 9);
  const png = encodeRgba(pixels.width, pixels.height, pixels.rgba).toString("base64");
  const before = (await admin("GET", "/v1/admin/capes?status=pending")).body.counts.pending;
  await setCape("Salamander", { png });
  assert.equal((await admin("GET", "/v1/admin/capes?status=pending")).body.counts.pending, before + 1);
  await setCape("Salamander", { builtin: "board" });
  assert.equal((await admin("GET", "/v1/admin/capes?status=pending")).body.counts.pending, before);
  await setCape("Salamander", null);
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Salamander")] })).body.capes, {});
});

test("Live: Cape-Wechsel und Freigaben kommen sofort an", async () => {
  const live = await openEvents();
  try {
    assert.equal(live.response.status, 200);
    assert.match(live.response.headers.get("content-type"), /text\/event-stream/);
    assert.equal(live.response.headers.get("x-accel-buffering"), "no");
    const uuid = uuidOf("Pepper_99");
    const mine = (e) => e.uuid === uuid;

    await setCape("Pepper_99", { builtin: "board" });
    assert.deepEqual(await live.next(mine), { uuid, cape: { builtin: "board" } });

    // eigenes Bild: bis zur Prüfung sehen andere kein Cape …
    const pixels = capePixels(64, 40);
    const id = capeId(64, 32, pixels.rgba);
    await setCape("Pepper_99", { png: encodeRgba(64, 32, pixels.rgba).toString("base64") });
    assert.deepEqual(await live.next(mine), { uuid, cape: null });
    // … und nach der Freigabe sofort das Bild
    await admin("POST", `/v1/admin/capes/${id}/approve`);
    assert.deepEqual(await live.next(mine), { uuid, cape: { id } });

    await setCape("Pepper_99", null);
    assert.deepEqual(await live.next(mine), { uuid, cape: null });
    // nichts geändert → keine Meldung
    await setCape("Pepper_99", null);
    await assert.rejects(live.next(mine, 300));
  } finally {
    live.close();
  }
});

test("kaputte Bilder und Upload-Limit", async () => {
  assert.equal((await setCape("Salamander", { png: Buffer.from("kein bild").toString("base64") })).status, 422);
  const square = encodeRgba(64, 64, Buffer.alloc(64 * 64 * 4)).toString("base64");
  assert.equal((await setCape("Salamander", { png: square })).status, 422);
  // UPLOADS_PER_DAY=3 im Test; Salamander hat schon eins eingereicht
  for (let seed = 20; seed < 22; seed++) {
    const p = capePixels(64, seed);
    assert.equal((await setCape("Salamander", { png: encodeRgba(64, 32, p.rgba).toString("base64") })).status, 200);
  }
  const p = capePixels(64, 30);
  assert.equal((await setCape("Salamander", { png: encodeRgba(64, 32, p.rgba).toString("base64") })).status, 429);
});

test("Daten überstehen einen Neustart", async () => {
  await setCape("Salamander", { builtin: "salami" });
  await new Promise((r) => setTimeout(r, 700)); // Speichern ist um 500 ms verzögert
  await stopServer();
  await startServer(serverPort, mojangPort);
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Salamander")] })).body.capes, {
    [uuidOf("Salamander")]: { builtin: "salami" },
  });
});

test("Admin-Seite wird mit CSP ausgeliefert", async () => {
  const response = await fetch(`${base}/admin/`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-security-policy"), /script-src 'self'/);
  assert.match(await response.text(), /Salami Capes/);
  assert.equal((await fetch(`${base}/admin/app.js`)).status, 200);
  assert.equal((await fetch(`${base}/admin/../server.mjs`)).status, 404);
});

test("Admin-Sperre nach 10 falschen Schlüsseln", async () => {
  for (let i = 0; i < 10; i++) {
    await call("GET", "/v1/admin/me", undefined, { Authorization: `Bearer falsch-${i}` });
  }
  assert.equal((await call("GET", "/v1/admin/me", undefined, { Authorization: "Bearer falsch" })).status, 429);
  assert.equal((await admin("GET", "/v1/admin/me")).status, 429);
});
