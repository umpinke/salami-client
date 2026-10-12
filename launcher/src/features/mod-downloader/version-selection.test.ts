// Ausführen: node --test src/features/mod-downloader/
import { test } from "node:test";
import assert from "node:assert/strict";
import type { UnifiedVersion } from "../../types/unified";
import { compatibleLoaders, findBestVersionFor, selectBestVersion } from "./version-selection.ts";

function v(id: string, game: string[], loaders: string[], date: string, release = "release"): UnifiedVersion {
  return {
    id,
    project_id: "p",
    source: "Modrinth",
    name: id,
    version_number: id,
    dependencies: [],
    game_versions: game,
    loaders,
    files: [],
    date_published: date,
    downloads: 0,
    release_type: release,
    url: "",
  } as unknown as UnifiedVersion;
}

test("Quilt-Profile akzeptieren Fabric als Fallback", () => {
  assert.deepEqual(compatibleLoaders("quilt"), ["quilt", "fabric"]);
  assert.deepEqual(compatibleLoaders("vanilla"), []);
});

test("exakter Treffer: neueste Release-Version für MC + Loader", () => {
  const choice = selectBestVersion(
    [
      v("old", ["1.21.11"], ["fabric"], "2026-01-01"),
      v("new", ["1.21.11"], ["fabric"], "2026-05-01"),
      v("forge", ["1.21.11"], ["forge"], "2026-06-01"),
    ],
    { gameVersion: "1.21.11", loader: "fabric" },
  );
  assert.equal(choice?.version.id, "new");
  assert.equal(choice?.match, "exact");
});

test("kein stiller Fallback auf inkompatible Versionen", () => {
  const choice = selectBestVersion(
    [v("forge", ["1.21.11"], ["forge"], "2026-06-01"), v("fabric-old-mc", ["1.20.1"], ["fabric"], "2026-06-01")],
    { gameVersion: "1.21.11", loader: "fabric" },
  );
  assert.equal(choice, null);
});

test("Quilt-Profil nimmt Fabric-Version, markiert als Fallback", () => {
  const choice = selectBestVersion([v("fabric", ["1.21.11"], ["fabric"], "2026-06-01")], {
    gameVersion: "1.21.11",
    loader: "quilt",
  });
  assert.equal(choice?.version.id, "fabric");
  assert.equal(choice?.match, "loader_fallback");
});

test("Version für Fabric UND Quilt zählt im Quilt-Profil als exakt", () => {
  const choice = selectBestVersion([v("both", ["1.21.11"], ["fabric", "quilt"], "2026-06-01")], {
    gameVersion: "1.21.11",
    loader: "quilt",
  });
  assert.equal(choice?.match, "exact");
});

test("Release schlägt neuere Beta", () => {
  const choice = selectBestVersion(
    [v("beta", ["1.21.11"], ["fabric"], "2026-09-01", "beta"), v("release", ["1.21.11"], ["fabric"], "2026-03-01")],
    { gameVersion: "1.21.11", loader: "fabric" },
  );
  assert.equal(choice?.version.id, "release");
});

test("nur Betas vorhanden: neueste Beta", () => {
  const choice = selectBestVersion(
    [v("b1", ["1.21.11"], ["fabric"], "2026-01-01", "beta"), v("b2", ["1.21.11"], ["fabric"], "2026-02-01", "beta")],
    { gameVersion: "1.21.11", loader: "fabric" },
  );
  assert.equal(choice?.version.id, "b2");
});

test("Resourcepacks ignorieren den Loader (auch im Vanilla-Profil)", () => {
  const pack = v("pack", ["1.21.11"], ["minecraft"], "2026-01-01");
  assert.equal(selectBestVersion([pack], { gameVersion: "1.21.11", loader: "vanilla" })?.version.id, "pack");
});

test("Mods passen nicht in Vanilla-Profile", () => {
  assert.equal(findBestVersionFor({ game_version: "1.21.11", loader: "vanilla" }, [v("m", ["1.21.11"], ["fabric"], "2026-01-01")]), null);
});

test("Groß-/Kleinschreibung der Loader egal", () => {
  assert.equal(
    findBestVersionFor({ game_version: "1.21.11", loader: "fabric" }, [v("m", ["1.21.11"], ["Fabric"], "2026-01-01")])?.id,
    "m",
  );
});
