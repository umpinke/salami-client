import { cn } from "../../../lib/utils";
import { useBackgroundEffectStore } from "../../../store/background-effect-store";
import type { LookBackdropProps } from "../types";

/**
 * Hintergrund des Looks „Feinkost-Theke“: Holzwand über das ganze Fenster, oben die rot-creme
 * gestreifte Markise mit Bogenkante. Die Leiste (Logo, Konto, Fenster-Knöpfe) sitzt als Schilder
 * darauf. Reine Zierde – `pointer-events: none`; Maße kommen aus `look.css` (`--theke-*`).
 *
 * Hat der Spieler ein eigenes Hintergrundbild gewählt, bleiben von der Wand nur die Bretterfugen,
 * damit das Bild darunter zu sehen ist (gleiche Bedingung wie in `AppLayout`).
 */
export function ThekeBackdrop({ activeTab }: LookBackdropProps) {
  const customMediaUrl = useBackgroundEffectStore((state) => state.customMediaUrl);
  const onlyOnPlay = useBackgroundEffectStore((state) => state.customMediaOnlyOnPlay);
  const showsCustomMedia = Boolean(customMediaUrl) && (!onlyOnPlay || activeTab === "play");

  return (
    <div className={cn("theke-backdrop", showsCustomMedia && "has-media")} aria-hidden="true">
      <div className="theke-wall" />
      <div className="theke-awning-shade" />
      <div className="theke-awning" />
    </div>
  );
}
