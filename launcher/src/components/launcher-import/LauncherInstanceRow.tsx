"use client";

import { useTranslation } from "react-i18next";
import { convertFileSrc } from "@tauri-apps/api/core";

import { CheckboxV2 } from "../ui/CheckboxV2";
import { Tooltip } from "../ui/Tooltip";
import { useThemeStore } from "../../store/useThemeStore";
import { FK_CARD_CLASS, fkCardStyle, fkThumbStyle } from "../profiles/v3/shared/feinkost";
import { loaderIconSrc, loaderLabel } from "../../lib/loader-icons";
import { formatRelativeTime } from "../../utils/format-relative-time";
import type { ExternalInstanceRef } from "../../types/launcherImport";

export interface LauncherInstanceRowProps {
  instance: ExternalInstanceRef;
  selected: boolean;
  importedProfileId?: string;
  onToggle: () => void;
  onOpenProfile: (profileId: string) => void;
}

export function LauncherInstanceRow({
  instance,
  selected,
  importedProfileId,
  onToggle,
  onOpenProfile,
}: LauncherInstanceRowProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);

  const unsupported = instance.unsupported
    ? t(`profiles.launcherImport.card.unsupported_${instance.unsupported}`)
    : null;
  const disabled = Boolean(unsupported) || Boolean(importedProfileId);

  const version = instance.gameVersion ?? t("profiles.launcherImport.card.unknown_version");
  const subtitle = [
    instance.name.trim() !== version ? version : null,
    loaderLabel(instance.loader),
    instance.modCount != null
      ? t("profiles.launcherImport.card.mod_count", { count: instance.modCount })
      : null,
    instance.lastPlayed
      ? t("profiles.launcherImport.card.last_played", {
          time: formatRelativeTime(instance.lastPlayed),
        })
      : t("profiles.launcherImport.card.never_played"),
  ]
    .filter(Boolean)
    .join("  ·  ");

  const row = (
    <div
      onClick={disabled ? undefined : onToggle}
      style={fkCardStyle(accentColor.value, selected)}
      className={`flex items-center gap-3 px-3 py-2 ${FK_CARD_CLASS} ${disabled ? "opacity-50" : "cursor-pointer"}`}
    >
      <div onClick={(event) => event.stopPropagation()} className="flex-shrink-0">
        <CheckboxV2 size="sm" checked={selected} disabled={disabled} onChange={onToggle} />
      </div>

      <div className="relative h-9 w-9 flex-shrink-0">
        <div className="flex h-full w-full items-center justify-center overflow-hidden border-2" style={fkThumbStyle}>
          <img
            src={instance.iconPath ? convertFileSrc(instance.iconPath) : loaderIconSrc(instance.loader)}
            alt=""
            className="h-full w-full object-cover"
            onError={(event) => {
              event.currentTarget.src = loaderIconSrc(instance.loader);
            }}
          />
        </div>
        {instance.iconPath && (
          <img
            src={loaderIconSrc(instance.loader)}
            alt=""
            className="absolute -bottom-1 -right-1 h-4 w-4 rounded-sm bg-black/80 p-px ring-1 ring-black/60"
          />
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <span
          className={`truncate font-minecraft text-sm normal-case text-white ${
            unsupported ? "line-through" : ""
          }`}
          style={unsupported ? { textDecorationColor: accentColor.value } : undefined}
        >
          {instance.name}
        </span>
        <span className="truncate font-minecraft text-xs text-white/50">{subtitle}</span>
      </div>

      {importedProfileId && (
        <button
          onClick={(event) => {
            event.stopPropagation();
            onOpenProfile(importedProfileId);
          }}
          className="flex-shrink-0 px-2 py-1 font-minecraft text-xs transition-opacity hover:opacity-80"
          style={{ color: accentColor.value }}
        >
          {t("profiles.launcherImport.card.open_profile")}
        </button>
      )}
    </div>
  );

  if (unsupported) {
    return (
      <Tooltip content={unsupported} position="top" wrapperClassName="w-full">
        {row}
      </Tooltip>
    );
  }

  return row;
}
