import type { UnifiedVersion } from "../../types/unified";

/**
 * Auswahl der passenden Mod-Version für ein Profil.
 *
 * Ersetzt die alte Logik, die bei fehlender Übereinstimmung still auf
 * "irgendeine Version" zurückfiel. Jetzt gilt:
 * - Minecraft-Version muss passen.
 * - Bei Mods muss der Loader passen (Quilt lädt auch Fabric-Mods).
 * - Releases vor Betas vor Alphas, dann exakter Loader vor Fallback, dann die neueste.
 * - Passt nichts, gibt es `null` – der Aufrufer zeigt "keine kompatible Version".
 *
 * Bewusst nur `import type`, damit die Datei ohne Build mit `node --test` läuft.
 */

/** Echte Mod-Loader. Resourcepacks/Shader/Datapacks nutzen andere Werte ("minecraft", "iris", …). */
const MOD_LOADERS = new Set(["fabric", "quilt", "forge", "neoforge", "liteloader", "modloader", "rift"]);

const RELEASE_RANK: Record<string, number> = { release: 0, beta: 1, alpha: 2 };

export interface VersionTarget {
  gameVersion?: string | null;
  loader?: string | null;
}

export type VersionMatch = "exact" | "loader_fallback";

export interface VersionChoice {
  version: UnifiedVersion;
  match: VersionMatch;
}

/** Akzeptierte Loader eines Profils in Vorzugsreihenfolge. */
export function compatibleLoaders(profileLoader: string | null | undefined): string[] {
  switch ((profileLoader ?? "").toLowerCase()) {
    case "quilt":
      return ["quilt", "fabric"];
    case "fabric":
      return ["fabric"];
    case "forge":
      return ["forge"];
    case "neoforge":
      return ["neoforge"];
    default:
      return []; // Vanilla: keine Mods
  }
}

/**
 * Rang des Loaders einer Version für das Profil:
 * 0 = exakt, 1+ = Fallback, -1 = inkompatibel, null = kein Mod (Loader egal).
 */
export function loaderRank(versionLoaders: string[], accepted: string[]): number | null {
  const modLoaders = versionLoaders.map((l) => l.toLowerCase()).filter((l) => MOD_LOADERS.has(l));
  if (modLoaders.length === 0) return null;
  return accepted.findIndex((loader) => modLoaders.includes(loader));
}

export function selectBestVersion(
  versions: readonly UnifiedVersion[] | null | undefined,
  target: VersionTarget,
): VersionChoice | null {
  if (!versions?.length) return null;
  const accepted = compatibleLoaders(target.loader);

  let best: { version: UnifiedVersion; rank: number; release: number; date: number } | null = null;
  for (const version of versions) {
    if (target.gameVersion && !version.game_versions.includes(target.gameVersion)) continue;
    const rank = loaderRank(version.loaders, accepted);
    if (rank !== null && rank < 0) continue;

    const candidate = {
      version,
      rank: rank ?? 0,
      release: RELEASE_RANK[(version.release_type as string) ?? "release"] ?? 3,
      date: Date.parse(version.date_published) || 0,
    };
    if (
      !best ||
      candidate.release < best.release ||
      (candidate.release === best.release && candidate.rank < best.rank) ||
      (candidate.release === best.release && candidate.rank === best.rank && candidate.date > best.date)
    ) {
      best = candidate;
    }
  }
  if (!best) return null;
  return { version: best.version, match: best.rank === 0 ? "exact" : "loader_fallback" };
}

/** Kompatibilität mit den Profilen der alten API (`utils/content-status.ts`). */
export function findBestVersionFor(
  profile: { game_version?: string | null; loader?: string | null } | null | undefined,
  versions: readonly UnifiedVersion[] | null | undefined,
): UnifiedVersion | null {
  if (!profile) return null;
  return selectBestVersion(versions, { gameVersion: profile.game_version, loader: profile.loader })?.version ?? null;
}
