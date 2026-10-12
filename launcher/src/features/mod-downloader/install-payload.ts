import type { ContentType, InstallContentPayload } from "../../types/content";
import type { ModPlatform, UnifiedVersion } from "../../types/unified";

/** Minimal nötige Projektdaten für eine Installation. */
export interface InstallProjectRef {
  project_id: string;
  title: string;
  source: ModPlatform;
}

/**
 * Baut den Install-Payload für eine Version (primäre Datei, inkl. SHA1 und
 * CurseForge-Fingerprint für spätere Update-Prüfungen).
 */
export function buildInstallPayload(
  profileId: string,
  project: InstallProjectRef,
  version: UnifiedVersion,
  contentType: ContentType,
): InstallContentPayload | null {
  const file = version.files.find((f) => f.primary) ?? version.files[0];
  if (!file) return null;
  return {
    profile_id: profileId,
    project_id: project.project_id,
    version_id: version.id,
    file_name: file.filename,
    download_url: file.url,
    file_hash_sha1: file.hashes?.sha1 || undefined,
    file_fingerprint: file.fingerprint || undefined,
    content_name: project.title,
    version_number: version.version_number,
    content_type: contentType,
    loaders: version.loaders,
    game_versions: version.game_versions,
    source: project.source,
  };
}
