"use client";

import type React from "react";
import { useRef, useState } from "react";
import { SURFACE, KANTE_SOFT, hexAlpha } from "../../../../ui/design-system";
import { SALAMI_COLORS } from "../../../../../themes/salami/salami-theme";
import { PixelIcon } from "../../../../../themes/salami/PixelIcon";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { useThemeStore } from "../../../../../store/useThemeStore";
import type { LocalContentItem } from "../../../../../hooks/useLocalContentManager";
import type { UnifiedVersion } from "../../../../../types/unified";
import { formatFileSize } from "../../../../../utils/format-file-size";
import { Tooltip } from "../../../../ui/Tooltip";
import { ModUpdateText } from "../../../../ui/ModUpdateText";
import { ToggleSwitch } from "../../../../ui/ToggleSwitch";
import { CheckboxV2 } from "../../../../ui/CheckboxV2";
import { ThemedDropdown, ThemedDropdownItem, ThemedDropdownDivider } from "../../shared/ThemedDropdown";
import { VersionSelectDropdown } from "../../shared/VersionSelectDropdown";

export interface ContentTileProps {
  item: LocalContentItem;
  displayName: string;
  iconUrl: string | null;
  platformLabel: string;
  busy: boolean;
  onToggle: () => void;
  onDelete: () => void;
  onOpenFolder: () => void;
  onNameClick?: () => void;
  menuOpen: boolean;
  onMenuToggle: (open: boolean) => void;
  // Selection
  selectMode: boolean;
  isSelected: boolean;
  onToggleSelection: () => void;
  // Update-Checks pausieren
  onToggleUpdateChecks?: () => void;
  // Quick-Update: immer sichtbar wenn Update da, evtl. disabled
  onQuickUpdate?: () => void;
  quickUpdateDisabled?: boolean;
  quickUpdateTooltip?: React.ReactNode;
  isQuickUpdating?: boolean;
  // Version switching
  versionDropdownOpen: boolean;
  availableVersions: UnifiedVersion[] | null;
  isLoadingVersions: boolean;
  versionError: string | null;
  onVersionClick: () => void;
  onSwitchVersion: (v: UnifiedVersion) => void;
  updateAvailable: UnifiedVersion | null;
  isSwitchingVersion?: boolean;
}

export function ContentTile({
  item,
  displayName,
  iconUrl,
  busy,
  onToggle,
  onDelete,
  onOpenFolder,
  onNameClick,
  menuOpen,
  onMenuToggle,
  versionDropdownOpen,
  availableVersions,
  isLoadingVersions,
  versionError,
  onVersionClick,
  onSwitchVersion,
  updateAvailable,
  isSwitchingVersion,
  selectMode,
  isSelected,
  onToggleSelection,
  onToggleUpdateChecks,
  onQuickUpdate,
  quickUpdateDisabled,
  quickUpdateTooltip,
  isQuickUpdating,
}: ContentTileProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((s) => s.accentColor);
  const enabled = !item.is_disabled;
  const versionText =
    (item.modrinth_info as any)?.version_number ||
    (item.curseforge_info as any)?.file_name ||
    item.fallback_version ||
    "";

  const isSwitchable =
    !!(item.modrinth_info?.project_id || item.curseforge_info?.project_id);

  const currentVersionId =
    (item.modrinth_info as any)?.version_id ||
    (item.modrinth_info as any)?.id ||
    (item.curseforge_info as any)?.file_id ||
    null;

  // Portal-mode triggers: the tile wrapper carries `opacity-55` when the
  // content is disabled. CSS opacity cascades to children, so without a
  // portal the dropdown panels would also fade and become unreadable.
  // Passing these refs into ThemedDropdown renders the panels in
  // `document.body`, outside the faded subtree.
  const versionButtonRef = useRef<HTMLButtonElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  // Salami: Karte im Feinkost-Stil – warme Fläche mit Haarlinie, beim Hover Akzent-Rahmen + leichter Schein
  const [hovered, setHovered] = useState(false);
  const lifted = hovered && !isSelected;
  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        backgroundColor: isSelected ? hexAlpha(accentColor.value, 0.12) : lifted ? SURFACE.hover : SURFACE.base,
        borderColor: isSelected || lifted ? hexAlpha(accentColor.value, 0.55) : SURFACE.line,
        borderRadius: "var(--border-radius)",
        boxShadow: lifted ? `0 10px 24px -14px ${hexAlpha(accentColor.value, 0.6)}` : undefined,
      }}
      className={`group relative flex items-center gap-4 p-3 border transition-[background-color,border-color,box-shadow,opacity] duration-150 ${
        !enabled && !isSelected ? "opacity-55" : ""
      }`}
    >
      {/* Selection checkbox (on-hover, or permanent when selectMode aktiv) */}
      <div className={`flex-shrink-0 transition-opacity ${selectMode || isSelected ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}>
        <CheckboxV2
          size="sm"
          checked={isSelected}
          onChange={() => onToggleSelection()}
          tooltip={isSelected ? t("profiles.v3.tile.deselect") : t("profiles.v3.tile.select")}
        />
      </div>

      {/* Icon + optionales NoRisk-Warn-Icon */}
      <div className="relative w-14 h-14 flex-shrink-0">
        <div
          className="w-full h-full flex items-center justify-center overflow-hidden border-2"
          style={{ backgroundColor: SURFACE.solid, borderColor: SURFACE.lineStrong, borderRadius: "calc(var(--border-radius) * 0.8)" }}
        >
          {iconUrl ? (
            <img src={iconUrl} alt="" className="w-full h-full object-cover" onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }} />
          ) : (
            <PixelIcon name="mods" className="w-6 h-6 text-white/35" />
          )}
        </div>
      </div>

      {/* Identity */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          {onNameClick ? (
            <button
              onClick={(e) => { e.stopPropagation(); onNameClick(); }}
              className={`text-sm text-white font-minecraft truncate normal-case underline-offset-2 text-left ${!enabled ? "line-through" : "hover:underline decoration-white/40"}`}
              style={!enabled ? { textDecorationColor: accentColor.value, textDecorationThickness: "2px" } : undefined}
              title={displayName}
            >
              {displayName}
            </button>
          ) : (
            <div
              className={`text-sm text-white font-minecraft truncate normal-case ${!enabled ? "line-through" : ""}`}
              style={!enabled ? { textDecorationColor: accentColor.value, textDecorationThickness: "2px" } : undefined}
              title={displayName}
            >
              {displayName}
            </div>
          )}
          {item.updates_enabled === false && (
            <Tooltip content={t("profiles.v3.tile.updateChecksPaused")}>
              <Icon icon="solar:volume-cross-bold" className="w-3 h-3 text-white/30 flex-shrink-0" />
            </Tooltip>
          )}
          {item.modpack_origin && (
            <Tooltip content={t("profiles.v3.tile.fromModpackTooltip", { source: item.modpack_origin.split(":")[0] })}>
              <Icon
                icon="solar:box-bold"
                className="w-3 h-3 text-violet-300/70 flex-shrink-0"
              />
            </Tooltip>
          )}
        </div>
        <div className="flex items-center gap-2 mt-1 text-xs font-minecraft">
          <div className="relative min-w-0">
            <button
              ref={versionButtonRef}
              onClick={(e) => { e.stopPropagation(); if (isSwitchable && !isSwitchingVersion) onVersionClick(); }}
              disabled={!isSwitchable || isSwitchingVersion}
              /* Fixed h-5 verhindert dass die Chip 1-2px hoeher wird sobald ein
                 Update-Dot/Switch-Spinner reinkommt (inline-block Baseline-Quirk
                 vom Tooltip-Wrapper). Sonst shifted Identity vertikal. */
              /* Salami: Version als kleines Feinkost-Etikett (DeliLabel sm) */
              className={`${!versionText && !isSwitchable && !isSwitchingVersion ? "hidden" : "inline-flex"} items-center gap-1.5 h-5 max-w-full truncate pl-1.5 pr-2 leading-none transition-[filter] ${
                isSwitchingVersion
                  ? "cursor-wait"
                  : isSwitchable
                    ? "cursor-pointer hover:brightness-105"
                    : "cursor-default opacity-75"
              }`}
              style={{
                background: SALAMI_COLORS.fat,
                color: SALAMI_COLORS.pepper,
                borderRadius: "4px 10px 10px 4px",
                boxShadow: "0 2px 0 rgba(0,0,0,0.35)",
              }}
            >
              {!isSwitchingVersion && !updateAvailable && (
                <span className="h-1.5 w-1.5 flex-shrink-0" style={{ borderRadius: 999, background: SALAMI_COLORS.casing }} />
              )}
              {isSwitchingVersion && (
                <Icon icon="svg-spinners:ring-resize" className="w-3 h-3 flex-shrink-0" />
              )}
              {!isSwitchingVersion && updateAvailable && (() => {
                const isFromModPack = !!item.modpack_origin;
                const active = isFromModPack
                  ? item.updates_enabled === true
                  : item.updates_enabled !== false;
                // Kein Tooltip-Wrapper hier: der Quick-Update-Button rechts
                // zeigt ohnehin die ModUpdateText-Tooltip beim Hovern. Der Dot
                // bleibt rein visueller Indicator — vermeidet den inline-block
                // Baseline-Jitter der sonst die Chip 1px verschiebt.
                return (
                  <span
                    className={`inline-block w-1.5 h-1.5 rounded-full flex-shrink-0 ${
                      active ? "bg-amber-500 animate-pulse" : "bg-black/25"
                    }`}
                  />
                );
              })()}
              <span className="truncate">{isSwitchingVersion ? t("profiles.v3.tile.switching") : (versionText || "—")}</span>
              {isSwitchable && !isSwitchingVersion && <Icon icon="solar:alt-arrow-down-linear" className="w-3 h-3 flex-shrink-0 opacity-60" />}
            </button>

            <VersionSelectDropdown
              open={versionDropdownOpen}
              onClose={() => onVersionClick()}
              triggerRef={versionButtonRef}
              versions={availableVersions}
              loading={isLoadingVersions}
              error={versionError}
              currentVersionId={currentVersionId}
              onSelect={onSwitchVersion}
            />
          </div>

          {item.file_size > 0 && (
            <span className="text-white/40 flex-shrink-0">{formatFileSize(item.file_size)}</span>
          )}
        </div>
      </div>

      {/* Quick-Update */}
      {onQuickUpdate && (
        <Tooltip content={isQuickUpdating ? t("profiles.v3.tile.updating") : (quickUpdateTooltip ?? t("profiles.v3.tile.updateToLatest"))}>
          <button
            onClick={(e) => { e.stopPropagation(); if (!quickUpdateDisabled && !isQuickUpdating) onQuickUpdate(); }}
            disabled={quickUpdateDisabled || isQuickUpdating}
            style={{ borderRadius: "calc(var(--border-radius) * 0.8)", boxShadow: KANTE_SOFT }}
            className={`h-8 w-8 border flex items-center justify-center flex-shrink-0 transition-[background-color,color,transform] active:translate-y-px ${
              isQuickUpdating
                ? "bg-amber-400/20 border-amber-400/40 text-amber-200 cursor-wait"
                : quickUpdateDisabled
                  ? "bg-white/5 border-white/10 text-white/30 cursor-not-allowed grayscale"
                  : "bg-amber-400/15 hover:bg-amber-400/30 border-amber-400/30 text-amber-200 hover:text-amber-100"
            }`}
          >
            <Icon
              icon={isQuickUpdating ? "solar:refresh-bold" : "solar:arrow-up-bold"}
              className={`w-4 h-4 ${isQuickUpdating ? "animate-spin" : ""}`}
            />
          </button>
        </Tooltip>
      )}

      {/* Enable/Disable — V2 ToggleSwitch (accent-getönt, Minecraft-Border) */}
      <div
        className="flex-shrink-0"
        title={enabled ? t("profiles.v3.tile.disable") : t("profiles.v3.tile.enable")}
      >
        <ToggleSwitch
          checked={enabled}
          onChange={() => onToggle()}
          disabled={busy}
          size="sm"
        />
      </div>

      {/* Menu */}
      <div className="relative flex-shrink-0">
        <button
          ref={menuButtonRef}
          onClick={(e) => { e.stopPropagation(); onMenuToggle(!menuOpen); }}
          className="p-1.5 rounded text-white/40 hover:text-white hover:bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"
        >
          <Icon icon="solar:menu-dots-bold" className="w-4 h-4" />
        </button>
        <ThemedDropdown open={menuOpen} onClose={() => onMenuToggle(false)} width="w-52" triggerRef={menuButtonRef}>
          <ThemedDropdownItem
            icon="solar:folder-linear"
            onClick={() => { onOpenFolder(); onMenuToggle(false); }}
          >
            {t("profiles.v3.tile.openFolder")}
          </ThemedDropdownItem>
          {onToggleUpdateChecks && (
            <>
              <ThemedDropdownDivider />
              <ThemedDropdownItem
                icon={(item.updates_enabled ?? true) ? "solar:volume-cross-linear" : "solar:volume-loud-linear"}
                onClick={() => { onToggleUpdateChecks(); onMenuToggle(false); }}
              >
                {(item.updates_enabled ?? true) ? t("profiles.v3.tile.pauseUpdates") : t("profiles.v3.tile.resumeUpdates")}
              </ThemedDropdownItem>
            </>
          )}
          {!item.norisk_info && (
            <>
              <ThemedDropdownDivider />
              <ThemedDropdownItem
                icon="solar:trash-bin-trash-linear"
                tone="danger"
                onClick={() => { onDelete(); onMenuToggle(false); }}
              >
                {t("profiles.v3.tile.delete")}
              </ThemedDropdownItem>
            </>
          )}
        </ThemedDropdown>
      </div>
    </div>
  );
}
