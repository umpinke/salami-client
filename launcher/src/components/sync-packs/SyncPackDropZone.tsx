"use client";

import { useTranslation } from "react-i18next";

import { useThemeStore } from "../../store/useThemeStore";
import type { SyncPack, SyncTargetPreset } from "../../types/syncPacks";
import { SYNC_TARGET_PRESETS } from "../../types/syncPacks";
import { Tooltip } from "../ui/Tooltip";
import { FK_PRIMARY_BUTTON_CLASS, FK_SOFT_BUTTON_CLASS, fkPrimaryStyle } from "../profiles/v3/shared/feinkost";
import { SALAMI_COLORS } from "../../themes/salami/salami-theme";

export interface SyncPackDropZoneProps {
  pack: SyncPack;
  isDragOver: boolean;
  showBusy: boolean;
  canBrowse: boolean;
  onPickPaths: (directory: boolean) => void;
  onPickPreset: (preset: SyncTargetPreset) => void;
  onBrowseMods: () => void;
}

const ADD_BUTTON = `h-8 px-3 text-sm font-minecraft disabled:opacity-30 ${FK_SOFT_BUTTON_CLASS}`;

function missingPresets(pack: SyncPack): SyncTargetPreset[] {
  const taken = new Set(pack.targets.map((target) => target.path.toLowerCase()));
  return SYNC_TARGET_PRESETS.filter(
    (preset) =>
      preset.kindType !== "mods" && !taken.has(preset.path.toLowerCase()),
  );
}

export function SyncPackDropZone({
  pack,
  isDragOver,
  showBusy,
  canBrowse,
  onPickPaths,
  onPickPreset,
  onBrowseMods,
}: SyncPackDropZoneProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);
  const presets = missingPresets(pack);

  return (
    <div
      className="m-3 flex flex-col items-center justify-center gap-2.5 border-2 border-dashed py-4 text-center transition-colors"
      style={{
        borderColor: isDragOver ? accentColor.value : "rgba(255,255,255,0.12)",
        backgroundColor: isDragOver ? `${accentColor.value}12` : "transparent",
        borderRadius: "var(--border-radius)",
      }}
    >
      <span className="text-sm text-white/45">
        {isDragOver ? t("syncPacks.drop.overlay") : t("syncPacks.drop.hint")}
      </span>

      {!isDragOver && presets.length > 0 && (
        <div className="flex max-w-[560px] flex-wrap items-center justify-center gap-1.5">
          {presets.map((preset) => (
            <Tooltip
              key={preset.path}
              content={
                <span className="block max-w-[260px]">{t(preset.hint)}</span>
              }
              position="top"
            >
              <button
                onClick={() => onPickPreset(preset)}
                disabled={showBusy}
                className="inline-flex items-center gap-1.5 py-1 pl-1.5 pr-2 font-minecraft text-xs leading-none transition-[filter,transform] hover:brightness-105 active:translate-y-px disabled:opacity-30"
                style={{
                  background: SALAMI_COLORS.fat,
                  color: SALAMI_COLORS.pepper,
                  borderRadius: "4px 10px 10px 4px",
                  boxShadow: "0 2px 0 rgba(0,0,0,0.35)",
                }}
              >
                <span className="h-1.5 w-1.5 flex-shrink-0" style={{ borderRadius: 999, background: SALAMI_COLORS.casing }} />
                + {preset.path}
              </button>
            </Tooltip>
          ))}
        </div>
      )}

      {!isDragOver && (
        <div className="flex items-center gap-2">
          <button
            onClick={() => onPickPaths(true)}
            disabled={showBusy}
            className={ADD_BUTTON}
          >
            + {t("syncPacks.add.folder")}
          </button>
          <button
            onClick={() => onPickPaths(false)}
            disabled={showBusy}
            className={ADD_BUTTON}
          >
            + {t("syncPacks.add.file")}
          </button>
          <Tooltip
            content={
              canBrowse ? t("syncPacks.add.mods") : t("syncPacks.noProfile")
            }
            position="top"
          >
            <button
              onClick={onBrowseMods}
              disabled={showBusy || !canBrowse}
              className={`h-8 px-3 text-sm disabled:opacity-30 ${FK_PRIMARY_BUTTON_CLASS}`}
              style={fkPrimaryStyle(accentColor.value)}
            >
              + {t("syncPacks.add.mods")}
            </button>
          </Tooltip>
        </div>
      )}
    </div>
  );
}
