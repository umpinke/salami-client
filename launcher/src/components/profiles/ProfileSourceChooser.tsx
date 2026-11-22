"use client";

import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";

import { useThemeStore } from "../../store/useThemeStore";
import { FK_CARD_CLASS, fkCardStyle, fkPrimaryStyle } from "./v3/shared/feinkost";
import { SALAMI_COLORS } from "../../themes/salami/salami-theme";

export type ProfileSource = "blank" | "modpack" | "file" | "launcher";

interface ProfileSourceChooserProps {
  onChoose: (source: ProfileSource) => void;
  isBusy?: boolean;
}

const IMPORT_SOURCES: { id: Exclude<ProfileSource, "blank">; icon: string }[] = [
  { id: "modpack", icon: "solar:box-bold" },
  { id: "file", icon: "solar:file-bold" },
  { id: "launcher", icon: "solar:layers-bold" },
];

export function ProfileSourceChooser({ onChoose, isBusy = false }: ProfileSourceChooserProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);

  return (
    <div className="space-y-4 px-8 pb-6 pt-5">
      <button
        onClick={() => onChoose("blank")}
        disabled={isBusy}
        className={`flex w-full items-center gap-5 px-6 py-5 text-left hover:-translate-y-0.5 disabled:opacity-50 ${FK_CARD_CLASS}`}
        style={fkCardStyle(accentColor.value, false, true)}
      >
        {/* Salami: Hauptweg als volle Akzentfläche mit harter Unterkante */}
        <div
          className="flex h-14 w-14 flex-shrink-0 items-center justify-center"
          style={{ ...fkPrimaryStyle(accentColor.value), borderRadius: "calc(var(--border-radius) * 0.8)" }}
        >
          <Icon icon="solar:widget-add-bold" className="h-7 w-7" style={{ color: SALAMI_COLORS.fat }} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-minecraft text-xl normal-case text-white">
            {t("profiles.import.source_blank_title")}
          </div>
          <div className="mt-1 font-minecraft text-sm leading-relaxed text-white/55">
            {t("profiles.import.source_blank_description")}
          </div>
        </div>
        <Icon icon="solar:arrow-right-bold" className="h-5 w-5 flex-shrink-0" style={{ color: accentColor.value }} />
      </button>

      <div className="flex items-center gap-3">
        <div className="h-px flex-1 bg-white/10" />
        <span className="font-minecraft text-xs text-white/45">
          {t("profiles.import.source_divider")}
        </span>
        <div className="h-px flex-1 bg-white/10" />
      </div>

      <div className="space-y-2">
        {IMPORT_SOURCES.map((source) => (
          <button
            key={source.id}
            onClick={() => onChoose(source.id)}
            disabled={isBusy}
            className={`group flex w-full items-center gap-4 px-4 py-3 text-left disabled:opacity-50 ${FK_CARD_CLASS}`}
            style={fkCardStyle(accentColor.value)}
          >
            <Icon
              icon={source.icon}
              className="h-5 w-5 flex-shrink-0 text-white/40 transition-colors group-hover:text-white"
            />
            <div className="min-w-0 flex-1">
              <div className="font-minecraft text-sm text-white/85">
                {t(`profiles.import.source_${source.id}_title`)}
              </div>
              <div className="truncate font-minecraft text-xs text-white/40">
                {t(`profiles.import.source_${source.id}_description`)}
              </div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
