// Tests für den Cape-Server: `node --test server/capes/test.mjs`
// Startet server.mjs mit einem Schein-Mojang (hasJoined) und einem leeren Datenordner.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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
  Brotzeit: "22222222333344445555666666666666",
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
/** Kopie des Emote-Katalogs, damit die Tests ihn ändern können, ohne die echte Datei anzufassen. */
const emotesFile = join(dataDir, "emotes-test.json");
copyFileSync(fileURLToPath(new URL("./emotes.json", import.meta.url)), emotesFile);
/** Ebenso die Hüte; die offiziellen Capes beginnen leer (Bilder im Ordner official-capes daneben). */
const hatsFile = join(dataDir, "hats-test.json");
copyFileSync(fileURLToPath(new URL("./hats.json", import.meta.url)), hatsFile);
const officialFile = join(dataDir, "official-capes.json");
const officialDir = join(dataDir, "official-capes");
mkdirSync(officialDir);
writeFileSync(officialFile, JSON.stringify({ capes: [] }));

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
      REPORTS_TO_HIDE: "2",
      EMOTES_FILE: emotesFile,
      HATS_FILE: hatsFile,
      OFFICIAL_CAPES_FILE: officialFile,
      CATALOG_RELOAD_MS: "100",
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
async function setCape(name, cape, { join = true, hat, minime } = {}) {
  const { body } = await call("POST", "/v1/auth/challenge");
  if (join) joined.add(`${name}|${body.serverId}`);
  return call("PUT", "/v1/capes/me", {
    name,
    serverId: body.serverId,
    cape,
    ...(hat !== undefined ? { hat } : {}),
    ...(minime !== undefined ? { minime } : {}),
  });
}

/** Wie der Launcher: Challenge holen, bei „Mojang“ anmelden, Cape zur Prüfung einreichen. */
async function submitCape(name, png, { join = true } = {}) {
  const { body } = await call("POST", "/v1/auth/challenge");
  if (join) joined.add(`${name}|${body.serverId}`);
  return call("POST", "/v1/capes/submit", { name, serverId: body.serverId, png });
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
  // die animierten Capes sind genauso mitgeliefert
  for (const builtin of ["aurora", "magma", "galaxy", "neon", "prism", "sakura", "storm", "dragon"]) {
    assert.deepEqual((await setCape("Salamander", { builtin })).body, { status: "approved", builtin });
  }
  await setCape("Salamander", { builtin: "pepperoni" });
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

test("Launcher: Einreichen ändert das getragene Cape nicht und bleibt in der Warteschlange", async () => {
  await setCape("Pepper_99", { builtin: "board" });
  const pixels = capePixels(64, 50);
  const png = encodeRgba(64, 32, pixels.rgba).toString("base64");
  const id = capeId(64, 32, pixels.rgba);

  assert.equal((await submitCape("Pepper_99", png, { join: false })).status, 401);
  assert.equal((await submitCape("Pepper_99", undefined)).status, 400);
  assert.equal((await submitCape("Pepper_99", Buffer.from("kein bild").toString("base64"))).status, 422);

  assert.deepEqual((await submitCape("Pepper_99", png)).body, { status: "pending", id });
  // getragen wird weiter das Brett
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Pepper_99")] })).body.capes, {
    [uuidOf("Pepper_99")]: { builtin: "board" },
  });
  const listed = (await admin("GET", "/v1/admin/capes?status=pending")).body.capes.find((c) => c.id === id);
  assert.equal(listed.submittedName, "Pepper_99");
  assert.equal(listed.viaLauncher, true);
  assert.deepEqual(listed.users, []);

  // Anziehen und wieder ablegen: eingereichte Capes fliegen nicht aus der Warteschlange
  await setCape("Pepper_99", { png });
  await setCape("Pepper_99", { builtin: "salami" });
  assert.deepEqual((await call("POST", "/v1/capes/status", { ids: [id] })).body.status, { [id]: { status: "pending" } });

  // nochmal einreichen: dasselbe Cape, kein zweiter Eintrag
  const before = (await admin("GET", "/v1/admin/capes?status=pending")).body.counts.pending;
  assert.deepEqual((await submitCape("Pepper_99", png)).body, { status: "pending", id });
  assert.equal((await admin("GET", "/v1/admin/capes?status=pending")).body.counts.pending, before);

  // nach der Prüfung liefert Einreichen den Status samt Grund
  await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "zu bunt" });
  assert.deepEqual((await submitCape("Pepper_99", png)).body, { status: "rejected", id, reason: "zu bunt" });
});

test("Galerie: nur freigegebene Capes, ohne Namen, jeder kann sie tragen", async () => {
  const pixels = capePixels(64, 60);
  const png = encodeRgba(64, 32, pixels.rgba).toString("base64");
  const id = capeId(64, 32, pixels.rgba);
  const gallery = async () => (await call("GET", "/v1/capes/gallery")).body.capes;
  const listed = async () => (await gallery()).find((c) => c.id === id);

  assert.deepEqual((await submitCape("Brotzeit", png)).body, { status: "pending", id });
  assert.equal(await listed(), undefined);

  await admin("POST", `/v1/admin/capes/${id}/approve`);
  const entry = await listed();
  assert.deepEqual(Object.keys(entry).sort(), ["approvedAt", "id", "wearers"]);
  assert.equal(entry.wearers, 0);
  assert.equal((await gallery())[0].id, id, "neueste Freigabe zuerst");
  assert.ok((await gallery()).every((c) => c.id !== capeId(128, 64, capePixels(128, 2).rgba)), "gesperrte fehlen");

  // Wie der Launcher: Bild aus der Galerie laden und selbst tragen – sofort für alle sichtbar,
  // ohne neue Prüfung und ohne das Tageslimit (Salamander hat es schon ausgeschöpft)
  const image = await call("GET", `/v1/capes/img/${id}.png`);
  assert.equal(image.status, 200);
  assert.deepEqual((await setCape("Salamander", { png: image.body.toString("base64") })).body, { status: "approved", id });
  assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuidOf("Salamander")] })).body.capes, {
    [uuidOf("Salamander")]: { id },
  });
  assert.equal((await listed()).wearers, 1);

  // wieder gesperrt → nicht mehr in der Galerie
  await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "doch nicht" });
  assert.equal(await listed(), undefined);
});

test("Melden: eine Meldung pro Spieler, ab REPORTS_TO_HIDE sofort versteckt, Admin entscheidet", async () => {
  const pixels = capePixels(64, 70);
  const png = encodeRgba(64, 32, pixels.rgba).toString("base64");
  const id = capeId(64, 32, pixels.rgba);
  /** Wie der Launcher: Challenge holen, bei „Mojang“ anmelden, melden. */
  const report = async (name, body, { join = true } = {}) => {
    const { body: challenge } = await call("POST", "/v1/auth/challenge");
    if (join) joined.add(`${name}|${challenge.serverId}`);
    return call("POST", "/v1/capes/report", { name, serverId: challenge.serverId, ...body });
  };
  const inGallery = async () => (await call("GET", "/v1/capes/gallery")).body.capes.find((c) => c.id === id);
  const adminEntry = async (tab) => (await admin("GET", `/v1/admin/capes?status=${tab}`)).body.capes.find((c) => c.id === id);

  // ungeprüft (für andere ohnehin unsichtbar): nicht meldbar
  await submitCape("Brotzeit", png);
  assert.equal((await report("Salamander", { id, reason: "logo" })).status, 404);

  await admin("POST", `/v1/admin/capes/${id}/approve`);
  const approvedAt = (await inGallery()).approvedAt;
  await setCape("Brotzeit", { png });

  // Eingaben und Anmeldung
  assert.equal((await report("Salamander", { id, reason: "weil" })).status, 400);
  assert.equal((await report("Salamander", { id: "kaputt", reason: "logo" })).status, 400);
  assert.equal((await report("Salamander", { id, reason: "logo" }, { join: false })).status, 401);

  // erste Meldung: bleibt sichtbar, steht unter „Gemeldet“; doppelt melden zählt nicht
  assert.deepEqual((await report("Salamander", { id, reason: "logo", note: "  Nike-Logo  " })).body, { status: "reported" });
  assert.deepEqual((await report("Salamander", { id, reason: "hate" })).body, { status: "already_reported" });
  assert.ok(await inGallery());
  const reported = await admin("GET", "/v1/admin/capes?status=reported");
  assert.equal(reported.body.counts.reported, 1);
  const entry = reported.body.capes.find((c) => c.id === id);
  assert.equal(entry.status, "approved");
  assert.deepEqual(
    entry.reports.map((r) => [r.name, r.reason, r.note]),
    [["Salamander", "logo", "Nike-Logo"]],
  );

  // zweite Meldung eines anderen Spielers (REPORTS_TO_HIDE=2 im Test): sofort für alle weg
  const live = await openEvents();
  try {
    assert.deepEqual((await report("Pepper_99", { id, reason: "logo" })).body, { status: "reported" });
    const brotzeit = uuidOf("Brotzeit");
    assert.deepEqual(await live.next((e) => e.uuid === brotzeit), { uuid: brotzeit, cape: null });
  } finally {
    live.close();
  }
  assert.equal(await inGallery(), undefined);
  assert.equal((await call("GET", `/v1/capes/img/${id}.png`)).status, 404);
  assert.deepEqual((await call("POST", "/v1/capes/status", { ids: [id] })).body.status, { [id]: { status: "pending" } });
  // wartet auf einen Admin, auch wenn es keiner mehr trägt
  await setCape("Brotzeit", { builtin: "salami" });
  assert.ok((await adminEntry("pending")).hiddenByReports);

  // Admin behält es: Meldungen erledigt, wieder sichtbar, alter Platz in der Galerie
  await admin("POST", `/v1/admin/capes/${id}/approve`);
  assert.equal((await inGallery()).approvedAt, approvedAt);
  assert.equal((await admin("GET", "/v1/admin/capes?status=reported")).body.counts.reported, 0);

  // wieder meldbar; nach dem Sperren nicht mehr, und die Meldungen sind erledigt
  assert.deepEqual((await report("Salamander", { id, reason: "other" })).body, { status: "reported" });
  await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "fremdes Logo" });
  assert.equal((await report("Pepper_99", { id, reason: "other" })).status, 404);
  assert.equal((await adminEntry("rejected")).reports, undefined);
  assert.equal((await admin("GET", "/v1/admin/capes?status=bogus")).status, 400);
});

test("Hüte: Weihnachtsmütze wird mit dem Cape geteilt, auch ohne Cape", async () => {
  const live = await openEvents();
  try {
    const uuid = uuidOf("Brotzeit");
    const mine = (e) => e.uuid === uuid;
    const lookup = async () => (await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid];

    // Cape und Mütze zusammen
    assert.equal((await setCape("Brotzeit", { builtin: "board" }, { hat: "santa" })).status, 200);
    assert.deepEqual(await lookup(), { builtin: "board", hat: "santa" });
    assert.deepEqual(await live.next(mine), { uuid, cape: { builtin: "board", hat: "santa" } });

    // nur die Mütze, kein Cape: der Spieler bleibt gespeichert
    await setCape("Brotzeit", null, { hat: "santa" });
    assert.deepEqual(await lookup(), { hat: "santa" });
    assert.deepEqual(await live.next(mine), { uuid, cape: { hat: "santa" } });

    // die neuen Hüte (Krone, Zylinder …) gehen genauso
    assert.equal((await setCape("Brotzeit", { builtin: "board" }, { hat: "top_hat" })).status, 200);
    assert.deepEqual(await lookup(), { builtin: "board", hat: "top_hat" });
    assert.deepEqual(await live.next(mine), { uuid, cape: { builtin: "board", hat: "top_hat" } });

    // unbekannter Hut (neuerer Client) wird ignoriert, nicht abgelehnt
    assert.equal((await setCape("Brotzeit", { builtin: "board" }, { hat: "zylinder" })).status, 200);
    assert.deepEqual(await lookup(), { builtin: "board" });
    assert.deepEqual(await live.next(mine), { uuid, cape: { builtin: "board" } });

    // Mütze ab und kein Cape: der Spieler verschwindet wie bisher
    await setCape("Brotzeit", null);
    assert.equal(await lookup(), undefined);
    assert.deepEqual(await live.next(mine), { uuid, cape: null });
  } finally {
    live.close();
  }
});

/** Wie salami-core: Challenge holen, bei „Mojang“ anmelden, Emote-Token abholen. */
async function emoteSession(name, { join = true } = {}) {
  const { body } = await call("POST", "/v1/auth/challenge");
  if (join) joined.add(`${name}|${body.serverId}`);
  return call("POST", "/v1/emotes/session", { name, serverId: body.serverId });
}

test("Emotes: nur angemeldet, nur bekannte, live an alle – ohne uuid oben", async () => {
  const live = await openEvents();
  try {
    const uuid = uuidOf("Pepper_99");

    // ohne Mojang-Anmeldung kein Token, ohne Token kein Emote
    assert.equal((await emoteSession("Pepper_99", { join: false })).status, 401);
    assert.equal((await call("POST", "/v1/emotes", { emote: "wave" })).status, 401);
    assert.equal((await call("POST", "/v1/emotes", { emote: "wave" }, { Authorization: "Bearer falsch" })).status, 401);

    const session = await emoteSession("Pepper_99");
    assert.equal(session.status, 200);
    assert.equal(session.body.uuid, uuid);
    const auth = { Authorization: `Bearer ${session.body.token}` };

    assert.deepEqual((await call("POST", "/v1/emotes", { emote: "wave" }, auth)).body, { ok: true });
    // ältere Spiele lesen {uuid, cape}; das Emote steckt deshalb in einem eigenen Objekt
    assert.deepEqual(await live.next((e) => e.emote), { emote: { uuid, id: "wave" } });

    assert.equal((await call("POST", "/v1/emotes", { emote: "gibt_es_nicht" }, auth)).status, 400);
    assert.equal((await call("POST", "/v1/emotes", {}, auth)).status, 400);

    // nichts davon wird beim Spieler gespeichert
    const stored = (await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid] ?? {};
    assert.equal("emote" in stored, false);
  } finally {
    live.close();
  }
});

test("Emotes: höchstens 20 pro Minute und Spieler", async () => {
  const session = await emoteSession("Salamander");
  const auth = { Authorization: `Bearer ${session.body.token}` };
  const statuses = [];
  for (let i = 0; i < 22; i++) statuses.push((await call("POST", "/v1/emotes", { emote: "clap" }, auth)).status);
  assert.deepEqual(statuses.slice(0, 20), Array(20).fill(200));
  assert.equal(statuses[21], 429);
});

test("Emote-Katalog: wird ausgeliefert, neue Emotes kommen ohne Neustart und werden live gemeldet", async () => {
  const catalog = async () => (await call("GET", "/v1/emotes/catalog")).body;
  const before = await catalog();
  const ids = before.emotes.map((e) => e.id);
  for (const id of ["wave", "cheer", "clap", "dance", "facepalm", "point", "shrug", "tpose", "salute"]) {
    assert.ok(ids.includes(id), `${id} fehlt im Katalog`);
  }
  assert.match(before.version, /^[0-9a-f]{16}$/);
  const wave = before.emotes.find((e) => e.id === "wave");
  assert.equal(wave.names.de_de, "Winken");
  assert.deepEqual(wave.tracks.right_arm.keys[0], { t: 0, rot: [0, 0, 129] });

  const session = await emoteSession("Brotzeit");
  const auth = { Authorization: `Bearer ${session.body.token}` };
  assert.equal((await call("POST", "/v1/emotes", { emote: "moonwalk" }, auth)).status, 400);

  const live = await openEvents();
  try {
    // neues Emote in die Datei: ein gültiges, ein kaputtes (fällt weg, die anderen bleiben), eine doppelte id
    const file = JSON.parse(readFileSync(emotesFile, "utf8"));
    file.emotes.push(
      {
        id: "moonwalk",
        names: { en_us: "Moonwalk", de_de: "Moonwalk" },
        loop: true,
        hidden_field: "fällt weg",
        tracks: { right_leg: { cycle: 0.8, keys: [{ t: 0, rot: [-20, 0, 0] }, { t: 0.4, rot: [20, 0, 0] }] } },
      },
      { id: "Kaputt!", names: { en_us: "x" }, duration: 1, tracks: { head: { keys: [{ t: 0, rot: [0, 0, 0] }] } } },
      { id: "spin", names: { en_us: "Spin" }, duration: 99, tracks: { head: { keys: [{ t: 0, rot: [0, 0, 0] }] } } },
      { id: "wave", names: { en_us: "Wave again" }, duration: 1, tracks: { head: { keys: [{ t: 0, rot: [0, 0, 0] }] } } },
    );
    writeFileSync(emotesFile, JSON.stringify(file));

    const changed = await live.next((e) => e.kind === "emotes");
    const after = await catalog();
    assert.equal(changed.catalog, after.version);
    assert.notEqual(after.version, before.version);
    assert.equal(after.emotes.length, before.emotes.length + 1);
    const moonwalk = after.emotes.find((e) => e.id === "moonwalk");
    assert.deepEqual(moonwalk, {
      id: "moonwalk",
      names: { en_us: "Moonwalk", de_de: "Moonwalk" },
      loop: true,
      tracks: { right_leg: { cycle: 0.8, keys: [{ t: 0, rot: [-20, 0, 0] }, { t: 0.4, rot: [20, 0, 0] }] } },
    });
    assert.equal(after.emotes.find((e) => e.id === "wave").names.de_de, "Winken");

    // das neue Emote lässt sich sofort abspielen
    assert.equal((await call("POST", "/v1/emotes", { emote: "moonwalk" }, auth)).status, 200);
    assert.deepEqual(await live.next((e) => e.emote), { emote: { uuid: uuidOf("Brotzeit"), id: "moonwalk" } });

    // kaputte Datei: der bisherige Katalog bleibt
    writeFileSync(emotesFile, "{ das ist kein JSON");
    await new Promise((r) => setTimeout(r, 400));
    assert.equal((await catalog()).version, after.version);
  } finally {
    live.close();
  }
});

test("Hut-Katalog: Hüte sind Daten, neue kommen ohne Neustart und lassen sich sofort tragen", async () => {
  const catalog = async () => (await call("GET", "/v1/hats/catalog")).body;
  const before = await catalog();
  assert.match(before.version, /^[0-9a-f]{16}$/);
  assert.deepEqual(
    before.hats.map((h) => h.id),
    ["santa", "crown", "top_hat", "chef", "halo", "horns", "cat_ears", "party", "beret"],
  );
  const santa = before.hats.find((h) => h.id === "santa");
  assert.equal(santa.anchor, "shoulder");
  assert.equal(santa.scale, 0.35);
  assert.equal(santa.names.de_de, "Weihnachtsmütze");
  const halo = before.hats.find((h) => h.id === "halo");
  assert.equal(halo.glow, true);
  assert.equal(halo.anchor, undefined, "head ist der Standard und steht nicht drin");
  assert.deepEqual(halo.parts[0].boxes[0], [-4, -12, -4, 8, 0.6, 1]);

  const uuid = uuidOf("Brotzeit");
  const lookup = async () => (await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid];
  // den Hut gibt es noch nicht: wird ignoriert
  await setCape("Brotzeit", { builtin: "board" }, { hat: "wizard" });
  assert.deepEqual(await lookup(), { builtin: "board" });

  const live = await openEvents();
  try {
    const box = [-1, -2, -1, 2, 2, 2];
    const file = JSON.parse(readFileSync(hatsFile, "utf8"));
    file.hats.push(
      {
        id: "wizard",
        names: { en_us: "Wizard hat", de_de: "Zauberhut" },
        anchor: "head",
        scale: 1,
        hidden_field: "fällt weg",
        parts: [
          { color: "#4B2A9E", boxes: [[-5, -9, -5, 10, 1, 10]] },
          { at: [0, -9, 0], rot: [0, 0, 8], parts: [{ color: "#3a1f80", boxes: [[-2.5, -6, -2.5, 5, 6, 5]] }] },
        ],
      },
      { id: "kaputt", names: { en_us: "Broken" }, parts: [{ color: "lila", boxes: [box] }] },
      { id: "leer", names: { en_us: "Empty" }, parts: [{ at: [0, 0, 0] }] },
      { id: "none", names: { en_us: "None" }, parts: [{ color: "#ffffff", boxes: [box] }] },
      { id: "riesig", names: { en_us: "Huge" }, parts: [{ color: "#ffffff", boxes: Array(65).fill(box) }] },
      { id: "crown", names: { en_us: "Crown again" }, parts: [{ color: "#ffffff", boxes: [box] }] },
    );
    writeFileSync(hatsFile, JSON.stringify(file));

    const changed = await live.next((e) => e.kind === "hats");
    const after = await catalog();
    assert.equal(changed.catalog, after.version);
    assert.equal(after.hats.length, before.hats.length + 1);
    assert.deepEqual(after.hats.at(-1), {
      id: "wizard",
      names: { en_us: "Wizard hat", de_de: "Zauberhut" },
      parts: [
        { color: "#4b2a9e", boxes: [[-5, -9, -5, 10, 1, 10]] },
        { at: [0, -9, 0], rot: [0, 0, 8], parts: [{ color: "#3a1f80", boxes: [[-2.5, -6, -2.5, 5, 6, 5]] }] },
      ],
    });
    assert.equal(after.hats.find((h) => h.id === "crown").names.de_de, "Krone");

    // der neue Hut lässt sich sofort tragen und ist für alle sichtbar
    await setCape("Brotzeit", { builtin: "board" }, { hat: "wizard" });
    assert.deepEqual(await lookup(), { builtin: "board", hat: "wizard" });
    assert.deepEqual(await live.next((e) => e.uuid === uuid), { uuid, cape: { builtin: "board", hat: "wizard" } });

    // kaputte Datei: der bisherige Katalog bleibt
    writeFileSync(hatsFile, "[]");
    await new Promise((r) => setTimeout(r, 400));
    assert.equal((await catalog()).version, after.version);
  } finally {
    live.close();
    await setCape("Brotzeit", null);
  }
});

test("Offizielle Capes: Bild und Eintrag auf dem Server genügen, jeder kann sie sofort tragen", async () => {
  const catalog = async () => (await call("GET", "/v1/capes/catalog")).body;
  const gallery = async () => (await call("GET", "/v1/capes/gallery")).body.capes;
  assert.deepEqual((await catalog()).capes, []);

  const pixels = capePixels(64, 90);
  const id = capeId(64, 32, pixels.rgba);
  const png = encodeRgba(64, 32, pixels.rgba);
  // dasselbe Bild war vorher eingereicht und abgelehnt: als offizielles Cape gilt es trotzdem
  assert.equal((await submitCape("Brotzeit", png.toString("base64"))).body.status, "pending");
  await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "erst mal nicht" });

  const live = await openEvents();
  try {
    writeFileSync(join(officialDir, "frost.png"), png);
    writeFileSync(join(officialDir, "schief.png"), encodeRgba(64, 64, Buffer.alloc(64 * 64 * 4, 255)));
    writeFileSync(
      officialFile,
      JSON.stringify({
        capes: [
          { id: "frost", names: { en_us: "Frost", de_de: "Frost" }, extra: "fällt weg" },
          { id: "ohne_bild", names: { en_us: "No image" } },
          { id: "schief", names: { en_us: "Wrong size" } },
          { id: "Kaputt!", names: { en_us: "Broken" } },
        ],
      }),
    );
    const changed = await live.next((e) => e.kind === "capes");
    const now = await catalog();
    assert.equal(changed.catalog, now.version);
    assert.deepEqual(now.capes, [{ id: "frost", names: { en_us: "Frost", de_de: "Frost" }, cape: id, width: 64, height: 32 }]);

    // Bild ist abrufbar, das Cape steht mit Namen vorn in der Galerie
    const image = await call("GET", `/v1/capes/img/${id}.png`);
    assert.equal(image.status, 200);
    const decoded = decodeCape(image.body);
    assert.equal(capeId(decoded.width, decoded.height, decoded.rgba), id);
    const first = (await gallery())[0];
    assert.deepEqual(
      { id: first.id, official: first.official, names: first.names },
      { id, official: true, names: { en_us: "Frost", de_de: "Frost" } },
    );

    // tragen: sofort freigegeben und für alle sichtbar, ohne Prüfung
    const uuid = uuidOf("Pepper_99");
    assert.deepEqual((await setCape("Pepper_99", { png: image.body.toString("base64") })).body, { status: "approved", id });
    assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid], { id });

    // melden und sperren geht bei offiziellen Capes nicht
    const challenge = await call("POST", "/v1/auth/challenge", {});
    joined.add(`Salamander|${challenge.body.serverId}`);
    const report = { name: "Salamander", serverId: challenge.body.serverId, id, reason: "other" };
    assert.equal((await call("POST", "/v1/capes/report", report)).status, 409);
    assert.equal((await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "nein" })).status, 409);
    assert.deepEqual((await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid], { id });

    // neues Bild unter demselben Namen: der Katalog zeigt auf das neue
    const other = capePixels(64, 91);
    const otherId = capeId(64, 32, other.rgba);
    writeFileSync(join(officialDir, "frost.png"), encodeRgba(64, 32, other.rgba));
    const replaced = await live.next((e) => e.kind === "capes");
    assert.notEqual(replaced.catalog, now.version);
    assert.equal((await catalog()).capes[0].cape, otherId);

    // aus der Liste genommen: nicht mehr offiziell, aber weiter freigegeben, bis ein Admin sperrt
    writeFileSync(officialFile, JSON.stringify({ capes: [] }));
    await live.next((e) => e.kind === "capes");
    assert.deepEqual((await catalog()).capes, []);
    const listed = (await gallery()).find((c) => c.id === id);
    assert.equal(listed.official, undefined);
    assert.equal(listed.wearers, 1);
    assert.equal((await admin("POST", `/v1/admin/capes/${id}/reject`, { reason: "jetzt doch" })).status, 200);
    assert.equal((await admin("POST", `/v1/admin/capes/${otherId}/reject`, {})).status, 200);
    assert.equal((await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid], undefined);
  } finally {
    live.close();
    await setCape("Pepper_99", null);
  }
});

test("Mini Me: Name und Platz werden mit dem Cape geteilt, Ungültiges ignoriert", async () => {
  const live = await openEvents();
  try {
    const uuid = uuidOf("Brotzeit");
    const mine = (e) => e.uuid === uuid;
    const lookup = async () => (await call("POST", "/v1/capes/lookup", { uuids: [uuid] })).body.capes[uuid];

    // nur ein Mini Me, kein Cape: der Spieler bleibt gespeichert
    await setCape("Brotzeit", null, { minime: { name: "Salamander", place: "follow" } });
    assert.deepEqual(await lookup(), { minime: { name: "Salamander", place: "follow" } });
    assert.deepEqual(await live.next(mine), { uuid, cape: { minime: { name: "Salamander", place: "follow" } } });

    // mit Cape und Mütze zusammen; zusätzliche Felder fallen weg
    assert.equal(
      (await setCape("Brotzeit", { builtin: "board" }, { hat: "santa", minime: { name: "Pepper_99", place: "head", x: 1 } })).status,
      200,
    );
    assert.deepEqual(await lookup(), { builtin: "board", hat: "santa", minime: { name: "Pepper_99", place: "head" } });
    assert.deepEqual(await live.next(mine), {
      uuid,
      cape: { builtin: "board", hat: "santa", minime: { name: "Pepper_99", place: "head" } },
    });

    // ungültiger Name oder Platz wird ignoriert, nicht abgelehnt
    for (const minime of [{ name: "kein name!", place: "hand" }, { name: "Salamander", place: "rucksack" }, "Salamander", [1]]) {
      assert.equal((await setCape("Brotzeit", { builtin: "board" }, { minime })).status, 200);
      assert.deepEqual(await lookup(), { builtin: "board" });
    }
    assert.deepEqual(await live.next(mine), { uuid, cape: { builtin: "board" } });

    // alles ab: der Spieler verschwindet
    await setCape("Brotzeit", null);
    assert.equal(await lookup(), undefined);
    assert.deepEqual(await live.next(mine), { uuid, cape: null });
  } finally {
    live.close();
  }
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

test("Admin-Seite ist als App installierbar", async () => {
  const manifest = await fetch(`${base}/admin/manifest.webmanifest`);
  assert.equal(manifest.status, 200);
  assert.match(manifest.headers.get("content-type"), /application\/manifest\+json/);
  assert.match(manifest.headers.get("content-security-policy"), /manifest-src 'self'; worker-src 'self'/);
  assert.ok((await manifest.json()).icons.some((icon) => icon.purpose === "maskable"));
  assert.match((await fetch(`${base}/admin/sw.js`)).headers.get("content-type"), /text\/javascript/);
  for (const icon of ["icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png", "badge-96.png"]) {
    const response = await fetch(`${base}/admin/${icon}`);
    assert.equal(response.status, 200, icon);
    assert.equal(response.headers.get("content-type"), "image/png");
  }
});

test("Admin-Sperre nach 10 falschen Schlüsseln", async () => {
  for (let i = 0; i < 10; i++) {
    await call("GET", "/v1/admin/me", undefined, { Authorization: `Bearer falsch-${i}` });
  }
  assert.equal((await call("GET", "/v1/admin/me", undefined, { Authorization: "Bearer falsch" })).status, 429);
  assert.equal((await admin("GET", "/v1/admin/me")).status, 429);
});
