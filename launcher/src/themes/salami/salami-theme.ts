import type { AccentColor } from "../../store/useThemeStore";

/** ID im launcher-theme-store (LAUNCHER_THEMES). */
export const SALAMI_THEME_ID = "salami_theme";

/** Salami-Palette – identisch mit dem In-Game-Theme (mod: theme/Theme.java). */
export const SALAMI_COLORS = {
  red: "#B23A3A",
  redDark: "#7A2425",
  redLight: "#D06060",
  fat: "#F3E3D3",
  pepper: "#2B1D1A",
  casing: "#7A4A35",
} as const;

export const SALAMI_ACCENT: AccentColor = {
  name: "Salami-Rot",
  value: SALAMI_COLORS.red,
  hoverValue: "#9A2F30",
  light: SALAMI_COLORS.redLight,
  dark: SALAMI_COLORS.redDark,
  shadowValue: "rgba(178, 58, 58, 0.5)",
  isCustom: true,
};
