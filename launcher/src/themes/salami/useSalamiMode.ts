import { useLauncherThemeStore } from "../../store/launcher-theme-store";
import { SALAMI_THEME_ID } from "./salami-theme";

/**
 * Salami-Modus an/aus. Technisch ein Launcher-Theme (siehe launcher-theme-store);
 * alle Nebenwirkungen (CSS, Texte, Backend-Config) erledigt <SalamiModeSync />.
 */
export function useSalamiMode() {
  const selectedThemeId = useLauncherThemeStore((s) => s.selectedThemeId);
  const selectTheme = useLauncherThemeStore((s) => s.selectTheme);

  const enabled = selectedThemeId === SALAMI_THEME_ID;
  const setEnabled = (value: boolean) => selectTheme(value ? SALAMI_THEME_ID : null);

  return { enabled, setEnabled };
}
