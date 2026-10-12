import { useEffect } from "react";
import { getLauncherConfig, setLauncherConfig } from "../../services/launcher-config-service";
import { applySalamiTexts, registerSalamiStrings } from "./salami-texts";
import { useSalamiMode } from "./useSalamiMode";
import "./salami-theme.css";

registerSalamiStrings();

/**
 * Unsichtbare Komponente (einmal in App gemountet), die den Salami-Modus umsetzt:
 * - `data-theme="salami"` auf <html> für die CSS-Overrides,
 * - Salami-Texte an/aus,
 * - `salami_theme` in der Launcher-Config, damit das Spiel `-Dsalami.theme` bekommt.
 */
export function SalamiModeSync() {
  const { enabled } = useSalamiMode();

  useEffect(() => {
    const root = document.documentElement;
    if (enabled) {
      root.dataset.theme = "salami";
    } else if (root.dataset.theme === "salami") {
      delete root.dataset.theme;
    }
    applySalamiTexts(enabled);
  }, [enabled]);

  useEffect(() => {
    let cancelled = false;
    getLauncherConfig()
      .then((config) => {
        if (cancelled || config.salami_theme === enabled) return;
        return setLauncherConfig({ ...config, salami_theme: enabled });
      })
      .catch((error) => console.warn("[Salami] Could not sync salami_theme to launcher config", error));
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return null;
}
