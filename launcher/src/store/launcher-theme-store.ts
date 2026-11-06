import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AccentColor } from "./useThemeStore";
import { SALAMI_ACCENT, SALAMI_THEME_ID } from "../themes/salami/salami-theme";

export interface LauncherTheme {
  id: string;
  name: string;
  accentColor: AccentColor;
  backgroundImage?: string; // Path to background image (only for play screen)
}

export const LAUNCHER_THEMES: Record<string, LauncherTheme> = {
  // Salami-Modus (src/themes/salami)
  [SALAMI_THEME_ID]: {
    id: SALAMI_THEME_ID,
    name: "Salami",
    accentColor: SALAMI_ACCENT,
    backgroundImage: "/themes/salami_theme.jpg",
  },
  christmas_theme: {
    id: "christmas_theme",
    name: "Christmas Theme",
    accentColor: {
      name: "Christmas Blue",
      value: "#4A90D9",
      hoverValue: "#3A7BC8",
      light: "#6BA3E3",
      dark: "#2E5A8A",
      shadowValue: "rgba(74, 144, 217, 0.5)",
      isCustom: true,
    },
    backgroundImage: "/themes/christmas_theme.png",
  },
};

interface LauncherThemeState {
  // Currently selected theme (null means no theme, use custom accent color)
  selectedThemeId: string | null;
  // Original accent color before theme was applied (to restore when deselecting)
  originalAccentColor: AccentColor | null;
  // Actions
  selectTheme: (themeId: string | null) => void;
  setOriginalAccentColor: (color: AccentColor | null) => void;
  getSelectedTheme: () => LauncherTheme | null;
}

export const useLauncherThemeStore = create<LauncherThemeState>()(
  persist(
    (set, get) => ({
      selectedThemeId: null,
      originalAccentColor: null,

      selectTheme: (themeId: string | null) => {
        set({ selectedThemeId: themeId });
      },

      setOriginalAccentColor: (color: AccentColor | null) => {
        set({ originalAccentColor: color });
      },

      getSelectedTheme: () => {
        const { selectedThemeId } = get();
        if (!selectedThemeId) return null;
        return LAUNCHER_THEMES[selectedThemeId] || null;
      },
    }),
    {
      name: "launcher-theme-storage",
    }
  )
);

