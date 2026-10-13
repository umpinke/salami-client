import { create } from "zustand";
import { persist } from "zustand/middleware";
import { DEFAULT_FONT_ID, FONT_PRESETS } from "../config/fonts";

export const CUSTOM_FONT_ID = "custom";

interface FontState {
  fontId: string;
  customFamily: string;
  setFont: (id: string) => void;
  setCustomFamily: (name: string) => void;
  applyFontToDOM: () => void;
}

export const useFontStore = create<FontState>()(
  persist(
    (set, get) => ({
      fontId: DEFAULT_FONT_ID,
      customFamily: "",

      setFont: (id: string) => {
        const nextId = id === CUSTOM_FONT_ID || FONT_PRESETS[id] ? id : DEFAULT_FONT_ID;
        set({ fontId: nextId });
        get().applyFontToDOM();

        void import("../services/analytics-service").then(({ trackEvent }) => {
          trackEvent("font_changed", { font: nextId }).catch(console.error);
        });
      },

      setCustomFamily: (name: string) => {
        set({ customFamily: name });
        if (get().fontId === CUSTOM_FONT_ID) get().applyFontToDOM();
      },

      applyFontToDOM: () => {
        const { fontId, customFamily } = get();
        const root = document.documentElement.style;
        // Schriftgewichte/Abstände in globals.css hängen an der gewählten Schrift
        document.documentElement.dataset.font = fontId;

        if (fontId === CUSTOM_FONT_ID && customFamily.trim()) {
          const family = `"${customFamily.trim()}", sans-serif`;
          root.setProperty("--font-smallcaps", family);
          root.setProperty("--font-minecraft", family);
          return;
        }

        const preset = FONT_PRESETS[fontId] ?? FONT_PRESETS[DEFAULT_FONT_ID];
        root.setProperty("--font-smallcaps", preset.smallcaps);
        root.setProperty("--font-minecraft", preset.minecraft);
      },
    }),
    {
      name: "norisk-font-storage",
      version: 1,
      // Salami Client: einmalig von der Pixel-Schrift auf die Salami-Schrift wechseln
      migrate: (persisted, version) => {
        const state = persisted as Partial<FontState>;
        if (version < 1 && state.fontId === "minecraft") {
          return { ...state, fontId: DEFAULT_FONT_ID } as FontState;
        }
        return state as FontState;
      },
      onRehydrateStorage: () => (state) => {
        state?.applyFontToDOM();
      },
    },
  ),
);
