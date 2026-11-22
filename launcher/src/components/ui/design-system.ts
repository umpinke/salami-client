import type { AccentColor } from "../../store/useThemeStore";

export type ComponentSize = "xs" | "sm" | "md" | "lg" | "xl";
export type ComponentVariant = "default" | "flat" | "3d" | "elevated" | "secondary" | "flat-secondary" | "ghost" | "warning" | "destructive" | "info" | "success";
export type StateVariant = "error" | "success" | "warning" | "info";

export interface DesignConfig {
  isRoundTheme?: boolean;
  accentColor?: AccentColor;
  isAnimationEnabled?: boolean;
  borderRadius?: number;
}

export interface ColorTokens {
  main: string;
  light: string;
  dark: string;
  text: string;
}

export const designTokens = {
  spacing: {
    xs: "0.5rem",
    sm: "0.75rem", 
    md: "1rem",
    lg: "1.25rem",
    xl: "1.5rem"
  },
  typography: {
    xs: "0.75rem",
    sm: "0.875rem",
    md: "1rem", 
    lg: "1.125rem",
    xl: "1.25rem"
  },
  animations: {
    fast: "0.15s",
    normal: "0.2s",
    slow: "0.3s"
  },
  shadows: {
    sm: "0 2px 4px rgba(0,0,0,0.1)",
    md: "0 4px 8px rgba(0,0,0,0.15)",
    lg: "0 8px 16px rgba(0,0,0,0.2)"
  }
};

/**
 * Salami-Designsprache: neutrale, warme Flächen mit Haarlinien statt akzentgetöntem Glas.
 * Der Akzent bleibt Hauptaktionen und aktiven Zuständen vorbehalten.
 */
/**
 * Die Werte sind CSS-Variablen mit Rückfallwert: jeder Look (themes/looks/<id>/look.css) setzt sie
 * für sich, und alle Seiten ziehen mit. Deshalb nie durch `hexAlpha`/`mixHex` schicken – das sind
 * keine Hex-Farben. Für halbtransparente Varianten gibt es `SURFACE.veil`.
 */
export const SURFACE = {
  base: "var(--surface-base, rgba(255, 255, 255, 0.045))",
  hover: "var(--surface-hover, rgba(255, 255, 255, 0.08))",
  raised: "var(--surface-raised, rgba(255, 255, 255, 0.11))",
  line: "var(--surface-line, rgba(255, 255, 255, 0.09))",
  lineStrong: "var(--surface-line-strong, rgba(255, 255, 255, 0.18))",
  /** deckend – für Dialoge, Menüs und Tooltips (warm, passend zum Pfefferbraun-Grund) */
  solid: "var(--surface-solid, #1b1513)",
  solidRaised: "var(--surface-solid-raised, #261d1a)",
  /** halbtransparenter Schleier über Inhalten (Laden, gesperrt) */
  veil: "var(--surface-veil, rgba(27, 21, 19, 0.85))",
} as const;

const parseHex = (hex: string): [number, number, number] | null => {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})/i.exec(hex ?? "");
  return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)] : null;
};

/** `#rrggbb` + Deckkraft → `rgba(...)`; alles andere bleibt unverändert. */
export const hexAlpha = (hex: string, alpha: number): string => {
  const rgb = parseHex(hex);
  return rgb ? `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})` : hex;
};

/** Mischt zwei Hex-Farben (t = Anteil von `b`). */
export const mixHex = (a: string, b: string, t: number): string => {
  const x = parseHex(a);
  const y = parseHex(b);
  if (!x || !y) return a;
  const c = x.map((v, i) => Math.round(v + (y[i] - v) * t));
  return `rgb(${c[0]}, ${c[1]}, ${c[2]})`;
};

/** Lesbare Schriftfarbe auf einer vollflächigen Farbe. */
export const readableOn = (hex: string): string => {
  const rgb = parseHex(hex);
  if (!rgb) return "#ffffff";
  const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
  return luminance > 0.62 ? "#1b1415" : "#ffffff";
};

export interface ButtonSkin {
  background: string;
  border: string;
  color: string;
  shadow: string;
}

/** Aussehen aller Knöpfe: gefüllt (3d), getönt (Standard/Status), neutral oder unsichtbar. */
export const getButtonSkin = (variant: ComponentVariant, accentColor: AccentColor, hovered: boolean): ButtonSkin => {
  if (variant === "ghost") {
    return { background: hovered ? SURFACE.hover : "transparent", border: "transparent", color: "#ffffff", shadow: "none" };
  }
  // Salami-Feinkost: Knöpfe haben eine harte Kante nach unten statt weichem Schein
  if (variant === "secondary" || variant === "flat-secondary") {
    return { background: hovered ? SURFACE.raised : SURFACE.hover, border: SURFACE.line, color: "#f3f1f1", shadow: KANTE_SOFT };
  }
  if (variant === "3d" || variant === "elevated") {
    const fill = hovered ? accentColor.light || accentColor.value : accentColor.value;
    return {
      background: fill,
      border: "transparent",
      color: readableOn(accentColor.value),
      shadow: kante(accentColor.value),
    };
  }
  const colors = getVariantColors(variant, accentColor);
  return {
    background: hexAlpha(colors.main, hovered ? 0.28 : 0.16),
    border: hexAlpha(colors.main, hovered ? 0.5 : 0.28),
    color: colors.text,
    shadow: KANTE_SOFT,
  };
};

/** Harte Unterkante eines vollen Knopfs (Feinkost-Stil): die Knopffarbe, kräftig abgedunkelt. */
export const kante = (hex: string, depth = 3): string =>
  `var(--look-kante, 0 ${depth}px 0 ${mixHex(hex, "#000000", 0.45)})`;
/** Unterkante für flache Knöpfe und Karten. */
export const KANTE_SOFT = "var(--look-kante-soft, 0 2px 0 rgba(0, 0, 0, 0.32))";

/**
 * Kopfband im Salami-Anschnitt (mit der CSS-Klasse `salami-fat-spots` kombinieren). Immer in
 * Salami-Rot – es ist das Erkennungszeichen, unabhängig von der gewählten Akzentfarbe (mit Grau
 * sah es sonst aus wie Stein). Der Parameter bleibt für bestehende Aufrufe.
 */
export const kopfband = (_accentHex?: string) => ({
  backgroundColor: `var(--look-band-bg, ${mixHex("#b23a3a", "#2b1d1a", 0.3)})`,
  borderBottom: `var(--look-band-border, 3px solid ${mixHex("#b23a3a", "#2b1d1a", 0.6)})`,
});

export const getVariantColors =(variant: ComponentVariant, accentColor: AccentColor): ColorTokens => {
  switch (variant) {
    case "warning":
      return {
        main: "#f59e0b",
        light: "#fbbf24", 
        dark: "#d97706",
        text: "#fef3c7"
      };
    case "destructive":
      return {
        main: "#ef4444",
        light: "#f87171",
        dark: "#dc2626", 
        text: "#fee2e2"
      };
    case "info":
      // Salami: Hinweise in Fett-Creme statt kaltem Blau
      return {
        main: "#d9c2a7",
        light: "#f3e3d3",
        dark: "#b39a7f",
        text: "#f3e3d3"
      };
    case "success":
      return {
        main: "#10b981",
        light: "#34d399",
        dark: "#059669",
        text: "#d1fae5"
      };
    case "secondary":
      return {
        main: "#6b7280",
        light: "#9ca3af",
        dark: "#4b5563",
        text: "#f3f4f6"
      };
    case "ghost":
      return {
        main: "transparent",
        light: "transparent", 
        dark: "transparent",
        text: "#ffffff"
      };
    default:
      return {
        main: accentColor.value,
        light: accentColor.hoverValue || accentColor.value,
        dark: accentColor.value,
        text: "#ffffff"
      };
  }
};

export const getSizeClasses = (size: ComponentSize, element: "button" | "input" | "badge" = "button"): string => {  if (element === "badge") {
    switch (size) {
      case "xs": return "px-1.5 py-0.5 text-xs min-h-[18px]";
      case "sm": return "px-1.5 py-0.5 text-xs min-h-[20px]";
      case "md": return "px-2 py-1 text-sm min-h-[24px]";
      case "lg": return "px-2.5 py-1 text-base min-h-[28px]";
      case "xl": return "px-3 py-1.5 text-lg min-h-[32px]";
      default: return "px-2 py-1 text-sm min-h-[24px]";
    }
  }
  
  if (element === "input") {
    switch (size) {
      case "xs": return "h-[36px] px-5 text-xs";
      case "sm": return "h-[42px] px-6 text-sm";
      case "md": return "h-[50px] px-8 text-sm";
      case "lg": return "h-[58px] px-10 text-base";
      case "xl": return "h-[66px] px-12 text-base";
      default: return "h-[50px] px-8 text-sm";
    }
  }
  
  switch (size) {
    case "xs": return "h-[36px] px-5 py-2 text-xs";
    case "sm": return "h-[42px] px-6 py-2 text-sm";
    case "md": return "h-[50px] px-8 py-2.5 text-sm";
    case "lg": return "h-[58px] px-10 py-3 text-base";
    case "xl": return "h-[66px] px-12 py-4 text-base";
    default: return "h-[50px] px-8 py-2.5 text-sm";
  }
};

export const getAccessibilityProps = (props: {
  label?: string;
  description?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
}): Record<string, any> => {
  const accessibilityProps: Record<string, any> = {};
  
  if (props.label) {
    accessibilityProps["aria-label"] = props.label;
  }
  
  if (props.description) {
    accessibilityProps["aria-describedby"] = `desc-${Math.random().toString(36).substr(2, 9)}`;
  }
  
  if (props.error) {
    accessibilityProps["aria-invalid"] = true;
    accessibilityProps["aria-describedby"] = `error-${Math.random().toString(36).substr(2, 9)}`;
  }
  
  if (props.required) {
    accessibilityProps["aria-required"] = true;
  }
  
  if (props.disabled) {
    accessibilityProps["aria-disabled"] = true;
  }
  
  return accessibilityProps;
};

export const getBorderRadiusStyle = (borderRadius?: number): React.CSSProperties => {
  if (borderRadius === undefined) {
    return { borderRadius: "var(--border-radius)" };
  }
  return { borderRadius: `${borderRadius}px` };
};

export const getBorderRadiusClass = (borderRadius?: number): string => {
  return "rounded-[var(--border-radius)]";
};

export const createRadiusStyle = (borderRadius?: number, multiplier?: number): React.CSSProperties => {
  if (borderRadius === undefined) {
    return { borderRadius: "var(--border-radius)" };
  }
  
  if (borderRadius === 0) {
    return { borderRadius: "0px" };
  }
  
  const adjustedRadius = multiplier ? Math.round(borderRadius * multiplier) : borderRadius;
  return { borderRadius: `${adjustedRadius}px` };
};

export const getRadiusClasses = (borderRadius?: number, element?: string): string => {
  return getBorderRadiusClass(borderRadius);
};

export const getTextSizeClass = (size: ComponentSize, element: "button" | "input" | "badge" | "label" | "checkbox" = "label"): string => {  if (element === "badge") {
    switch (size) {
      case "xs": return "text-xs";
      case "sm": return "text-xs";
      case "md": return "text-sm";
      case "lg": return "text-base";
      case "xl": return "text-lg";
      default: return "text-sm";
    }
  }
  
  if (element === "input") {
    switch (size) {
      case "xs": return "text-xs";
      case "sm": return "text-sm";
      case "md": return "text-sm";
      case "lg": return "text-base";
      case "xl": return "text-base";
      default: return "text-sm";
    }
  }
    if (element === "button") {
    switch (size) {
      case "xs": return "text-xs";
      case "sm": return "text-sm";
      case "md": return "text-sm";
      case "lg": return "text-base";
      case "xl": return "text-base";
      default: return "text-sm";
    }
  }
  
  if (element === "checkbox") {
    switch (size) {
      case "xs": return "text-xs";
      case "sm": return "text-xs";
      case "md": return "text-sm";
      case "lg": return "text-base";
      case "xl": return "text-base";
      default: return "text-sm";
    }
  }
  
  switch (size) {
    case "xs": return "text-xs";
    case "sm": return "text-sm";
    case "md": return "text-sm";
    case "lg": return "text-base";
    case "xl": return "text-base";
    default: return "text-sm";
  }
};

// Toast styling utilities
export type ToastVariant = "default" | "success" | "error" | "loading";

export interface ToastStyleConfig {
  accentColor: string;
  borderRadius?: number;
}

export const getToastVariantStyles = (variant: ToastVariant, accentColor: string): React.CSSProperties => {
  switch (variant) {
    case "success":
      return {
        backgroundColor: "rgba(16, 185, 129, 0.3)",
        borderColor: "rgba(16, 185, 129, 0.8)",
        borderBottomColor: "#059669",
        color: "#d1fae5",
      };
    case "error":
      return {
        backgroundColor: "rgba(239, 68, 68, 0.3)",
        borderColor: "rgba(239, 68, 68, 0.8)",
        borderBottomColor: "#dc2626",
        color: "#fee2e2",
      };
    default:
      return {
        backgroundColor: `${accentColor}30`,
        borderColor: `${accentColor}80`,
        borderBottomColor: accentColor,
        color: "#ffffff",
      };
  }
};

export const getToastBaseStyles = (config: ToastStyleConfig): React.CSSProperties => {
  const radiusStyle = createRadiusStyle(config.borderRadius);
  const variantStyles = getToastVariantStyles("default", config.accentColor);

  return {
    borderWidth: "1px",
    borderBottomWidth: "2px",
    borderStyle: "solid",
    boxShadow: "none",
    padding: "12px 20px",
    backdropFilter: "blur(8px)",
    WebkitBackdropFilter: "blur(8px)",
    minWidth: "300px",
    transition: "all 0.2s ease",
    fontWeight: "500",
    ...variantStyles,
    ...radiusStyle,
  };
};

export const TOAST_BASE_CLASSES = "font-smallcaps tracking-wider text-shadow-sm";
