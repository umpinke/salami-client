import { useRef } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { ContentTileProps } from "../../../components/profiles/v3/tabs/local-content/ContentTile";
import {
  ThemedDropdown,
  ThemedDropdownDivider,
  ThemedDropdownItem,
} from "../../../components/profiles/v3/shared/ThemedDropdown";
import { VersionSelectDropdown } from "../../../components/profiles/v3/shared/VersionSelectDropdown";
import { CheckboxV2 } from "../../../components/ui/CheckboxV2";
import { Tooltip } from "../../../components/ui/Tooltip";
import { formatFileSize } from "../../../utils/format-file-size";
import { WerkbankIcon } from "./icons";

/**
 * Ein Eintrag im Inventar wie im Rezeptbuch: links der Slot mit dem Gegenstand (beim Überfahren
 * der Gegenstands-Tooltip), daneben Name und Version, rechts ein „AN/AUS“-Knopf wie in den
 * Spiel-Optionen. Ausgeschaltetes liegt ausgegraut im Slot. Auswählen und das Menü erscheinen beim
 * Überfahren.
 */
export function WerkbankContentItem({
  item,
  displayName,
  iconUrl,
  platformLabel,
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
  updateAvailable,
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
  const size = item.file_size > 0 ? formatFileSize(item.file_size) : null;
  const toggleLabel = enabled ? t("salami.werkbank.item.disable") : t("salami.werkbank.item.enable");

  // Gegenstands-Tooltip wie im Spiel: Name weiß, Angaben grau, Herkunft blau
  const tooltip = (
    <span className="wb-mctip">
      <span className="wb-mctip__name">{displayName}</span>
      {versionText && <span className="wb-mctip__line">{versionText}</span>}
      {size && <span className="wb-mctip__line">{size}</span>}
      {!enabled && <span className="wb-mctip__line wb-mctip__line--off">{t("salami.werkbank.item.off_hint")}</span>}
      {item.modpack_origin && (
        <span className="wb-mctip__line">
          {t("profiles.v3.tile.fromModpackTooltip", { source: item.modpack_origin.split(":")[0] })}
        </span>
      )}
      {platformLabel && <span className="wb-mctip__source">{platformLabel}</span>}
    </span>
  );

  return (
    <div
      className="wb-ci"
      data-off={!enabled || undefined}
      data-selected={isSelected || undefined}
      data-select-mode={selectMode || undefined}
      data-menu={menuOpen || undefined}
    >
      <Tooltip content={tooltip} delay={150} wrapperClassName="wb-ci__slot-wrap">
        <span className="wb-ci__slot">
          {iconUrl ? (
            <img
              src={iconUrl}
              alt=""
              loading="lazy"
              onError={(e) => {
                (e.currentTarget as HTMLImageElement).style.display = "none";
              }}
            />
          ) : (
            <WerkbankIcon name="mods" className="wb-ci__noicon" />
          )}
          {busy && (
            <span className="wb-ci__busy" aria-hidden="true">
              <Icon icon="svg-spinners:ring-resize" className="h-4 w-4" />
            </span>
          )}
        </span>
      </Tooltip>

      <div className="wb-ci__text">
        <div className="wb-ci__name-row">
          {onNameClick ? (
            <button
              type="button"
              className="wb-ci__name"
              title={displayName}
              onClick={(e) => {
                e.stopPropagation();
                onNameClick();
              }}
            >
              {displayName}
            </button>
          ) : (
            <span className="wb-ci__name" title={displayName}>
              {displayName}
            </span>
          )}
          {item.updates_enabled === false && (
            <Tooltip content={t("profiles.v3.tile.updateChecksPaused")}>
              <Icon icon="solar:volume-cross-bold" className="wb-ci__mark h-3 w-3" />
            </Tooltip>
          )}
          {item.modpack_origin && (
            <Tooltip content={t("profiles.v3.tile.fromModpackTooltip", { source: item.modpack_origin.split(":")[0] })}>
              <Icon icon="solar:box-bold" className="wb-ci__mark wb-ci__mark--pack h-3 w-3" />
            </Tooltip>
          )}
        </div>

        <div className="wb-ci__meta">
          <span className="relative min-w-0">
            <button
              ref={versionButtonRef}
              type="button"
              className="wb-ci__version"
              data-switchable={(isSwitchable && !isSwitchingVersion) || undefined}
              data-update={(updateAvailable && !isSwitchingVersion) || undefined}
              disabled={!isSwitchable || isSwitchingVersion}
              title={versionText}
              onClick={(e) => {
                e.stopPropagation();
                if (isSwitchable && !isSwitchingVersion) onVersionClick();
              }}
            >
              {isSwitchingVersion && <Icon icon="svg-spinners:ring-resize" className="h-3 w-3 flex-shrink-0" />}
              <span className="truncate">
                {isSwitchingVersion ? t("profiles.v3.tile.switching") : versionText || "—"}
              </span>
              {isSwitchable && !isSwitchingVersion && <span className="wb-ci__caret" aria-hidden="true" />}
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
          {size && <span className="wb-ci__size">{size}</span>}
        </div>
      </div>

      {onQuickUpdate && (
        <Tooltip
          content={isQuickUpdating ? t("profiles.v3.tile.updating") : (quickUpdateTooltip ?? t("profiles.v3.tile.updateToLatest"))}
        >
          <button
            type="button"
            className="wb-ci__update"
            aria-label={t("profiles.v3.tile.updateToLatest")}
            disabled={quickUpdateDisabled || isQuickUpdating}
            onClick={(e) => {
              e.stopPropagation();
              if (!quickUpdateDisabled && !isQuickUpdating) onQuickUpdate();
            }}
          >
            {isQuickUpdating ? (
              <Icon icon="solar:refresh-bold" className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <WerkbankIcon name="up" className="h-3.5 w-3.5" />
            )}
          </button>
        </Tooltip>
      )}

      <button
        type="button"
        role="switch"
        aria-checked={enabled}
        aria-label={`${displayName}: ${toggleLabel}`}
        title={toggleLabel}
        className="wb-ci__toggle"
        disabled={busy}
        onClick={onToggle}
      >
        {enabled ? t("salami.werkbank.item.on") : t("salami.werkbank.item.off")}
      </button>

      <span className="wb-ci__tools">
        <CheckboxV2
          size="sm"
          checked={isSelected}
          onChange={() => onToggleSelection()}
          tooltip={isSelected ? t("profiles.v3.tile.deselect") : t("profiles.v3.tile.select")}
        />
        <button
          ref={menuButtonRef}
          type="button"
          className="wb-ci__menu"
          aria-label={t("profiles.moreOptions")}
          onClick={(e) => {
            e.stopPropagation();
            onMenuToggle(!menuOpen);
          }}
        >
          <WerkbankIcon name="dots" className="h-4 w-4" />
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
