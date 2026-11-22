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
import { formatFileSize } from "../../../utils/format-file-size";

/**
 * Ein Gang auf der Karte: vorne eine Raute im feinen Rahmen (gefüllt = wird serviert, also an;
 * leer = aus), der Name in Serife, darunter die Version kursiv wie die Beschreibung eines Gerichts,
 * rechts die Dateigröße an der Stelle des Preises. Ausgeschaltete Gänge sind durchgestrichen.
 * Auswählen und das Menü erscheinen beim Überfahren.
 */
export function SalumeriaContentItem({
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
  const showVersion = !!versionText || isSwitchable || !!isSwitchingVersion;

  return (
    <div
      className="sal-dish"
      data-off={!enabled || undefined}
      data-selected={isSelected || undefined}
      data-select-mode={selectMode || undefined}
    >
      <button
        type="button"
        role="switch"
        aria-checked={enabled}
        aria-label={enabled ? t("salami.look.salumeria.item.disable") : t("salami.look.salumeria.item.enable")}
        title={enabled ? t("salami.look.salumeria.item.disable") : t("salami.look.salumeria.item.enable")}
        className="sal-dish__mark"
        disabled={busy}
        onClick={onToggle}
      >
        {busy ? <Icon icon="svg-spinners:ring-resize" className="h-3 w-3" /> : <i aria-hidden />}
      </button>

      <div className="sal-dish__body">
        <div className="sal-dish__head">
          {onNameClick ? (
            <button type="button" className="sal-dish__name" title={displayName} onClick={onNameClick}>
              {displayName}
            </button>
          ) : (
            <span className="sal-dish__name" title={displayName}>
              {displayName}
            </span>
          )}
          {item.updates_enabled === false && (
            <Tooltip content={t("profiles.v3.tile.updateChecksPaused")}>
              <Icon icon="solar:volume-cross-linear" className="sal-dish__mark-icon h-3 w-3" />
            </Tooltip>
          )}
          {item.modpack_origin && (
            <Tooltip content={t("profiles.v3.tile.fromModpackTooltip", { source: item.modpack_origin.split(":")[0] })}>
              <Icon icon="solar:box-linear" className="sal-dish__mark-icon h-3 w-3" />
            </Tooltip>
          )}
        </div>

        {(showVersion || onQuickUpdate) && (
          <div className="sal-dish__desc">
            {showVersion && (
              <span className="relative min-w-0 flex-shrink">
                <button
                  ref={versionButtonRef}
                  type="button"
                  className="sal-dish__version"
                  disabled={!isSwitchable || isSwitchingVersion}
                  title={versionText}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (isSwitchable && !isSwitchingVersion) onVersionClick();
                  }}
                >
                  <span className="truncate">
                    {isSwitchingVersion ? t("profiles.v3.tile.switching") : versionText || "—"}
                  </span>
                  {isSwitchable && !isSwitchingVersion && (
                    <Icon icon="solar:alt-arrow-down-linear" className="h-3 w-3 flex-shrink-0" />
                  )}
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
            )}

            {onQuickUpdate && (
              <Tooltip
                content={
                  isQuickUpdating
                    ? t("profiles.v3.tile.updating")
                    : (quickUpdateTooltip ?? t("profiles.v3.tile.updateToLatest"))
                }
              >
                <button
                  type="button"
                  className="sal-dish__update"
                  disabled={quickUpdateDisabled || isQuickUpdating}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (!quickUpdateDisabled && !isQuickUpdating) onQuickUpdate();
                  }}
                >
                  <Icon
                    icon={isQuickUpdating ? "solar:refresh-linear" : "solar:arrow-up-linear"}
                    className={isQuickUpdating ? "h-3 w-3 animate-spin" : "h-3 w-3"}
                  />
                  {isQuickUpdating ? t("profiles.v3.tile.updating") : t("salami.look.salumeria.item.update")}
                </button>
              </Tooltip>
            )}
          </div>
        )}
      </div>

      {item.file_size > 0 && <span className="sal-dish__weight">{formatFileSize(item.file_size)}</span>}

      <span className="sal-dish__tools">
        <CheckboxV2
          size="sm"
          checked={isSelected}
          onChange={() => onToggleSelection()}
          tooltip={isSelected ? t("profiles.v3.tile.deselect") : t("profiles.v3.tile.select")}
        />
        <button
          ref={menuButtonRef}
          type="button"
          className="sal-dish__menu"
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
