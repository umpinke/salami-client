import { toast } from "react-hot-toast";
import { invoke } from "@tauri-apps/api/core";
import i18n from "../../i18n/i18n";
import { getProfile } from "../../services/profile-service";
import UnifiedService from "../../services/unified-service";
import { ContentType, type InstallContentPayload } from "../../types/content";
import type { PlannedDependency } from "./dependency-plan";
import { buildInstallPayload } from "./install-payload";
import { selectBestVersion } from "./version-selection";
import "./i18n";

/**
 * Installiert die im Dialog angehakten optionalen Abhängigkeiten – jeweils in der
 * zum Profil passenden Version. Fehler einzelner Mods brechen den Rest nicht ab.
 *
 * Ruft den Backend-Befehl direkt auf (statt content-service), um einen
 * Import-Zyklus zu vermeiden und keinen zweiten Abhängigkeits-Dialog zu öffnen.
 */
export async function installOptionalDependencies(
  parent: InstallContentPayload,
  optional: readonly PlannedDependency[],
): Promise<void> {
  const profile = await getProfile(parent.profile_id);
  let installed = 0;
  for (const dep of optional) {
    const name = dep.name || dep.projectId;
    try {
      const response = await UnifiedService.getModVersions({ source: parent.source, project_id: dep.projectId });
      const choice = selectBestVersion(response.versions, { gameVersion: profile.game_version, loader: profile.loader });
      if (!choice) {
        toast.error(
          i18n.t("salami.downloader.no_compatible", { name, game: profile.game_version, loader: profile.loader }),
        );
        continue;
      }
      const payload = buildInstallPayload(
        profile.id,
        { project_id: dep.projectId, title: name, source: parent.source },
        choice.version,
        ContentType.Mod,
      );
      if (!payload) continue;
      await invoke<void>("install_content_to_profile", { payload });
      installed++;
    } catch (error) {
      toast.error(i18n.t("salami.downloader.optional_failed", { name, error: String(error) }));
    }
  }
  if (installed > 0) {
    toast.success(i18n.t("salami.downloader.optional_installed", { count: installed }));
  }
}
