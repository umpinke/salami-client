import type React from "react";
import { SALAMI_COLORS } from "./salami-theme";
import { SalamiSlice } from "./SalamiSlice";

/**
 * Bühne der Startseite: eine dicke Salamischeibe in Schrägansicht, auf der die Spielfigur steht.
 * Die Dicke entsteht aus derselben Scheibe, dunkler und ein paar Pixel tiefer.
 */
export function SalamiStage({ width = 420, className }: { width?: number; className?: string }) {
  const squash = 0.26;
  const height = Math.round(width * squash);
  const slice = (style: React.CSSProperties) => (
    <div
      className="absolute left-0 top-0"
      style={{ width, height: width, transform: `scaleY(${squash})`, transformOrigin: "top left", ...style }}
    >
      <SalamiSlice size={width} />
    </div>
  );
  return (
    <div className={`look-stage ${className ?? ""}`} style={{ width, height: height + 14 }} aria-hidden="true">
      <div className="relative h-full w-full">
      {/* Schatten auf dem Boden */}
      <div
        className="absolute left-1/2 -translate-x-1/2"
        style={{
          top: height * 0.55,
          width: width * 1.08,
          height: height * 0.9,
          borderRadius: "50%",
          background: "radial-gradient(closest-side, rgba(0,0,0,0.55), rgba(0,0,0,0))",
        }}
      />
      {/* Rand der Scheibe (Dicke) */}
      {slice({ top: 12, filter: "brightness(0.45) saturate(0.8)" })}
      {slice({ top: 6, filter: "brightness(0.6)" })}
      {/* Schnittfläche */}
      {slice({ top: 0 })}
      </div>
    </div>
  );
}

/** Etikett wie an der Feinkosttheke: cremefarbenes Schildchen mit Lochung. */
export function DeliLabel({
  children,
  tone = "fat",
  size = "md",
}: {
  children: React.ReactNode;
  tone?: "fat" | "red";
  size?: "sm" | "md";
}) {
  const background = tone === "red" ? SALAMI_COLORS.red : SALAMI_COLORS.fat;
  const color = tone === "red" ? SALAMI_COLORS.fat : SALAMI_COLORS.pepper;
  return (
    <span
      className={
        size === "sm"
          ? `look-label look-label--${tone} inline-flex items-center gap-1.5 whitespace-nowrap py-0.5 pl-1.5 pr-2 font-minecraft text-xs leading-none`
          : `look-label look-label--${tone} inline-flex items-center gap-2 whitespace-nowrap py-1 pl-2 pr-3 font-minecraft text-sm leading-none`
      }
      style={{
        background,
        color,
        borderRadius: "4px 10px 10px 4px",
        boxShadow: "0 2px 0 rgba(0,0,0,0.35)",
      }}
    >
      <span
        className={size === "sm" ? "h-1.5 w-1.5 flex-shrink-0" : "h-2 w-2 flex-shrink-0"}
        style={{ borderRadius: 999, background: tone === "red" ? SALAMI_COLORS.redDark : SALAMI_COLORS.casing }}
      />
      {children}
    </span>
  );
}
