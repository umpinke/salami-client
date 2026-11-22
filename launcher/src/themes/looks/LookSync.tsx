import { useEffect } from "react";
import { getLauncherConfig, setLauncherConfig } from "../../services/launcher-config-service";
import { useThemeStore } from "../../store/useThemeStore";
import { useLook } from "./registry";

/**
 * Setzt `<html data-look="…">`, woran die Regeln in `themes/looks/<id>/look.css` hängen.
 * Mit `syncToGame` (nur im Hauptfenster) landet der gewählte Look außerdem in der Launcher-Config,
 * damit das Spiel ihn als `-Dsalami.look` bekommt und die Salami-Menüs im Spiel genauso aussehen.
 */
export function LookSync({ syncToGame = false }: { syncToGame?: boolean }) {
  const look = useLook();
  const chosen = useThemeStore((state) => state.look);
  useEffect(() => {
    document.documentElement.dataset.look = look.id;
  }, [look.id]);

  useEffect(() => {
    if (!syncToGame || !chosen) return;
    let cancelled = false;
    getLauncherConfig()
      .then((config) => {
        if (cancelled || config.salami_look === chosen) return;
        return setLauncherConfig({ ...config, salami_look: chosen });
      })
      .catch((error) => console.warn("[Looks] Could not pass the look to the game", error));
    return () => {
      cancelled = true;
    };
  }, [syncToGame, chosen]);

  return null;
}
