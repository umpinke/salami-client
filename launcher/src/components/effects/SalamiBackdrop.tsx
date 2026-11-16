import { hexAlpha } from "../ui/design-system";

interface SalamiBackdropProps {
  accentColorValue: string;
  /** Mittelpunkt der Ringe in Prozent der Fläche. */
  focus?: { x: number; y: number };
  animated?: boolean;
  className?: string;
}

/**
 * Standard-Hintergrund des Salami Client: weiches Licht in der Akzentfarbe und feine,
 * nach außen verblassende Ringe (wie eine angeschnittene Scheibe). Reines CSS – kein
 * Canvas, damit der Launcher neben dem Spiel keine Leistung zieht.
 */
export function SalamiBackdrop({ accentColorValue, focus = { x: 74, y: 56 }, animated = false, className }: SalamiBackdropProps) {
  const at = `${focus.x}% ${focus.y}%`;
  const ringMask = `radial-gradient(circle at ${at}, #000 0%, rgba(0,0,0,0.55) 30%, transparent 62%)`;

  return (
    <div className={`pointer-events-none absolute inset-0 overflow-hidden ${className ?? ""}`} aria-hidden="true">
      <div
        className={animated ? "salami-backdrop-drift absolute inset-0" : "absolute inset-0"}
        style={{
          backgroundImage: [
            `radial-gradient(42% 52% at ${at}, ${hexAlpha(accentColorValue, 0.22)} 0%, transparent 70%)`,
            `radial-gradient(48% 44% at 4% 104%, ${hexAlpha(accentColorValue, 0.12)} 0%, transparent 72%)`,
            "radial-gradient(60% 40% at 30% -10%, rgba(255,255,255,0.05) 0%, transparent 70%)",
          ].join(", "),
        }}
      />
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `repeating-radial-gradient(circle at ${at}, transparent 0 47px, rgba(255,255,255,0.055) 47px 48px)`,
          maskImage: ringMask,
          WebkitMaskImage: ringMask,
        }}
      />
      <div
        className="absolute inset-0"
        style={{ backgroundImage: "linear-gradient(to bottom, rgba(0,0,0,0.28), transparent 18%, transparent 78%, rgba(0,0,0,0.35))" }}
      />
    </div>
  );
}
