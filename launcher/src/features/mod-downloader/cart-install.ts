import { installContentToTarget } from "../../services/content-service";
import UnifiedService from "../../services/unified-service";
import { ContentType } from "../../types/content";
import type { Profile } from "../../types/profile";
import type { CartItem } from "./cart-store";
import { installedProjectsOf, platformOf } from "./dependency-plan";
import { buildInstallPayload } from "./install-payload";
import { selectBestVersion } from "./version-selection";

export interface CartInstallResult {
  ok: CartItem[];
  already: CartItem[];
  skipped: CartItem[];
  failed: Array<{ item: CartItem; error: string }>;
}

export function contentTypeFor(projectType: string): ContentType | null {
  switch (projectType.toLowerCase()) {
    case "mod":
      return ContentType.Mod;
    case "resourcepack":
      return ContentType.ResourcePack;
    case "shader":
      return ContentType.ShaderPack;
    case "datapack":
      return ContentType.DataPack;
    default:
      return null; // Modpacks werden als eigenes Profil installiert, nicht hier
  }
}

/**
 * Installiert alle Warenkorb-Einträge nacheinander in ein Profil – jeweils in der
 * passenden Version. Nacheinander, weil jede Installation das Profil schreibt.
 * Ein Dialog erscheint nur bei Konflikten.
 */
export async function installCart(
  items: readonly CartItem[],
  profile: Profile,
  onProgress?: (current: number, total: number, item: CartItem) => void,
): Promise<CartInstallResult> {
  const result: CartInstallResult = { ok: [], already: [], skipped: [], failed: [] };
  const installed = new Set(installedProjectsOf(profile.mods).map((p) => `${p.platform}:${p.projectId}`));

  for (let index = 0; index < items.length; index++) {
    const item = items[index];
    onProgress?.(index + 1, items.length, item);

    if (installed.has(`${platformOf(item.source)}:${item.projectId}`)) {
      result.already.push(item);
      continue;
    }
    const contentType = contentTypeFor(item.projectType);
    if (!contentType) {
      result.skipped.push(item);
      continue;
    }
    try {
      const response = await UnifiedService.getModVersions({ source: item.source, project_id: item.projectId });
      const choice = selectBestVersion(response.versions, { gameVersion: profile.game_version, loader: profile.loader });
      const payload = choice
        ? buildInstallPayload(profile.id, { project_id: item.projectId, title: item.title, source: item.source }, choice.version, contentType)
        : null;
      if (!payload) {
        result.skipped.push(item);
        continue;
      }
      await installContentToTarget(payload, undefined, { preflight: "conflicts_only" });
      result.ok.push(item);
    } catch (error) {
      result.failed.push({ item, error: error instanceof Error ? error.message : String(error) });
    }
  }
  return result;
}
