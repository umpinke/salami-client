import { useGlobalModalStore } from "../../hooks/useGlobalModal";
import { getProfile } from "../../services/profile-service";
import { ModrinthService } from "../../services/modrinth-service";
import UnifiedService from "../../services/unified-service";
import { ContentType, type InstallContentPayload } from "../../types/content";
import {
  installedProjectsOf,
  needsConfirmation,
  planDependencies,
  platformOf,
  type DependencyPlan,
  type PlannedDependency,
} from "./dependency-plan";
import { DependencyPlanModal, type DependencyPlanDecision } from "./DependencyPlanModal";

/**
 * Prüft vor einer Mod-Installation die Abhängigkeiten und fragt bei Bedarf nach.
 *
 * "fail open": Kann die Vorschau nicht erstellt werden (offline, API-Fehler),
 * wird ohne Dialog installiert – die Vorschau ist eine Hilfe, keine Hürde.
 */

export type PreflightMode = "full" | "conflicts_only" | "off";

export interface PreflightResult {
  proceed: boolean;
  optional: PlannedDependency[];
}

const MODAL_ID = "salami-dependency-plan";
const DONT_ASK_KEY = "salami-downloader-dont-ask";
const PROCEED: PreflightResult = { proceed: true, optional: [] };

function dontAsk(): boolean {
  try {
    return localStorage.getItem(DONT_ASK_KEY) === "1";
  } catch {
    return false;
  }
}

function rememberDontAsk(): void {
  try {
    localStorage.setItem(DONT_ASK_KEY, "1");
  } catch {
    // ohne Speicher eben jedes Mal fragen
  }
}

export async function runInstallPreflight(
  payload: InstallContentPayload,
  mode: PreflightMode = "full",
): Promise<PreflightResult> {
  if (mode === "off" || payload.content_type !== ContentType.Mod) return PROCEED;
  const platform = platformOf(payload.source as string);
  if (!platform || !payload.project_id || !payload.version_id) return PROCEED;

  try {
    const [versions, profile] = await Promise.all([
      UnifiedService.getModVersions({ source: payload.source, project_id: payload.project_id }),
      getProfile(payload.profile_id),
    ]);
    const version = versions.versions.find((v) => v.id === payload.version_id);
    if (!version) return PROCEED;

    const plan = planDependencies(version, installedProjectsOf(profile.mods));
    const effectiveMode = mode === "full" && dontAsk() ? "conflicts_only" : mode;
    if (!needsConfirmation(plan, effectiveMode)) return PROCEED;

    await resolveNames(plan);
    const decision = await ask(payload.content_name || payload.file_name, plan);
    if (decision.dontAskAgain) rememberDontAsk();
    return { proceed: decision.proceed, optional: decision.optional };
  } catch (error) {
    console.warn("[Salami] Dependency preview failed – installing without it", error);
    return PROCEED;
  }
}

/** Anzeigenamen für Modrinth-Abhängigkeiten nachladen (CurseForge zeigt die ID). */
async function resolveNames(plan: DependencyPlan): Promise<void> {
  const all = [...plan.required, ...plan.optional, ...plan.conflicts, ...plan.alreadyInstalled];
  const missing = all.filter((d) => !d.name && d.platform === "modrinth").map((d) => d.projectId);
  if (missing.length === 0) return;
  try {
    const projects = await ModrinthService.getProjectDetails([...new Set(missing)]);
    const titles = new Map(projects.map((p) => [p.id, p.title]));
    for (const dep of all) {
      if (!dep.name) dep.name = titles.get(dep.projectId) ?? null;
    }
  } catch {
    // Namen sind nur Komfort
  }
}

function ask(name: string, plan: DependencyPlan): Promise<DependencyPlanDecision> {
  const { openModal, closeModal } = useGlobalModalStore.getState();
  return new Promise((resolve) => {
    openModal(
      MODAL_ID,
      <DependencyPlanModal
        name={name}
        plan={plan}
        onDone={(decision) => {
          closeModal(MODAL_ID);
          resolve(decision);
        }}
      />,
      2000,
    );
  });
}
