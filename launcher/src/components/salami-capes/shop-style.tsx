import type React from "react";
import { useThemeStore } from "../../store/useThemeStore";
import { SURFACE, hexAlpha, kopfband } from "../ui/design-system";

/**
 * Karten im Shop im Feinkost-Stil (Vorbild: Profil-Kachel): ruhige Fläche mit Haarlinie, beim Hover
 * 2px hoch, Rand und weicher Schein im Akzent. Der Hover läuft über CSS-Variablen, damit die Karten
 * keinen eigenen Zustand brauchen (die Seiten merken sich schon, welche Karte die Vorschau zeigt).
 */
export const SHOP_CARD_CLASS =
  "look-card border border-[color:var(--shop-line)] transition-[transform,border-color,box-shadow,background-color] duration-200 hover:border-[color:var(--shop-hover)] hover:shadow-[0_10px_24px_-12px_var(--shop-glow)] motion-safe:hover:-translate-y-0.5";

export function shopCardStyle(accent: string, selected = false): React.CSSProperties {
  return {
    "--shop-line": selected ? accent : SURFACE.line,
    "--shop-hover": selected ? accent : hexAlpha(accent, 0.55),
    "--shop-glow": hexAlpha(accent, 0.6),
    backgroundColor: selected ? hexAlpha(accent, 0.1) : SURFACE.base,
    borderRadius: "var(--border-radius)",
  } as React.CSSProperties;
}

/** Bildfeld in den Karten und in der großen Vorschau: etwas dunkler als die Karte. */
export const SHOP_WELL: React.CSSProperties = {
  backgroundColor: "rgba(0, 0, 0, 0.28)",
  borderRadius: "calc(var(--border-radius) * 0.8)",
};

/** Überschrift eines Abschnitts im Shop (Salami-Capes, Deine Capes, Galerie). */
export function ShopHeading({ children, className }: { children: React.ReactNode; className?: string }) {
  const accent = useThemeStore((s) => s.accentColor.value);
  return (
    <h3 className={`flex items-center gap-2.5 font-minecraft text-lg normal-case tracking-normal text-white ${className ?? ""}`}>
      <span className="h-[3px] w-5 flex-shrink-0" style={{ backgroundColor: accent }} aria-hidden="true" />
      {children}
    </h3>
  );
}

/**
 * Große Vorschau links auf allen Shop-Seiten: Karte mit Kopfband im Salami-Anschnitt, darunter die
 * Figur bzw. das Cape und der Text zum gezeigten Eintrag.
 */
export function ShopPreviewCard({
  viewer,
  viewerStyle,
  children,
}: {
  viewer: React.ReactNode;
  viewerStyle?: React.CSSProperties;
  children: React.ReactNode;
}) {
  const accent = useThemeStore((s) => s.accentColor.value);
  return (
    <div
      className="flex flex-col overflow-hidden border"
      style={{ backgroundColor: SURFACE.base, borderColor: SURFACE.line, borderRadius: "var(--border-radius)" }}
    >
      <div className="salami-fat-spots h-3 flex-shrink-0" style={kopfband(accent)} aria-hidden="true" />
      <div className="relative h-[300px] flex-shrink-0 overflow-hidden" style={viewerStyle}>
        {viewer}
      </div>
      <div className="border-t px-4 pb-4 pt-3" style={{ borderColor: SURFACE.line }}>
        {children}
      </div>
    </div>
  );
}
