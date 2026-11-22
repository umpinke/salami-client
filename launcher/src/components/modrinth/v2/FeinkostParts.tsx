"use client";

import type React from "react";
import { forwardRef, useState } from "react";
import { Icon } from "@iconify/react";
import { cn } from "../../../lib/utils";
import { DeliLabel } from "../../../themes/salami/SalamiStage";
import { useThemeStore } from "../../../store/useThemeStore";
import { hexAlpha, kante, KANTE_SOFT, mixHex, readableOn, SURFACE } from "../../ui/design-system";

/**
 * Kleine Feinkost-Bausteine für Mod-Browser, Mod-Detailseite und Warenkorb
 * (siehe themes/salami/DESIGN.md): Knöpfe mit harter Unterkante, Abschnittsköpfe
 * mit Akzentstrich und die Kartenfläche mit Hover-Anheben.
 */

export type KanteTone = "primary" | "secondary" | "danger" | "ghost" | "done";

interface KanteButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: KanteTone;
  size?: "sm" | "md" | "lg";
  icon?: React.ReactNode;
  /** Quadratischer Knopf nur mit Symbol (Text gehört dann in `title`/`aria-label`). */
  iconOnly?: boolean;
  /** Eingeschalteter Zustand eines Umschalt-Knopfs (z. B. Filter offen, im Warenkorb). */
  active?: boolean;
}

const SIZE: Record<NonNullable<KanteButtonProps["size"]>, { text: string; square: string }> = {
  sm: { text: "h-8 px-3 text-sm", square: "h-8 w-8" },
  md: { text: "h-9 px-3.5 text-sm", square: "h-9 w-9" },
  lg: { text: "h-11 px-5 text-base", square: "h-11 w-11" },
};

const DANGER = "#e0705f";

/** Feinkost-Knopf: volle Akzentfarbe mit harter Unterkante, beim Drücken 1px nach unten. */
export const KanteButton = forwardRef<HTMLButtonElement, KanteButtonProps>(
  ({ tone = "secondary", size = "md", icon, iconOnly, active, className, style, disabled, children, type, ...rest }, ref) => {
    const accent = useThemeStore((s) => s.accentColor);
    const [pressed, setPressed] = useState(false);
    const down = pressed && !disabled;

    let skin: React.CSSProperties;
    switch (tone) {
      case "primary":
        skin = {
          backgroundColor: accent.value,
          color: readableOn(accent.value),
          boxShadow: kante(accent.value, down ? 1 : 3),
        };
        break;
      case "done":
        // Erledigt (z. B. „Installiert“): ruhige Fläche, kein Aufruf zum Klicken
        skin = {
          backgroundColor: SURFACE.raised,
          color: "#f3e3d3",
          border: `1px solid ${SURFACE.line}`,
          boxShadow: KANTE_SOFT,
        };
        break;
      case "danger":
        skin = {
          backgroundColor: hexAlpha(DANGER, 0.14),
          color: "#f4b2a6",
          border: `1px solid ${hexAlpha(DANGER, 0.35)}`,
          boxShadow: down ? "none" : KANTE_SOFT,
        };
        break;
      case "ghost":
        skin = { backgroundColor: "transparent", color: "rgba(255,255,255,0.7)" };
        break;
      default:
        skin = {
          backgroundColor: active ? hexAlpha(accent.value, 0.2) : SURFACE.hover,
          color: active ? "#ffffff" : "rgba(255,255,255,0.8)",
          border: `1px solid ${active ? hexAlpha(accent.value, 0.55) : SURFACE.line}`,
          boxShadow: down ? "none" : KANTE_SOFT,
        };
    }

    return (
      <button
        ref={ref}
        type={type ?? "button"}
        disabled={disabled}
        onPointerDown={() => setPressed(true)}
        onPointerUp={() => setPressed(false)}
        onPointerLeave={() => setPressed(false)}
        className={cn(
          "look-btn",
          `look-btn--${tone === "done" ? "secondary" : tone}`,
          active && "look-btn--active",
          "inline-flex flex-shrink-0 items-center justify-center gap-1.5 whitespace-nowrap font-minecraft leading-none",
          "transition-[filter,background-color,transform,box-shadow] duration-150 motion-reduce:transition-none",
          iconOnly ? SIZE[size].square : SIZE[size].text,
          disabled
            ? tone === "done" ? "cursor-default" : "cursor-not-allowed opacity-50"
            : tone === "ghost" ? "hover:bg-white/[0.08] hover:text-white" : "hover:brightness-110",
          className,
        )}
        style={{
          borderRadius: "calc(var(--border-radius) * 0.8)",
          transform: down ? "translateY(1px)" : undefined,
          ...skin,
          ...style,
        }}
        {...rest}
      >
        {icon}
        {!iconOnly && children}
      </button>
    );
  },
);
KanteButton.displayName = "KanteButton";

/** Abschnittskopf wie auf der Startseite: kurzer Akzentstrich vor dem Titel. */
export function SectionHeading({
  children,
  right,
  className,
  size = "md",
}: {
  children: React.ReactNode;
  right?: React.ReactNode;
  className?: string;
  size?: "sm" | "md";
}) {
  const accent = useThemeStore((s) => s.accentColor);
  return (
    <div className={cn("flex items-center justify-between gap-3", className)}>
      <h3
        className={cn(
          "flex min-w-0 items-center gap-2.5 font-minecraft normal-case tracking-normal text-white",
          size === "sm" ? "text-sm text-white/85" : "text-lg",
        )}
      >
        <span className="h-[3px] w-5 flex-shrink-0" style={{ backgroundColor: accent.value }} aria-hidden="true" />
        <span className="truncate">{children}</span>
      </h3>
      {right}
    </div>
  );
}

/** Kartenfläche (SURFACE + Haarlinie); mit `hovered` 2px angehoben, Akzentrand und weicher Schein. */
export function cardSurface(accentHex: string, hovered = false, lift = true): React.CSSProperties {
  return {
    backgroundColor: hovered ? SURFACE.hover : SURFACE.base,
    border: `1px solid ${hovered ? hexAlpha(accentHex, 0.55) : SURFACE.line}`,
    borderRadius: "var(--border-radius)",
    transform: hovered && lift ? "translateY(-2px)" : undefined,
    boxShadow: hovered ? `0 10px 24px -12px ${hexAlpha(accentHex, 0.6)}` : KANTE_SOFT,
  };
}

/** Schlichte Kartenfläche ohne Hover (Abschnitte, Filterleisten). */
export const PLAIN_CARD: React.CSSProperties = {
  backgroundColor: SURFACE.base,
  border: `1px solid ${SURFACE.line}`,
  borderRadius: "var(--border-radius)",
};

/** Gewählter Filter als Theken-Etikett mit Schließen-Kreuz; ein Klick entfernt ihn. */
export function RemovableLabel({
  children,
  tone = "fat",
  onRemove,
  removeLabel,
}: {
  children: React.ReactNode;
  tone?: "fat" | "red";
  onRemove: () => void;
  removeLabel?: string;
}) {
  return (
    <button
      type="button"
      onClick={onRemove}
      aria-label={removeLabel}
      className="flex-shrink-0 transition-[filter,transform] duration-150 hover:brightness-105 active:translate-y-px motion-reduce:transition-none"
    >
      <DeliLabel size="sm" tone={tone}>
        {children}
        <Icon icon="solar:close-circle-bold" className="h-3.5 w-3.5 opacity-70" />
      </DeliLabel>
    </button>
  );
}

/** Ruhiges Kennzeichen (z. B. „Release“) – neutraler als ein Etikett. */
export function QuietTag({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span
      className={cn("look-tag inline-flex items-center gap-1 px-1.5 py-0.5 font-minecraft text-xs leading-none text-white/70", className)}
      style={{ backgroundColor: SURFACE.raised, borderRadius: 4 }}
    >
      {children}
    </span>
  );
}

const LOADERS = new Set(["fabric", "forge", "neoforge", "quilt", "liteloader", "rift", "modloader", "iris", "optifine", "canvas", "vanilla"]);

/** Modrinth mischt Loader unter die Kategorien – für die Etiketten getrennt halten. */
export function isLoaderTag(tag: string): boolean {
  return LOADERS.has(tag.toLowerCase());
}

const LOADER_NAMES: Record<string, string> = { neoforge: "NeoForge", optifine: "OptiFine", liteloader: "LiteLoader" };

/** „world-generation“ → „World generation“, Loader mit richtigem Namen. Ohne CSS-Großschreibung. */
export function tagLabel(tag: string): string {
  const lower = tag.toLowerCase();
  if (LOADER_NAMES[lower]) return LOADER_NAMES[lower];
  const text = tag.replace(/[-_]/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Übersetzungsschlüssel für den Inhaltstyp in der Einzahl („Ressourcenpaket“). */
export function projectTypeKey(type: string | null | undefined): string | null {
  switch ((type ?? "").toLowerCase()) {
    case "mod": return "profiles.content.mod";
    case "modpack": return "profiles.v3.stats.modpack";
    case "resourcepack": return "profiles.content.resourcePack";
    case "shader": return "profiles.content.shaderPack";
    case "datapack": return "profiles.content.dataPack";
    default: return null;
  }
}

/** Dunkle Fläche hinter Bildern/Symbolen (warm statt grau). */
export const ICON_WELL = mixHex("#2b1d1a", "#000000", 0.25);
