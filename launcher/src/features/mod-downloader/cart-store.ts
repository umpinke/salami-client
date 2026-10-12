import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { ModPlatform, UnifiedModSearchResult } from "../../types/unified";

/** Ein Projekt im Mod-Warenkorb (die passende Version wird erst beim Installieren gewählt). */
export interface CartItem {
  key: string;
  source: ModPlatform;
  projectId: string;
  title: string;
  iconUrl: string | null;
  projectType: string;
}

interface CartState {
  items: CartItem[];
  add: (item: CartItem) => void;
  remove: (key: string) => void;
  clear: () => void;
  toggle: (item: CartItem) => boolean;
}

export const cartKeyOf = (source: string, projectId: string) => `${source}:${projectId}`;

export function cartItemFrom(project: UnifiedModSearchResult): CartItem {
  return {
    key: cartKeyOf(project.source, project.project_id),
    source: project.source,
    projectId: project.project_id,
    title: project.title,
    iconUrl: project.icon_url,
    projectType: project.project_type ?? "mod",
  };
}

export const useInstallCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      add: (item) => set((s) => (s.items.some((i) => i.key === item.key) ? s : { items: [...s.items, item] })),
      remove: (key) => set((s) => ({ items: s.items.filter((i) => i.key !== key) })),
      clear: () => set({ items: [] }),
      /** @returns true, wenn das Projekt jetzt im Warenkorb liegt */
      toggle: (item) => {
        const present = get().items.some((i) => i.key === item.key);
        if (present) get().remove(item.key);
        else get().add(item);
        return !present;
      },
    }),
    { name: "salami-install-cart", storage: createJSONStorage(() => sessionStorage) },
  ),
);
