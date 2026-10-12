import type { Profile } from '../types/profile';
import type { UnifiedVersion } from '../types/unified';
import type { ContentInstallStatus } from '../types/profile';
import { findBestVersionFor } from '../features/mod-downloader/version-selection';

export const statusForNewInstall = (
  previous?: ContentInstallStatus | null,
): ContentInstallStatus => ({
  is_installed: true,
  is_included_in_norisk_pack: previous?.is_included_in_norisk_pack || false,
  is_specific_version_in_pack: previous?.is_specific_version_in_pack || false,
  is_enabled: true,
  found_item_details: previous?.found_item_details || null,
  norisk_pack_item_details: previous?.norisk_pack_item_details || null,
});

/**
 * Passende Version für ein Profil – oder `null`, wenn keine kompatibel ist.
 * Salami: kein stiller Rückfall mehr auf beliebige Versionen; Quilt nimmt Fabric-Mods
 * (siehe features/mod-downloader/version-selection.ts).
 */
export const findBestVersionForProfile = (
  profile: Profile,
  versions: UnifiedVersion[],
): UnifiedVersion | null => findBestVersionFor(profile, versions);
