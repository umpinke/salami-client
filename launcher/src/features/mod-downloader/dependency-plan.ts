import type { UnifiedVersion } from "../../types/unified";
import type { Mod } from "../../types/profile";

/**
 * Vorschau der Abhängigkeiten vor einer Installation.
 *
 * Pflicht-Abhängigkeiten installiert das Backend selbst (transitiv); die
 * Vorschau zeigt sie an, bietet optionale Abhängigkeiten zur Auswahl an und
 * warnt vor Konflikten (als "incompatible" markierte, bereits installierte Mods).
 */

export type Platform = "modrinth" | "curseforge";

export interface InstalledProject {
  platform: Platform;
  projectId: string;
  name: string | null;
  enabled: boolean;
}

export interface PlannedDependency {
  platform: Platform;
  projectId: string;
  versionId?: string;
  /** Anzeigename, sobald bekannt (wird nachgeladen). */
  name?: string | null;
}

export interface DependencyPlan {
  /** Pflicht, noch nicht installiert → wird mitinstalliert. */
  required: PlannedDependency[];
  /** Pflicht, bereits im Profil. */
  alreadyInstalled: PlannedDependency[];
  /** Optional, noch nicht installiert → zur Auswahl. */
  optional: PlannedDependency[];
  /** Inkompatibel und im Profil vorhanden (aktiv). */
  conflicts: PlannedDependency[];
}

export function platformOf(source: string | null | undefined): Platform | null {
  const s = (source ?? "").toLowerCase().replace(/[^a-z]/g, "");
  if (s === "modrinth") return "modrinth";
  if (s === "curseforge") return "curseforge";
  return null;
}

/** Installierte Modrinth-/CurseForge-Projekte eines Profils. */
export function installedProjectsOf(mods: readonly Mod[] | null | undefined): InstalledProject[] {
  const result: InstalledProject[] = [];
  for (const mod of mods ?? []) {
    const source = mod.source as { type: string; project_id?: string };
    if ((source.type === "modrinth" || source.type === "curseforge") && source.project_id) {
      result.push({
        platform: source.type,
        projectId: String(source.project_id),
        name: mod.display_name,
        enabled: mod.enabled,
      });
    }
  }
  return result;
}

export function planDependencies(version: UnifiedVersion, installed: readonly InstalledProject[]): DependencyPlan {
  const platform = platformOf(version.source as string);
  const plan: DependencyPlan = { required: [], alreadyInstalled: [], optional: [], conflicts: [] };
  if (!platform) return plan;

  const installedById = new Map<string, InstalledProject>();
  for (const item of installed) {
    if (item.platform === platform) installedById.set(item.projectId, item);
  }

  const seen = new Set<string>();
  for (const dependency of version.dependencies ?? []) {
    const projectId = dependency.project_id ? String(dependency.project_id) : null;
    if (!projectId || projectId === String(version.project_id) || seen.has(projectId)) continue;
    seen.add(projectId);

    const present = installedById.get(projectId);
    const entry: PlannedDependency = {
      platform,
      projectId,
      versionId: dependency.version_id ?? undefined,
      name: present?.name ?? null,
    };
    switch (dependency.dependency_type as string) {
      case "required":
        (present ? plan.alreadyInstalled : plan.required).push(entry);
        break;
      case "optional":
        if (!present) plan.optional.push(entry);
        break;
      case "incompatible":
        if (present?.enabled) plan.conflicts.push(entry);
        break;
      default: // "embedded": steckt bereits in der Mod
        break;
    }
  }
  return plan;
}

/** Lohnt sich ein Bestätigungsdialog? */
export function needsConfirmation(plan: DependencyPlan, mode: "full" | "conflicts_only" = "full"): boolean {
  if (plan.conflicts.length > 0) return true;
  if (mode === "conflicts_only") return false;
  return plan.required.length > 0 || plan.optional.length > 0;
}
