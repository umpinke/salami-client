import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { useThemeStore } from "../../store/useThemeStore";
import { LOOK_IDS, type LookId } from "../../themes/looks/types";
import { hexAlpha } from "../ui/design-system";

interface LookPickerProps {
  value: LookId | null;
  onSelect: (look: LookId) => void;
  /** "large": Auswahl beim ersten Start; "compact": in den Einstellungen. */
  size?: "large" | "compact";
  /** Beschriftung der Markierung; Standard „Aktiv" (Einstellungen). */
  markLabel?: string;
}

/**
 * Die drei Looks als Karten mit Vorschaubild. Bewusst neutral gestaltet (eigene feste Farben statt
 * der Look-Variablen), damit die Auswahl in jedem Look gleich aussieht.
 */
export function LookPicker({ value, onSelect, size = "large", markLabel }: LookPickerProps) {
  const { t } = useTranslation();
  const accent = useThemeStore((state) => state.accentColor.value);
  const large = size === "large";

  return (
    <div className={`look-picker grid grid-cols-3 ${large ? "gap-5" : "gap-3"}`}>
      {LOOK_IDS.map((id) => {
        const active = value === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => onSelect(id)}
            aria-pressed={active}
            className="group relative flex flex-col overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-150 hover:-translate-y-0.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/50"
            style={{
              borderRadius: 12,
              border: `2px solid ${active ? accent : "rgba(255,255,255,0.14)"}`,
              backgroundColor: "#1b1513",
              boxShadow: active ? `0 14px 34px -14px ${hexAlpha(accent, 0.8)}` : "0 10px 24px -16px rgba(0,0,0,0.8)",
            }}
          >
            <span className="relative block w-full overflow-hidden" style={{ aspectRatio: "710 / 422", backgroundColor: "#0f0b0a" }}>
              <img
                src={`/looks/${id}.jpg`}
                alt=""
                draggable={false}
                className="absolute inset-0 h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
              />
              {active && (
                <span
                  className="absolute right-2 top-2 flex items-center gap-1 px-2 py-1 text-xs text-white"
                  style={{ backgroundColor: accent, borderRadius: 999, fontFamily: '"Fredoka", sans-serif', fontWeight: 600 }}
                >
                  <Icon icon="solar:check-circle-bold" className="h-3.5 w-3.5" />
                  {markLabel ?? t("salami.look.current")}
                </span>
              )}
            </span>
            <span className={`flex flex-col ${large ? "gap-1 px-4 py-3.5" : "gap-0.5 px-3 py-2.5"}`}>
              <span
                className={`${large ? "text-xl" : "text-base"} text-white`}
                style={{ fontFamily: '"Fredoka", sans-serif', fontWeight: 600 }}
              >
                {t(`salami.look.${id}.name`)}
              </span>
              <span
                className={`${large ? "text-sm" : "text-xs"} leading-snug text-white/60`}
                style={{ fontFamily: '"Fredoka", sans-serif', fontWeight: 400 }}
              >
                {t(`salami.look.${id}.tagline`)}
              </span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
