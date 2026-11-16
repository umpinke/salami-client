"use client";

import React, { useEffect } from "react";
import type {
  ModrinthGameVersion,
} from "../../../types/modrinth";
import type { UnifiedModSearchResult, UnifiedVersion } from "../../../types/unified";

// Unified project card supporting both Modrinth and CurseForge
type CompatibleProject = UnifiedModSearchResult;
import type { AccentColor } from "../../../store/useThemeStore";
import type { ContentInstallStatus } from "../../../types/profile";
import { ActionButton } from "../../ui/ActionButton";
import { Icon } from "@iconify/react";
import { cn } from "../../../lib/utils";
import { ModrinthVersionListV2 } from "./ModrinthVersionListV2";
import { openExternalUrl } from "../../../services/tauri-service";
import { toast } from "react-hot-toast";
import { preloadIcons } from "../../../lib/icon-utils";
import { useTranslation } from "react-i18next";
import { readableOn, SURFACE } from "../../ui/design-system";
import { useNavigate } from "react-router-dom";
import { CartToggleButton } from "../../../features/mod-downloader/CartToggleButton";

type Profile = any;

const INSTALLED_COLOR = "#4ade80";
const PACK_COLOR = "#60a5fa";

const compactFormatters = new Map<string, Intl.NumberFormat>();
/** 286345789 → „286,3 Mio.“ – kurz genug für die Kennzahlenzeile. */
function compactNumber(value: number, language: string): string {
  let formatter = compactFormatters.get(language);
  if (!formatter) {
    try {
      formatter = new Intl.NumberFormat(language, { notation: "compact", maximumFractionDigits: 1 });
    } catch {
      formatter = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });
    }
    compactFormatters.set(language, formatter);
  }
  return formatter.format(value);
}

interface VersionListPassthroughProps {
  projectVersions: UnifiedVersion[] | null | "loading";
  displayedCount: number;
  versionFilters: {
    gameVersions: string[];
    loaders: string[];
    versionType: string;
  };
  versionDropdownUIState: {
    showAllGameVersions: boolean;
    gameVersionSearchTerm: string;
  };
  openVersionDropdowns: {
    type: boolean;
    gameVersion: boolean;
    loader: boolean;
  };
  installedVersions: Record<string, ContentInstallStatus | null>;
  selectedProfile: Profile | null;
  hoveredVersionId: string | null;
  gameVersionsData: ModrinthGameVersion[];
  showAllGameVersionsSidebar: boolean;
  selectedGameVersionsSidebar: string[];
  onVersionFilterChange: (
    projectId: string,
    filterType: "gameVersions" | "loaders" | "versionType",
    value: string | string[],
  ) => void;
  onVersionUiStateChange: (
    projectId: string,
    field: "showAllGameVersions" | "gameVersionSearchTerm",
    value: boolean | string,
  ) => void;
  onToggleVersionDropdown: (
    projectId: string,
    dropdownType: "type" | "gameVersion" | "loader",
  ) => void;
  onCloseAllVersionDropdowns: (projectId: string) => void;
  onLoadMoreVersions: (projectId: string) => void;
  onInstallVersionClick: (
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
  ) => void;
  onHoverVersion: (versionId: string | null) => void;
  selectedProfileId?: string | null;
  onDeleteVersionClick?: (
    profileId: string,
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
  ) => void;
  onToggleEnableClick?: (
    profileId: string,
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
    newEnabledState: boolean,
    sha1Hash: string,
  ) => void;
  itemIndex?: number;
}

export interface ModrinthProjectCardV2Props
  extends VersionListPassthroughProps {
  hit: UnifiedModSearchResult | any; // Temporary for compatibility
  accentColor: AccentColor;
  installStatus: ContentInstallStatus | null;
  isQuickInstalling?: boolean;
  isInstallingModpackAsProfile?: boolean;
  installingVersionStates?: Record<string, boolean>;
  installingModpackVersionStates?: Record<string, boolean>;
  onQuickInstallClick: (project: UnifiedModSearchResult | any) => void;
  onInstallModpackAsProfileClick?: (project: UnifiedModSearchResult) => void;
  onInstallModpackVersionAsProfileClick?: (
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
  ) => void;
  onToggleVersionsClick: (projectId: string) => void;
  isExpanded: boolean;
  isLoadingVersions: boolean;
  projectVersions: UnifiedVersion[] | null | "loading";
  displayedCount: number;
  versionDropdownUIState: {
    showAllGameVersions: boolean;
    gameVersionSearchTerm: string;
  };
  openVersionDropdowns: {
    type: boolean;
    gameVersion: boolean;
    loader: boolean;
  };
  installedVersions: Record<string, ContentInstallStatus | null>;
  selectedProfile: Profile | null;
  hoveredVersionId: string | null;
  gameVersionsData: ModrinthGameVersion[];
  showAllGameVersionsSidebar: boolean;
  selectedGameVersionsSidebar: string[];
  onVersionFilterChange: (
    projectId: string,
    filterType: "gameVersions" | "loaders" | "versionType",
    value: string | string[],
  ) => void;
  onVersionUiStateChange: (
    projectId: string,
    field: "showAllGameVersions" | "gameVersionSearchTerm",
    value: boolean | string,
  ) => void;
  onToggleVersionDropdown: (
    projectId: string,
    dropdownType: "type" | "gameVersion" | "loader",
  ) => void;
  onCloseAllVersionDropdowns: (projectId: string) => void;
  onLoadMoreVersions: (projectId: string) => void;
  onInstallVersionClick: (
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
  ) => void;
  onHoverVersion: (versionId: string | null) => void;
  selectedProfileId?: string | null;
  onDeleteVersionClick?: (
    profileId: string,
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
  ) => void;
  onToggleEnableClick?: (
    profileId: string,
    project: UnifiedModSearchResult | any,
    version: UnifiedVersion,
    newEnabledState: boolean,
    sha1Hash: string,
  ) => void;
  itemIndex?: number;
  /**
   * Override the default title-click behavior. Used by in-place detail
   * views (e.g. the V3 Add-content sheet) to render the mod detail as a
   * stacked layer instead of navigating away from the current surface.
   * The router-based full-page fallback is used when not provided.
   */
  onProjectClick?: (
    project: UnifiedModSearchResult | any,
    source: "modrinth" | "curseforge",
  ) => void;
}

export const ModrinthProjectCardV2 = React.memo<ModrinthProjectCardV2Props>(
  ({
    hit,
    accentColor,
    installStatus,
    isQuickInstalling,
    isInstallingModpackAsProfile,
    installingVersionStates,
    installingModpackVersionStates,
    onQuickInstallClick,
    onInstallModpackAsProfileClick,
    onInstallModpackVersionAsProfileClick,
    onToggleVersionsClick,
    isExpanded,
    isLoadingVersions,
    projectVersions,
    displayedCount,
    versionFilters,
    versionDropdownUIState,
    openVersionDropdowns,
    installedVersions,
    selectedProfile,
    hoveredVersionId,
    gameVersionsData,
    showAllGameVersionsSidebar,
    selectedGameVersionsSidebar,
    onVersionFilterChange,
    onVersionUiStateChange,
    onToggleVersionDropdown,
    onCloseAllVersionDropdowns,
    onLoadMoreVersions,
    onInstallVersionClick,
    onHoverVersion,
    selectedProfileId,
    onDeleteVersionClick,
    onToggleEnableClick,
    itemIndex,
    onProjectClick,
  }) => {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();

    useEffect(() => {
      preloadIcons([
        "solar:download-minimalistic-bold",
        "solar:alt-arrow-up-bold",
        "solar:alt-arrow-down-bold",
      ]);
    }, []);

    const handleTitleClick = (e: React.MouseEvent) => {
      e.preventDefault();
      const source: "modrinth" | "curseforge" =
        hit.source === "Modrinth" ? "modrinth" : "curseforge";
      // When a consumer owns the surface (e.g. the Add-content sheet),
      // let them handle the click in-place instead of hard-navigating
      // away from their overlay.
      if (onProjectClick) {
        onProjectClick(hit, source);
        return;
      }
      navigate(`/mods/${source}/${hit.project_id}`);
    };

    const authorUrl =
      hit.source === "Modrinth"
        ? `https://modrinth.com/user/${hit.author}`
        : `https://www.curseforge.com/members/${hit.author}/projects`;
    const statusColor = installStatus?.is_included_in_norisk_pack
      ? PACK_COLOR
      : installStatus?.is_installed
        ? INSTALLED_COLOR
        : null;
    const installBusy =
      hit.project_type === "modpack" ? !!isInstallingModpackAsProfile : !!isQuickInstalling;
    const alreadyInstalled = !!installStatus?.is_installed && !!selectedProfile;
    const installDisabled = !!isInstallingModpackAsProfile || !!isQuickInstalling || alreadyInstalled;

    return (
      <div>
        {/* Zeile der Zutatenliste: Platznummer, Bild, Text, Aktionen – Haarlinie statt Kasten */}
        <div
          onClick={handleTitleClick}
          className="group relative flex cursor-pointer items-center gap-4 border-b py-3 pl-2 pr-3 transition-colors duration-150 hover:bg-white/[0.04]"
          style={{ borderColor: SURFACE.line }}
        >
          {statusColor && (
            <span
              className="absolute bottom-2.5 left-0 top-2.5 w-[3px]"
              style={{ backgroundColor: statusColor }}
              aria-hidden="true"
            />
          )}

          {typeof itemIndex === "number" && (
            <span
              className="w-9 flex-shrink-0 text-right font-minecraft text-2xl leading-none tabular-nums text-white/[0.16] transition-colors duration-150 group-hover:text-white/45"
              aria-hidden="true"
            >
              {String(itemIndex + 1).padStart(2, "0")}
            </span>
          )}

          {/* Project Icon */}
          <div
            className="relative h-14 w-14 flex-shrink-0 overflow-hidden border"
            style={{
              borderColor: SURFACE.line,
              backgroundColor: SURFACE.base,
              borderRadius: "calc(var(--border-radius) * 0.8)",
            }}
          >
            {hit.icon_url ? (
              <img
                src={hit.icon_url || "/placeholder.svg"}
                alt={`${hit.title} icon`}
                className="h-full w-full object-cover"
                loading="lazy"
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center font-minecraft text-xl uppercase text-white/30">
                {String(hit.title ?? "?").trim().charAt(0) || "?"}
              </div>
            )}
          </div>

          {/* Project Info */}
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <span
                className="overflow-hidden text-ellipsis whitespace-nowrap text-left font-minecraft text-lg normal-case leading-tight text-white"
                title={`View ${hit.title} details`}
              >
                {hit.title}
              </span>
              {hit.author && (
                <a
                  href={authorUrl}
                  onClick={async (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    try {
                      await openExternalUrl(authorUrl);
                    } catch (error) {
                      console.error("Failed to open external URL:", error);
                      toast.error(t('common.open_link_failed'));
                    }
                  }}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="min-w-0 flex-shrink truncate font-minecraft text-xs text-white/40 hover:text-white/80 hover:underline"
                  title={`Open ${hit.author}'s profile on ${hit.source === 'Modrinth' ? 'Modrinth' : 'CurseForge'}`}
                >
                  {hit.author}
                </a>
              )}
            </div>

            <p className="mt-0.5 line-clamp-1 font-minecraft text-xs leading-snug text-white/55">
              {hit.description}
            </p>

            {/* Kennzahlen und Kategorien als ruhige Textzeile */}
            <div className="mt-1.5 flex items-center gap-2 overflow-hidden whitespace-nowrap font-minecraft text-[11px] text-white/40">
              <span className="flex flex-shrink-0 items-center gap-1 text-white/65" title={hit.downloads.toLocaleString()}>
                <Icon icon="solar:download-minimalistic-bold" className="h-3 w-3" />
                {compactNumber(hit.downloads, i18n.language)}
              </span>
              {installStatus?.is_installed && (
                <span className="flex-shrink-0" style={{ color: INSTALLED_COLOR }}>
                  ● Installed
                </span>
              )}
              {installStatus?.is_included_in_norisk_pack && (
                <span
                  className="flex-shrink-0"
                  style={{
                    color: PACK_COLOR,
                    opacity: installStatus.norisk_pack_item_details?.is_enabled === false ? 0.5 : 1,
                  }}
                >
                  ● Salami Pack
                </span>
              )}
              {(hit.categories ?? []).slice(0, 3).map((category: string) => (
                <React.Fragment key={category}>
                  <span className="text-white/20" aria-hidden="true">/</span>
                  <span className="truncate lowercase">{category.replace(/-/g, " ")}</span>
                </React.Fragment>
              ))}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-shrink-0 items-center gap-1">
            <button
              type="button"
              disabled={installDisabled}
              onClick={(e) => {
                e.stopPropagation();
                if (hit.project_type === "modpack" && onInstallModpackAsProfileClick) {
                  onInstallModpackAsProfileClick(hit);
                } else {
                  onQuickInstallClick(hit);
                }
              }}
              className={cn(
                "flex h-9 items-center gap-2 px-3.5 font-minecraft text-sm lowercase text-white transition-[filter,background-color] duration-150",
                installDisabled ? "cursor-not-allowed opacity-50" : "hover:brightness-110",
              )}
              style={{
                backgroundColor: installBusy || alreadyInstalled ? SURFACE.raised : accentColor.value,
                color: installBusy || alreadyInstalled ? "#fff" : readableOn(accentColor.value),
              }}
            >
              <Icon
                icon={
                  installBusy
                    ? "solar:refresh-bold"
                    : "solar:download-minimalistic-bold"
                }
                className={cn("h-4 w-4", installBusy && "animate-spin-slow")}
              />
              <span>{installBusy ? "Installing..." : alreadyInstalled ? "Installed" : "Install"}</span>
            </button>
            <CartToggleButton project={hit} />
            <ActionButton
              icon={
                isLoadingVersions
                  ? "solar:refresh-bold"
                  : isExpanded
                    ? "solar:alt-arrow-up-bold"
                    : "solar:alt-arrow-down-bold"
              }
              iconClassName={isLoadingVersions ? "animate-spin-fast" : ""}
              variant="icon-only"
              disabled={isLoadingVersions}
              tooltip={isExpanded ? "Hide Versions" : "Show Versions"}
              onClick={(e) => {
                e.stopPropagation();
                onToggleVersionsClick(hit.project_id);
              }}
              size="sm"
            />
          </div>
        </div>

        {/* Version List - Below Card */}
        {isExpanded &&
          Array.isArray(projectVersions) &&
          projectVersions.length > 0 && (
            <div className="mb-3 mt-3 pl-11">
              <ModrinthVersionListV2
              projectId={hit.project_id}
              project={hit}
              versions={projectVersions as UnifiedVersion[]}
              displayedCount={displayedCount}
              filters={versionFilters}
              uiState={versionDropdownUIState}
              openDropdowns={openVersionDropdowns}
              installedVersions={installedVersions}
              installingVersionStates={installingVersionStates}
              installingModpackVersionStates={installingModpackVersionStates}
              selectedProfile={selectedProfile}
              selectedProfileId={selectedProfileId}
              hoveredVersionId={hoveredVersionId}
              gameVersionsData={gameVersionsData}
              showAllGameVersionsSidebar={showAllGameVersionsSidebar}
              selectedGameVersionsSidebar={selectedGameVersionsSidebar}
              accentColor={accentColor}
              onFilterChange={onVersionFilterChange}
              onUiStateChange={onVersionUiStateChange}
              onToggleDropdown={onToggleVersionDropdown}
              onCloseAllDropdowns={onCloseAllVersionDropdowns}
              onLoadMore={onLoadMoreVersions}
              onInstallClick={onInstallVersionClick}
              onInstallModpackVersionAsProfileClick={
                onInstallModpackVersionAsProfileClick
              }
              onHoverVersion={onHoverVersion}
              onDeleteClick={onDeleteVersionClick}
                onToggleEnableClick={onToggleEnableClick}
              />
            </div>
          )}
      </div>
    );
  },
);
