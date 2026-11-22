import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useThemeStore } from "../../store/useThemeStore";
import { DEFAULT_LOOK, type LookId } from "../../themes/looks/types";
import { LookPicker } from "./LookPicker";

/**
 * Auswahl beim allerersten Öffnen: solange noch kein Look gewählt ist, liegt dieser Bildschirm
 * über allem (auch über der Anmeldung). Später lässt sich der Look in den Einstellungen wechseln.
 */
export function LookChooser() {
  const { t } = useTranslation();
  const chosen = useThemeStore((state) => state.look);
  const setLook = useThemeStore((state) => state.setLook);
  const accent = useThemeStore((state) => state.accentColor.value);
  const [selected, setSelected] = useState<LookId>(DEFAULT_LOOK);

  if (chosen) return null;

  return (
    <div
      className="fixed inset-0 z-[950] flex flex-col items-center justify-center overflow-y-auto px-10 py-8"
      style={{ backgroundColor: "#120d0b", fontFamily: '"Fredoka", sans-serif' }}
      data-tauri-drag-region
    >
      {/* Das Fenster hat keinen eigenen Rahmen – hier muss man es trotzdem verkleinern und schließen können */}
      <div className="absolute right-3 top-3 flex gap-1">
        <button
          type="button"
          aria-label="Minimize"
          onClick={() => void getCurrentWindow().minimize()}
          className="flex h-9 w-9 items-center justify-center text-white/60 transition-colors hover:bg-white/10 hover:text-white"
          style={{ borderRadius: 8 }}
        >
          <Icon icon="solar:minus-circle-linear" className="h-5 w-5" />
        </button>
        <button
          type="button"
          aria-label="Close"
          onClick={() => void getCurrentWindow().close()}
          className="flex h-9 w-9 items-center justify-center text-white/60 transition-colors hover:bg-white/10 hover:text-white"
          style={{ borderRadius: 8 }}
        >
          <Icon icon="solar:close-circle-linear" className="h-5 w-5" />
        </button>
      </div>

      <div className="w-full max-w-[1100px]">
        <h1 className="text-center text-4xl text-white" style={{ fontFamily: '"Fredoka", sans-serif', fontWeight: 600 }}>
          {t("salami.look.title")}
        </h1>
        <p className="mt-2 text-center text-base text-white/60">{t("salami.look.subtitle")}</p>

        <div className="mt-8">
          <LookPicker value={selected} onSelect={setSelected} markLabel={t("salami.look.selected")} />
        </div>

        <div className="mt-8 flex justify-center">
          <button
            type="button"
            onClick={() => setLook(selected)}
            className="h-14 px-10 text-xl text-white transition-[filter,transform] duration-150 hover:brightness-110 active:translate-y-px"
            style={{
              backgroundColor: accent,
              borderRadius: 12,
              boxShadow: "0 4px 0 rgba(0,0,0,0.45)",
              fontFamily: '"Fredoka", sans-serif',
              fontWeight: 600,
            }}
          >
            {t("salami.look.choose")}
          </button>
        </div>
      </div>
    </div>
  );
}
