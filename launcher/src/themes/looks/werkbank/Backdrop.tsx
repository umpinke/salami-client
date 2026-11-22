import { cn } from "../../../lib/utils";
import { useBackgroundEffectStore } from "../../../store/background-effect-store";
import type { LookBackdropProps } from "../types";

/**
 * Fensterhintergrund der Pixel-Werkbank: ein dunkles, warmes Schachbrett. Hat der Spieler ein
 * eigenes Hintergrundbild gesetzt, bleibt es sichtbar – dann liegt nur ein dünner Schleier darüber.
 */
export function WerkbankBackdrop({ activeTab }: LookBackdropProps) {
  const customMediaUrl = useBackgroundEffectStore((state) => state.customMediaUrl);
  const customMediaOnlyOnPlay = useBackgroundEffectStore((state) => state.customMediaOnlyOnPlay);
  const mediaVisible = Boolean(customMediaUrl) && (!customMediaOnlyOnPlay || activeTab === "play");

  return <div className={cn("wb-backdrop", mediaVisible && "wb-backdrop--media")} aria-hidden="true" />;
}
