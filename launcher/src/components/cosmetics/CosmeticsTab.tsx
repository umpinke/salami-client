import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { SalamiCapesTab } from "../salami-capes/SalamiCapesTab";
import "../salami-capes/i18n";

type CosmeticsSection = "capes";

/** Bereiche der Cosmetics-Seite – vorerst nur Capes, weitere kommen als eigener Reiter dazu. */
const SECTIONS: { id: CosmeticsSection; icon: string; labelKey: string }[] = [
  { id: "capes", icon: "solar:hanger-2-bold", labelKey: "salami.cosmetics.tab.capes" },
];

/** Cosmetics: Reiter oben, darunter der gewählte Bereich (Capes anlegen, hochladen, zeichnen). */
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
        <span className="ml-2 text-xs text-white/45">{t("salami.cosmetics.more_soon")}</span>
      </div>
      <div className="min-h-0 flex-1">{section === "capes" && <SalamiCapesTab />}</div>
    </div>
  );
}
