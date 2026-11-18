import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { SalamiCapesTab } from "../salami-capes/SalamiCapesTab";
import { EmotesSection, HatsSection, MiniMeSection } from "./CosmeticsSections";
import "../salami-capes/i18n";

type CosmeticsSection = "capes" | "hats" | "emotes" | "minime";

/** Bereiche der Shop-Seite, dieselben wie im Cosmetics-Menü des Spiels. */
const SECTIONS: { id: CosmeticsSection; icon: string; labelKey: string }[] = [
  { id: "capes", icon: "solar:hanger-2-bold", labelKey: "salami.cosmetics.tab.capes" },
  { id: "hats", icon: "solar:crown-bold", labelKey: "salami.cosmetics.tab.hats" },
  { id: "emotes", icon: "solar:hand-shake-bold", labelKey: "salami.cosmetics.tab.emotes" },
  { id: "minime", icon: "solar:users-group-rounded-bold", labelKey: "salami.cosmetics.tab.minime" },
];

/**
 * Shop (früher „Cosmetics“): Reiter oben, darunter der gewählte Bereich. Capes legt man hier an und
 * wählt sie aus; Hüte, Emotes und Mini Me zeigt der Shop zum Ansehen – gewählt werden sie im Spiel.
 */
export function CosmeticsTab() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor);
  const [section, setSection] = useState<CosmeticsSection>("capes");

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center gap-2 px-6 pt-5" role="tablist">
        {SECTIONS.map((entry) => {
          const active = entry.id === section;
          return (
            <button
              key={entry.id}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setSection(entry.id)}
              className={cn(
                "flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold text-white transition-colors",
                active ? "hover:brightness-110" : "bg-white/10 hover:bg-white/15",
              )}
              style={active ? { backgroundColor: accent.value } : undefined}
            >
              <Icon icon={entry.icon} className="h-5 w-5" />
              {t(entry.labelKey)}
            </button>
          );
        })}
      </div>
      <div className="min-h-0 flex-1">
        {section === "capes" && <SalamiCapesTab />}
        {section === "hats" && <HatsSection />}
        {section === "emotes" && <EmotesSection />}
        {section === "minime" && <MiniMeSection />}
      </div>
    </div>
  );
}
