import type React from "react";
import { SURFACE, hexAlpha, kante, readableOn } from "../../../ui/design-system";

/**
 * Feinkost-Bausteine für die Profil- und Sync-Pack-Seiten (siehe themes/salami/DESIGN.md).
 * Hover läuft über CSS-Variablen, damit Listenzeilen keinen eigenen Hover-State brauchen.
 */

/** Karte/Listenzeile: warme Fläche, Haarlinie, beim Hover Akzent-Rahmen und weicher Schein. */
export const FK_CARD_CLASS =
  "look-card border bg-[var(--fk-bg)] [border-color:var(--fk-line)] hover:bg-[var(--fk-bg-hover)] hover:[border-color:var(--fk-line-hover)] hover:[box-shadow:var(--fk-glow)] transition-[background-color,border-color,box-shadow,transform] duration-150";

/** `selected`: akzentgetönt (Auswahl); `active`: nur Akzent-Rahmen (z. B. „in Benutzung“). */
export function fkCardStyle(accent: string, selected = false, active = false): React.CSSProperties {
  return {
    borderRadius: "var(--border-radius)",
    "--fk-bg": selected ? hexAlpha(accent, 0.12) : SURFACE.base,
    "--fk-bg-hover": selected ? hexAlpha(accent, 0.16) : SURFACE.hover,
    "--fk-line": selected || active ? hexAlpha(accent, 0.55) : SURFACE.line,
    "--fk-line-hover": hexAlpha(accent, 0.55),
    "--fk-glow": `0 10px 24px -14px ${hexAlpha(accent, 0.6)}`,
  } as React.CSSProperties;
}

/** Flacher Knopf (secondary) mit weicher Unterkante; beim Drücken 1px nach unten. */
export const FK_SOFT_BUTTON_CLASS =
  "look-btn look-btn--secondary border border-white/[0.09] bg-white/[0.08] hover:bg-white/[0.11] text-white/75 hover:text-white shadow-[0_2px_0_rgba(0,0,0,0.32)] active:translate-y-px transition-[background-color,color,transform] duration-150 rounded-[calc(var(--border-radius)*0.8)]";

/** Hauptaktion: volle Akzentfarbe mit harter Unterkante (`kante`). */
export const FK_PRIMARY_BUTTON_CLASS =
  "look-btn look-btn--primary hover:brightness-110 active:translate-y-px transition-[filter,transform] duration-150 rounded-[calc(var(--border-radius)*0.8)] font-minecraft";

export function fkPrimaryStyle(accent: string): React.CSSProperties {
  return { backgroundColor: accent, color: readableOn(accent), boxShadow: kante(accent) };
}

/** Eingabefeld auf warmer Fläche. */
export const FK_INPUT_CLASS =
  "look-input border border-white/[0.09] bg-white/[0.045] focus:border-white/25 outline-none text-white placeholder:text-white/35 rounded-[calc(var(--border-radius)*0.8)]";

/** Rahmen für kleine Vorschaubilder (Welt-Icons, Mod-Icons). */
export const fkThumbStyle: React.CSSProperties = {
  backgroundColor: SURFACE.solid,
  borderColor: SURFACE.lineStrong,
  borderRadius: "calc(var(--border-radius) * 0.8)",
};
