import { invoke } from '@tauri-apps/api/core';
import type { UpdateInfo } from '../types/updater';
import type { Profile } from '../types/profile';
import { useProfileStore } from '../store/profile-store';

/**
 * Triggers a refresh of the pack configuration from the backend.
 *
 * @returns A promise that resolves when the refresh is complete.
 * @throws If the backend command fails.
 */
export const refreshNoriskPacks = (): Promise<void> => {
  return invoke('refresh_norisk_packs');
};

/**
 * Triggers a refresh of the standard versions configuration from the backend.
 *
 * @returns A promise that resolves with the standard profiles.
 * @throws If the backend command fails.
 */
export const refreshStandardVersions = (): Promise<Profile[]> => {
  return invoke('refresh_standard_versions');
};

/**
 * Refreshes both the packs and the standard versions configurations, then
 * reloads the profile list.
 */
export const refreshNrcDataOnMount = async (): Promise<void> => {
  useProfileStore.setState({ loading: true, error: null });

  try {
    try {
      await refreshNoriskPacks();
      console.log("Packs updated successfully on mount!");
    } catch (error) {
      console.error("Failed to refresh packs on mount:", error);
    }

    try {
      const standardProfiles = await refreshStandardVersions();
      console.log("Standard Versions updated successfully on mount!");
      useProfileStore.setState({ standardProfiles });
    } catch (error) {
      console.error("Failed to refresh Standard Versions on mount:", error);
    }

    // Salami: Profile immer laden – auch wenn Packs/Standardversionen nicht aktualisiert werden
    // konnten, sonst bleibt die Startseite leer. fetchProfiles setzt loading: false.
    try {
      await useProfileStore.getState().fetchProfiles();
    } catch (error) {
      console.error("Failed to refresh profiles state after pack data update:", error);
    }
  } catch (error) {
    console.error("Error during pack data refresh or profile fetching process:", error);
    useProfileStore.setState({
      error: "Failed to initialize or refresh app data.",
      loading: false,
    });
  }
};

/**
 * Checks if an application update is available.
 * Uses the beta channel setting from the launcher configuration.
 *
 * @returns A promise that resolves to UpdateInfo if an update is available, or null if up to date.
 * @throws If the backend command fails.
 */
export const checkUpdateAvailable = (): Promise<UpdateInfo | null> => {
  return invoke('check_update_available_command');
};

/**
 * Downloads and installs an available application update.
 * Uses the beta channel setting from the launcher configuration.
 * The application will restart automatically after successful installation.
 *
 * @returns A promise that resolves when the update process is complete.
 * @throws If the backend command fails or no update is available.
 */
export const downloadAndInstallUpdate = (): Promise<void> => {
  return invoke('download_and_install_update_command');
};
