import { create } from "zustand";
import { persist } from "zustand/middleware";

/** Größe der Launcher-Oberfläche (1 = 100 %). */
export const UI_SCALE_MIN = 0.7;
export const UI_SCALE_MAX = 1.5;
export const UI_SCALE_STEP = 0.05;

export function clampUiScale(value: number): number {
  if (!Number.isFinite(value)) return 1;
  const stepped = Math.round(value / UI_SCALE_STEP) * UI_SCALE_STEP;
  return Math.min(UI_SCALE_MAX, Math.max(UI_SCALE_MIN, Number(stepped.toFixed(2))));
}

interface UiScaleState {
  scale: number;
  setScale: (scale: number) => void;
  zoomIn: () => void;
  zoomOut: () => void;
  reset: () => void;
}

export const useUiScaleStore = create<UiScaleState>()(
  persist(
    (set, get) => ({
      scale: 1,
      setScale: (scale) => set({ scale: clampUiScale(scale) }),
      zoomIn: () => set({ scale: clampUiScale(get().scale + UI_SCALE_STEP * 2) }),
      zoomOut: () => set({ scale: clampUiScale(get().scale - UI_SCALE_STEP * 2) }),
      reset: () => set({ scale: 1 }),
    }),
    {
      name: "salami-ui-scale",
      partialize: (state) => ({ scale: state.scale }),
    },
  ),
);
