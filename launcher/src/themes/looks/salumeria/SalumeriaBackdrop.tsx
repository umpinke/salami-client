import type { LookBackdropProps } from "../types";

/**
 * Hintergrund der Salumeria: feines Leinen auf fast schwarzem Grund, ein leiser roter Schein und
 * der dünne Doppelrahmen ums Fenster. Auf der Startseite sitzt der Schein hinter dem Porträt,
 * sonst oben rechts. Die Grundfarbe selbst setzt `look.css` an `.look-shell`.
 */
export function SalumeriaBackdrop({ activeTab }: LookBackdropProps) {
  const onPlay = activeTab === "play";
  return (
    <div className="sal-backdrop" data-page={onPlay ? "play" : "other"} aria-hidden="true">
      <div className="sal-backdrop__glow" />
      <div className="sal-backdrop__linen" />
      <div className="sal-backdrop__frame" />
    </div>
  );
}
