import { test } from "node:test";
import assert from "node:assert/strict";
import { TtlCache, cacheKey } from "./ttl-cache.ts";

test("liefert innerhalb der TTL aus dem Cache", async () => {
  let time = 0;
  let calls = 0;
  const cache = new TtlCache<number>(1000, 10, () => time);
  const load = async () => ++calls;
  assert.equal(await cache.get("k", load), 1);
  time = 999;
  assert.equal(await cache.get("k", load), 1);
  time = 1000;
  assert.equal(await cache.get("k", load), 2);
});

test("gleichzeitige Anfragen teilen sich ein Promise", async () => {
  let calls = 0;
  const cache = new TtlCache<string>(1000, 10);
  const load = () => new Promise<string>((resolve) => setTimeout(() => resolve(String(++calls)), 5));
  const [a, b] = await Promise.all([cache.get("k", load), cache.get("k", load)]);
  assert.equal(a, "1");
  assert.equal(b, "1");
  assert.equal(calls, 1);
});

test("Fehler werden nicht gecacht", async () => {
  const cache = new TtlCache<number>(1000, 10);
  await assert.rejects(cache.get("k", () => Promise.reject(new Error("boom"))));
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(await cache.get("k", async () => 7), 7);
});

test("Größenlimit verdrängt den am längsten unbenutzten Eintrag", async () => {
  const cache = new TtlCache<string>(10_000, 2);
  await cache.get("a", async () => "a");
  await cache.get("b", async () => "b");
  await cache.get("a", async () => "a2"); // a frisch benutzt
  await cache.get("c", async () => "c"); // verdrängt b
  assert.equal(cache.size, 2);
  assert.equal(await cache.get("b", async () => "b-neu"), "b-neu");
});

test("Schlüssel unabhängig von Feldreihenfolge, undefined ignoriert", () => {
  assert.equal(cacheKey("s", { a: 1, b: [1, 2], c: undefined }), cacheKey("s", { b: [1, 2], a: 1 }));
  assert.notEqual(cacheKey("s", { a: 1 }), cacheKey("s", { a: 2 }));
});
