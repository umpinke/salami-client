"use client";

import React, { useEffect, useState } from "react";
import type {
  ModrinthGameVersion,
} from "../../../types/modrinth";
import type { UnifiedModSearchResult, UnifiedVersion } from "../../../types/unified";

// Unified project card supporting both Modrinth and CurseForge
type CompatibleProject = UnifiedModSearchResult;
import type { AccentColor } from "../../../store/useThemeStore";
import type { ContentInstallStatus } from "../../../types/profile";
import { Icon } from "@iconify/react";
import { cn } from "../../../lib/utils";
import { ModrinthVersionListV2 } from "./ModrinthVersionListV2";
import { openExternalUrl } from "../../../services/tauri-service";
import { toast } from "react-hot-toast";
import { preloadIcons } from "../../../lib/icon-utils";
import { useTranslation } from "react-i18next";
import { SURFACE } from "../../ui/design-system";
import { useNavigate } from "react-router-dom";
import { CartToggleButton } from "../../../features/mod-downloader/CartToggleButton";
import { DeliLabel } from "../../../themes/salami/SalamiStage";
import { cardSurface, ICON_WELL, isLoaderTag, KanteButton, tagLabel } from "./FeinkostParts";
import { useLook } from "../../../themes/looks/registry";

type Profile = any;

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
    const look = useLook();
    const [isHovered, setIsHovered] = useState(false);

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
    const installBusy =
      hit.project_type === "modpack" ? !!isInstallingModpackAsProfile : !!isQuickInstalling;
    const alreadyInstalled = !!installStatus?.is_installed && !!selectedProfile;
    const installDisabled = !!isInstallingModpackAsProfile || !!isQuickInstalling || alreadyInstalled;

    const categoryTags: string[] = (hit.categories ?? []).filter((c: string) => !isLoaderTag(c));
    const loaderTags: string[] = (hit.categories ?? []).filter((c: string) => isLoaderTag(c));
    const lifted = isHovered && !isExpanded;

    const handleInstall = () => {
      if (hit.project_type === "modpack" && onInstallModpackAsProfileClick) {
        onInstallModpackAsProfileClick(hit);
      } else {
        onQuickInstallClick(hit);
      }
    };
    const handleOpenAuthor = () => {
      openExternalUrl(authorUrl).catch((error) => {
        console.error("Failed to open external URL:", error);
        toast.error(t('common.open_link_failed'));
      });
    };

    if (look.SearchCard) {
      const LookSearchCard = look.SearchCard;
      return (
        <LookSearchCard
          hit={hit}
          installStatus={installStatus}
          downloadsLabel={compactNumber(hit.downloads, i18n.language)}
          categories={categoryTags.slice(0, 2).map(tagLabel)}
          installBusy={installBusy}
          alreadyInstalled={alreadyInstalled}
          installDisabled={installDisabled}
          onInstall={handleInstall}
          onOpen={() => {
            const source: "modrinth" | "curseforge" = hit.source === "Modrinth" ? "modrinth" : "curseforge";
            if (onProjectClick) onProjectClick(hit, source);
            else navigate(`/mods/${source}/${hit.project_id}`);
          }}
          onOpenAuthor={handleOpenAuthor}
          cartButton={<CartToggleButton project={hit} />}
        />
      );
    }

    return (
      // Feinkost-Karte: Fläche mit Haarlinie, beim Hover angehoben mit Akzentrand; Versionen klappen in der Karte auf
      <div className="pb-2.5 pt-0.5">
        <div
          className="look-card overflow-hidden transition-[transform,box-shadow,border-color,background-color] duration-200 motion-reduce:transition-none"
          style={cardSurface(accentColor.value, lifted || isExpanded, lifted)}
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
        >
        <div
          onClick={handleTitleClick}
          className="group relative flex cursor-pointer items-center gap-4 py-3 pl-3 pr-3"
        >
          {/* Project Icon */}
          <div
            className="relative h-14 w-14 flex-shrink-0 overflow-hidden"
            style={{
              backgroundColor: ICON_WELL,
              boxShadow: `0 0 0 1px ${SURFACE.line}`,
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
              <div className="flex h-full w-full items-center justify-center font-minecraft text-xl text-[#f3e3d3]/50">
                {String(hit.title ?? "?").trim().charAt(0).toUpperCase() || "?"}
              </div>
            )}
          </div>

          {/* Project Info */}
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <span
                className="overflow-hidden text-ellipsis whitespace-nowrap text-left font-minecraft text-lg normal-case leading-tight text-white"
                title={t('modrinth.view_details', { title: hit.title })}
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
                  title={t('modrinth.open_author', { author: hit.author, platform: hit.source === 'Modrinth' ? 'Modrinth' : 'CurseForge' })}
                >
                  {hit.author}
                </a>
              )}
            </div>

            <p className="mt-0.5 line-clamp-1 text-xs leading-snug text-white/55">
              {hit.description}
            </p>

            {/* Kennzahlen, Status und Kategorien als Etiketten von der Theke */}
            <div className="mt-2 flex items-center gap-1.5 overflow-hidden whitespace-nowrap">
              <span
                className="mr-1 flex flex-shrink-0 items-center gap-1 font-minecraft text-xs text-white/60"
                title={hit.downloads.toLocaleString()}
              >
                <Icon icon="solar:download-minimalistic-bold" className="h-3.5 w-3.5" />
                {compactNumber(hit.downloads, i18n.language)}
              </span>
              {installStatus?.is_installed && (
                <span className="flex-shrink-0">
                  <DeliLabel size="sm">
                    <Icon icon="solar:check-circle-bold" className="h-3 w-3" />
                    {t('modrinth.installed')}
                  </DeliLabel>
                </span>
              )}
              {installStatus?.is_included_in_norisk_pack && (
                <span
                  className="flex-shrink-0"
                  style={{ opacity: installStatus.norisk_pack_item_details?.is_enabled === false ? 0.5 : 1 }}
                >
                  <DeliLabel size="sm" tone="red">Salami Pack</DeliLabel>
                </span>
              )}
              {loaderTags.slice(0, 2).map((loader) => (
                <span key={loader} className="flex-shrink-0">
                  <DeliLabel size="sm" tone="red">{tagLabel(loader)}</DeliLabel>
                </span>
              ))}
              {categoryTags.slice(0, 2).map((category) => (
                <span key={category} className="min-w-0 flex-shrink-0">
                  <DeliLabel size="sm">{tagLabel(category)}</DeliLabel>
                </span>
              ))}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-shrink-0 items-center gap-1.5">
            <KanteButton
              tone={installBusy ? "secondary" : alreadyInstalled ? "done" : "primary"}
              disabled={installDisabled}
              onClick={(e) => {
                e.stopPropagation();
                if (hit.project_type === "modpack" && onInstallModpackAsProfileClick) {
                  onInstallModpackAsProfileClick(hit);
                } else {
                  onQuickInstallClick(hit);
                }
              }}
              icon={
                <Icon
                  icon={
                    installBusy
                      ? "solar:refresh-bold"
                      : alreadyInstalled
                        ? "solar:check-circle-bold"
                        : "solar:download-minimalistic-bold"
                  }
                  className={cn("h-4 w-4", installBusy && "animate-spin-slow")}
                />
              }
            >
              {installBusy ? t('modrinth.installing') : alreadyInstalled ? t('modrinth.installed') : t('modrinth.install')}
            </KanteButton>
            <CartToggleButton project={hit} />
            <KanteButton
              iconOnly
              active={isExpanded}
              disabled={isLoadingVersions}
              title={isExpanded ? t('modrinth.hide_versions') : t('modrinth.show_versions')}
              aria-label={isExpanded ? t('modrinth.hide_versions') : t('modrinth.show_versions')}
              onClick={(e) => {
                e.stopPropagation();
                onToggleVersionsClick(hit.project_id);
              }}
              icon={
                <Icon
                  icon={
                    isLoadingVersions
                      ? "solar:refresh-bold"
                      : isExpanded
                        ? "solar:alt-arrow-up-bold"
                        : "solar:alt-arrow-down-bold"
                  }
                  className={cn("h-4 w-4", isLoadingVersions && "animate-spin-fast")}
                />
              }
            />
          </div>
        </div>

        {/* Version List - in der Karte, durch eine Haarlinie abgesetzt */}
        {isExpanded &&
          Array.isArray(projectVersions) &&
          projectVersions.length > 0 && (
            <div className="border-t" style={{ borderColor: SURFACE.line }}>
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
      </div>
    );
  },
);
