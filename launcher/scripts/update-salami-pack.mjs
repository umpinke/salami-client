// Trägt das Salami-Pack (packs/salami-packs.json) für alle Minecraft-Versionen ein, für die
// salami-core gebaut wird (mod/stonecutter.properties.toml, mod.mc_releases):
//   - salami-core: file:dev/salami-core-<build>.jar (installDev bzw. Installer-Kopie)
//   - übrige Pack-Mods: neueste Modrinth-Version (Fabric) je Minecraft-Version
// Vorhandene Einträge der anderen Mods bleiben, wie sie sind (feste Versionen).
//
//   node scripts/update-salami-pack.mjs            (schreiben)
//   node scripts/update-salami-pack.mjs --check    (nur prüfen, Exit 1 wenn etwas fehlt)
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const packFile = join(root, "packs", "salami-packs.json");
const tomlFile = join(root, "mod", "stonecutter.properties.toml");
const check = process.argv.includes("--check");

/** Build-Version (Stonecutter) -> Minecraft-Versionen, auf denen diese Jar läuft. */
function salamiBuilds() {
  const builds = new Map();
  let section = null;
  for (const line of readFileSync(tomlFile, "utf8").split(/\r?\n/)) {
    const header = line.match(/^\["([^"]+)"\]\s*$/);
    if (header) {
      section = header[1];
      continue;
    }
    const releases = line.match(/^mod\.mc_releases\s*=\s*\[(.*)\]\s*$/);
    if (section && releases) {
      builds.set(section, [...releases[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]));
    }
  }
  return builds;
}

async function modrinthVersions(projectId) {
  const url = `https://api.modrinth.com/v2/project/${projectId}/version?loaders=${encodeURIComponent('["fabric"]')}`;
  const res = await fetch(url, { headers: { "User-Agent": "salami-client/update-salami-pack" } });
  if (!res.ok) {
    throw new Error(`Modrinth ${projectId}: HTTP ${res.status}`);
  }
  return res.json(); // neueste zuerst
}

function pick(versions, minecraft) {
  const fitting = versions.filter((v) => v.game_versions.includes(minecraft));
  return fitting.find((v) => v.version_type === "release") ?? fitting[0];
}

/** JSON wie bisher: kurze Objekte in einer Zeile, Rest eingerückt. */
function format(value, indent = "") {
  const inline = JSON.stringify(value, null, 1).replace(/\s*\n\s*/g, " ").replace(/\[ /g, "[").replace(/ \]/g, "]");
  if (value === null || typeof value !== "object" || inline.length + indent.length <= 150) {
    return inline;
  }
  const inner = indent + "  ";
  if (Array.isArray(value)) {
    return `[\n${value.map((v) => inner + format(v, inner)).join(",\n")}\n${indent}]`;
  }
  const entries = Object.entries(value).map(([k, v]) => `${inner}${JSON.stringify(k)}: ${format(v, inner)}`);
  return `{\n${entries.join(",\n")}\n${indent}}`;
}

const pack = JSON.parse(readFileSync(packFile, "utf8"));
const salami = pack.packs.salami;
const builds = salamiBuilds();
const minecraftVersions = [...builds.values()].flat();
const missing = [];

// salami-core: genau eine Jar pro Build, für alle seine Minecraft-Versionen
const core = salami.mods.find((m) => m.id === "salami-core");
const coreCompatibility = {};
for (const [build, releases] of builds) {
  const filename = `salami-core-${build}.jar`;
  for (const minecraft of releases) {
    coreCompatibility[minecraft] = { fabric: { identifier: `file:dev/${filename}`, filename, source: null } };
  }
}
if (JSON.stringify(core.compatibility) !== JSON.stringify(coreCompatibility)) {
  missing.push("salami-core");
  core.compatibility = coreCompatibility;
}

for (const mod of salami.mods) {
  if (mod.source.type !== "modrinth") {
    continue;
  }
  const todo = minecraftVersions.filter((v) => !mod.compatibility[v]?.fabric);
  if (todo.length === 0) {
    continue;
  }
  const versions = await modrinthVersions(mod.source.projectId);
  for (const minecraft of todo) {
    const version = pick(versions, minecraft);
    if (!version) {
      console.warn(`${mod.id}: keine Fabric-Version für ${minecraft} auf Modrinth`);
      continue;
    }
    missing.push(`${mod.id} ${minecraft}`);
    mod.compatibility[minecraft] = { fabric: { identifier: version.id, filename: null, source: null } };
  }
  // Versionen in fester Reihenfolge (wie mc_releases), übrige dahinter
  const ordered = {};
  for (const minecraft of [...minecraftVersions, ...Object.keys(mod.compatibility)]) {
    if (mod.compatibility[minecraft]) {
      ordered[minecraft] = mod.compatibility[minecraft];
    }
  }
  mod.compatibility = ordered;
}

if (check) {
  if (missing.length > 0) {
    console.error(`Pack nicht aktuell: ${missing.join(", ")} – node scripts/update-salami-pack.mjs`);
    process.exit(1);
  }
  console.log("Pack aktuell.");
} else {
  writeFileSync(packFile, format(pack) + "\n");
  console.log(missing.length > 0 ? `Eingetragen: ${missing.join(", ")}` : "Nichts zu tun.");
}
