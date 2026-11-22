import { useRef } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { ContentTileProps } from "../../../components/profiles/v3/tabs/local-content/ContentTile";
import { CheckboxV2 } from "../../../components/ui/CheckboxV2";
import { Tooltip } from "../../../components/ui/Tooltip";
import {
  ThemedDropdown,
  ThemedDropdownDivider,
  ThemedDropdownItem,
} from "../../../components/profiles/v3/shared/ThemedDropdown";
import { VersionSelectDropdown } from "../../../components/profiles/v3/shared/VersionSelectDropdown";

/**
 * Eine Zeile der „Zutatenliste“: runder Stempel (an/aus), Name, Punktlinie bis zur Version wie auf
 * einer Speisekarte. Ausgeschaltete Zutaten sind rot durchgestrichen; „neu“ heißt: Update da.
 * Auswählen und das Menü erscheinen beim Überfahren.
 */
export function ThekeContentItem({
  item,
  displayName,
  busy,
  onToggle,
  onDelete,
  onOpenFolder,
  onNameClick,
  menuOpen,
  onMenuToggle,
  selectMode,
  isSelected,
  onToggleSelection,
  onToggleUpdateChecks,
  onQuickUpdate,
  quickUpdateDisabled,
  quickUpdateTooltip,
  isQuickUpdating,
  versionDropdownOpen,
  availableVersions,
  isLoadingVersions,
  versionError,
  onVersionClick,
  onSwitchVersion,
  isSwitchingVersion,
}: ContentTileProps) {
  const { t } = useTranslation();
  const versionButtonRef = useRef<HTMLButtonElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const enabled = !item.is_disabled;
  const versionText =
    (item.modrinth_info as { version_number?: string } | undefined)?.version_number ||
    (item.curseforge_info as { file_name?: string } | undefined)?.file_name ||
    item.fallback_version ||
    "";
  const isSwitchable = !!(item.modrinth_info?.project_id || item.curseforge_info?.project_id);
  const currentVersionId =
    (item.modrinth_info as { version_id?: string; id?: string } | undefined)?.version_id ||
    (item.modrinth_info as { id?: string } | undefined)?.id ||
    (item.curseforge_info as { file_id?: string } | undefined)?.file_id ||
    null;

  return (
    <div
      className="theke-line group"
      data-off={!enabled || undefined}
      data-selected={isSelected || undefined}
      data-select-mode={selectMode || undefined}
    >
      <button
        type="button"
        role="switch"
        aria-checked={enabled}
        aria-label={enabled ? t("theke.item.disable") : t("theke.item.enable")}
        title={enabled ? t("theke.item.disable") : t("theke.item.enable")}
        className="theke-tick"
        disabled={busy}
        onClick={onToggle}
      >
        {busy ? (
          <Icon icon="svg-spinners:ring-resize" className="h-3 w-3" />
        ) : (
          enabled && <Icon icon="solar:check-read-linear" className="h-3.5 w-3.5" />
        )}
      </button>

      {onNameClick ? (
        <button type="button" className="theke-line-name" title={displayName} onClick={onNameClick}>
          {displayName}
        </button>
      ) : (
        <span className="theke-line-name" title={displayName}>
          {displayName}
        </span>
      )}
      {item.updates_enabled === false && (
        <Tooltip content={t("profiles.v3.tile.updateChecksPaused")}>
          <Icon icon="solar:volume-cross-bold" className="theke-line-mark h-3 w-3" />
        </Tooltip>
      )}
      {item.modpack_origin && (
        <Tooltip content={t("profiles.v3.tile.fromModpackTooltip", { source: item.modpack_origin.split(":")[0] })}>
          <Icon icon="solar:box-bold" className="theke-line-mark h-3 w-3" />
        </Tooltip>
      )}

      <span className="theke-leader" aria-hidden />

      <span className="relative min-w-0 flex-shrink">
        <button
          ref={versionButtonRef}
          type="button"
          className="theke-line-version"
          disabled={!isSwitchable || isSwitchingVersion}
          title={versionText}
          onClick={(e) => {
            e.stopPropagation();
            if (isSwitchable && !isSwitchingVersion) onVersionClick();
          }}
        >
          {isSwitchingVersion ? t("profiles.v3.tile.switching") : versionText || "—"}
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
      </span>

      {onQuickUpdate && (
        <Tooltip content={isQuickUpdating ? t("profiles.v3.tile.updating") : (quickUpdateTooltip ?? t("profiles.v3.tile.updateToLatest"))}>
          <button
            type="button"
            className="theke-line-update"
            disabled={quickUpdateDisabled || isQuickUpdating}
            onClick={(e) => {
              e.stopPropagation();
              if (!quickUpdateDisabled && !isQuickUpdating) onQuickUpdate();
            }}
          >
            {isQuickUpdating ? (
              <Icon icon="solar:refresh-bold" className="h-3 w-3 animate-spin" />
            ) : (
              <>
                <Icon icon="solar:arrow-up-bold" className="h-3 w-3" />
                {t("theke.item.update")}
              </>
            )}
          </button>
        </Tooltip>
      )}

      <span className="theke-line-tools">
        <CheckboxV2
          size="sm"
          checked={isSelected}
          onChange={() => onToggleSelection()}
          tooltip={isSelected ? t("profiles.v3.tile.deselect") : t("profiles.v3.tile.select")}
        />
        <button
          ref={menuButtonRef}
          type="button"
          className="theke-line-menu"
          aria-label={t("profiles.moreOptions")}
          onClick={(e) => {
            e.stopPropagation();
            onMenuToggle(!menuOpen);
          }}
        >
          <Icon icon="solar:menu-dots-bold" className="h-4 w-4" />
        </button>
        <ThemedDropdown open={menuOpen} onClose={() => onMenuToggle(false)} width="w-52" triggerRef={menuButtonRef}>
          <ThemedDropdownItem
            icon="solar:folder-linear"
            onClick={() => {
              onOpenFolder();
              onMenuToggle(false);
            }}
          >
            {t("profiles.v3.tile.openFolder")}
          </ThemedDropdownItem>
          {onToggleUpdateChecks && (
            <>
              <ThemedDropdownDivider />
              <ThemedDropdownItem
                icon={(item.updates_enabled ?? true) ? "solar:volume-cross-linear" : "solar:volume-loud-linear"}
                onClick={() => {
                  onToggleUpdateChecks();
                  onMenuToggle(false);
                }}
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
                onClick={() => {
                  onDelete();
                  onMenuToggle(false);
                }}
              >
                {t("profiles.v3.tile.delete")}
              </ThemedDropdownItem>
            </>
          )}
        </ThemedDropdown>
      </span>
    </div>
  );
}
