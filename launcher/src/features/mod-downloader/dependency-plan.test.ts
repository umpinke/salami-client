import { test } from "node:test";
import assert from "node:assert/strict";
import type { UnifiedVersion } from "../../types/unified";
import type { Mod } from "../../types/profile";
import { installedProjectsOf, needsConfirmation, planDependencies } from "./dependency-plan.ts";

function version(deps: Array<[string | undefined, string]>, source = "Modrinth"): UnifiedVersion {
  return {
    id: "v1",
    project_id: "self",
    source,
    dependencies: deps.map(([project_id, dependency_type]) => ({ project_id, dependency_type })),
  } as unknown as UnifiedVersion;
}

function mod(type: string, projectId: string, enabled = true, name = projectId): Mod {
  return { source: { type, project_id: projectId }, enabled, display_name: name } as unknown as Mod;
}

test("Pflicht-Abhängigkeiten: neu vs. schon installiert", () => {
  const installed = installedProjectsOf([mod("modrinth", "fabric-api")]);
  const plan = planDependencies(version([["fabric-api", "required"], ["cloth", "required"]]), installed);
  assert.deepEqual(plan.required.map((d) => d.projectId), ["cloth"]);
  assert.deepEqual(plan.alreadyInstalled.map((d) => d.projectId), ["fabric-api"]);
});

test("optionale nur, wenn nicht installiert; eingebettete ignoriert", () => {
  const installed = installedProjectsOf([mod("modrinth", "modmenu")]);
  const plan = planDependencies(
    version([["modmenu", "optional"], ["sodium", "optional"], ["lib", "embedded"]]),
    installed,
  );
  assert.deepEqual(plan.optional.map((d) => d.projectId), ["sodium"]);
  assert.equal(plan.required.length, 0);
});

test("Konflikt nur bei aktivem, installiertem inkompatiblem Mod", () => {
  const installed = installedProjectsOf([mod("modrinth", "optifine-like"), mod("modrinth", "off", false)]);
  const plan = planDependencies(version([["optifine-like", "incompatible"], ["off", "incompatible"], ["x", "incompatible"]]), installed);
  assert.deepEqual(plan.conflicts.map((d) => d.projectId), ["optifine-like"]);
  assert.equal(needsConfirmation(plan, "conflicts_only"), true);
});

test("Plattformen werden nicht vermischt", () => {
  const installed = installedProjectsOf([mod("curseforge", "306612")]);
  const plan = planDependencies(version([["306612", "required"]]), installed);
  assert.deepEqual(plan.required.map((d) => d.projectId), ["306612"]);
  const cfPlan = planDependencies(version([["306612", "required"]], "CurseForge"), installed);
  assert.deepEqual(cfPlan.alreadyInstalled.map((d) => d.projectId), ["306612"]);
});

test("Duplikate, Selbstbezug und Abhängigkeiten ohne Projekt-ID werden übersprungen", () => {
  const plan = planDependencies(
    version([["a", "required"], ["a", "required"], ["self", "required"], [undefined, "required"]]),
    [],
  );
  assert.deepEqual(plan.required.map((d) => d.projectId), ["a"]);
});

test("kein Dialog ohne Neuigkeiten", () => {
  const plan = planDependencies(version([]), []);
  assert.equal(needsConfirmation(plan), false);
});

test("lokale Mods zählen nicht als installierte Projekte", () => {
  assert.equal(installedProjectsOf([mod("local", "x")]).length, 0);
});
