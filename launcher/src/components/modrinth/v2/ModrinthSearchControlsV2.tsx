"use client";

import React, { useRef } from "react";
import { useTranslation } from "react-i18next";
import type {
  ModrinthProjectType,
} from "../../../types/modrinth";
import { UnifiedSortType, ModPlatform } from "../../../types/unified";
// Profile type will be defined locally
import { SearchWithFilters } from "../../ui/SearchWithFilters";
import type { GroupTab } from "../../ui/GroupTabs";
import { hexAlpha, SURFACE } from "../../ui/design-system";
import { IconButton } from "../../ui/buttons/IconButton";
import { TagBadge } from "../../ui/TagBadge";
import { Icon } from "@iconify/react";
import { useDisplayContextStore } from "../../../store/useDisplayContextStore";
import { useThemeStore } from "../../../store/useThemeStore";
import { cn } from "../../../lib/utils";

// Define Profile type locally, similar to ModrinthSearchV2.tsx
type Profile = any;

export // Define SelectOption type locally
interface SelectOption {
  value: UnifiedSortType;
  label: string;
  icon?: string;
}

interface ModrinthSearchControlsV2Props {
  searchTerm: string;
  onSearchTermChange: (term: string) => void;
  projectType: ModrinthProjectType;
  onProjectTypeChange: (type: ModrinthProjectType) => void;
  allProjectTypes: ModrinthProjectType[]; // This will be ALL_MODRINTH_PROJECT_TYPES from parent
  profiles: Profile[];
  selectedProfile: Profile | null;
  onSelectedProfileChange: (profile: Profile | null) => void;
  sortOrder: UnifiedSortType;
  onSortOrderChange: (sort: UnifiedSortType) => void;
  sortOptions: SelectOption[];
  isSidebarVisible: boolean;
  onToggleSidebar: () => void;
  selectedGameVersions: string[];
  currentSelectedLoaders: string[];
  currentSelectedCategories: string[];
  filterClientRequired: boolean;
  filterServerRequired: boolean;
  onRemoveGameVersionTag: (version: string) => void;
  onRemoveLoaderTag: (loader: string) => void;
  onRemoveCategoryTag: (category: string) => void;
  onRemoveClientRequiredTag: () => void;
  onRemoveServerRequiredTag: () => void;
  onClearAllFilters: () => void;
  overrideDisplayContext?: "detail" | "standalone";
  modSource: ModPlatform;
  onModSourceChange: (source: ModPlatform) => void;
}

export const ModrinthSearchControlsV2: React.FC<
  ModrinthSearchControlsV2Props
> = ({
  searchTerm,
  onSearchTermChange,
  projectType,
  onProjectTypeChange,
  allProjectTypes,
  profiles,
  selectedProfile,
  onSelectedProfileChange,
  sortOrder,
  onSortOrderChange,
  sortOptions,
  isSidebarVisible,
  onToggleSidebar,
  selectedGameVersions,
  currentSelectedLoaders,
  currentSelectedCategories,
  filterClientRequired,
  filterServerRequired,
  onRemoveGameVersionTag,
  onRemoveLoaderTag,
  onRemoveCategoryTag,
  onRemoveClientRequiredTag,
  onRemoveServerRequiredTag,
  onClearAllFilters,
  overrideDisplayContext,
  modSource,
  onModSourceChange,
}) => {
  const { t } = useTranslation();
  const globalDisplayContext = useDisplayContextStore((state) => state.context);
  const effectiveDisplayContext =
    overrideDisplayContext || globalDisplayContext;
  const accentColor = useThemeStore((state) => state.accentColor);
  const filtersContainerRef = useRef<HTMLDivElement>(null);

  const isDetailView = effectiveDisplayContext === "detail";
  const buttonSize = isDetailView
    ? isSidebarVisible
      ? "xs"
      : "sm"
    : !isSidebarVisible
      ? "lg"
      : "sm";

  // Calculate total number of active filters
  const totalFilters =
    selectedGameVersions.length +
    currentSelectedLoaders.length +
    currentSelectedCategories.length +
    (filterClientRequired ? 1 : 0) +
    (filterServerRequired ? 1 : 0);

  // Create groups array for project types
  const groups: GroupTab[] = allProjectTypes.map(type => ({
    id: type,
    name: type.charAt(0).toUpperCase() + type.slice(1) + 's',
    count: 0, // Could be populated with result counts if needed
  }));

  const sources = [
    {
      id: ModPlatform.Modrinth,
      name: "Modrinth",
      icon: "https://cdn.modrinth.com/modrinth-new.png",
      title: t('content.search_modrinth'),
    },
    {
      id: ModPlatform.CurseForge,
      name: "CurseForge",
      icon: "/icons/curseforge.svg",
      title: t('content.search_curseforge'),
    },
  ];

  return (
    <>
      {/* Inhaltsarten als Textreiter mit Akzentstrich, rechts die Quelle */}
      <div
        className="mb-4 flex items-end justify-between gap-4 border-b"
        style={{ borderColor: SURFACE.line }}
      >
        <div className="flex min-w-0 items-end gap-6 overflow-x-auto">
          {groups.map((group) => {
            const active = group.id === projectType;
            return (
              <button
                key={group.id}
                type="button"
                onClick={() => onProjectTypeChange(group.id as ModrinthProjectType)}
                className={cn(
                  "relative flex-shrink-0 pb-2.5 font-minecraft text-2xl lowercase leading-none transition-colors duration-150",
                  active ? "text-white" : "text-white/40 hover:text-white/75",
                )}
                style={{ borderRadius: 0 }}
              >
                {group.name}
                {active && (
                  <span
                    className="absolute inset-x-0 -bottom-px h-[3px]"
                    style={{ backgroundColor: accentColor.value }}
                  />
                )}
              </button>
            );
          })}
        </div>

        <div className="mb-2 flex flex-shrink-0 items-center gap-3 text-sm">
          {sources.map((source) => {
            const active = modSource === source.id;
            return (
              <button
                key={source.id}
                type="button"
                onClick={() => onModSourceChange(source.id)}
                title={source.title}
                className={cn(
                  "flex items-center gap-1.5 border px-2.5 py-1 font-minecraft lowercase transition-colors duration-150",
                  active ? "text-white" : "text-white/45 hover:text-white/80",
                )}
                style={{
                  backgroundColor: active ? hexAlpha(accentColor.value, 0.16) : "transparent",
                  borderColor: active ? hexAlpha(accentColor.value, 0.45) : SURFACE.line,
                }}
              >
                <img
                  src={source.icon}
                  alt=""
                  className={cn("h-4 w-4 object-contain", !active && "opacity-60 grayscale")}
                />
                <span>{source.name}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Search & Filter Header */}
      <div className="mb-3">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 flex-1">
            <SearchWithFilters
              className="min-w-0 flex-1 [&>div]:max-w-none"
              placeholder={t('content.search_placeholder', { type: projectType })}
              searchValue={searchTerm}
              onSearchChange={onSearchTermChange}
              sortOptions={sortOptions}
              sortValue={sortOrder}
              onSortChange={(value) => onSortOrderChange(value as UnifiedSortType)}
            />

            <button
              onClick={onToggleSidebar}
              className="flex min-h-[2.5rem] items-center gap-2 border px-3 font-minecraft text-sm lowercase transition-colors duration-150"
              style={{
                backgroundColor: isSidebarVisible ? hexAlpha(accentColor.value, 0.16) : SURFACE.base,
                borderColor: isSidebarVisible ? hexAlpha(accentColor.value, 0.45) : SURFACE.line,
                color: isSidebarVisible ? "#fff" : "rgba(255,255,255,0.6)",
              }}
              title={isSidebarVisible ? t('content.filters.hide') : t('content.filters.show')}
            >
              <Icon icon="solar:tuning-2-bold" className="h-4 w-4" />
              <span>{t('content.filters.title', 'Filter')}</span>
              {totalFilters > 0 && (
                <span
                  className="flex h-4 min-w-4 items-center justify-center px-1 text-[10px] leading-none text-white"
                  style={{ backgroundColor: accentColor.value, borderRadius: 999 }}
                >
                  {totalFilters}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Filter Tags - Under Search */}
        {totalFilters > 0 && (
          <div className="flex items-center gap-2 mt-4">
            <TagBadge
              variant="destructive"
              className="cursor-pointer hover:brightness-110 transition-all flex-shrink-0 flex items-center"
              onClick={onClearAllFilters}
              size="md"
            >
              <Icon
                icon="solar:trash-bin-trash-bold"
                className="w-4 h-4 mr-1"
              />
              <span>{t('content.filters.clear_all')}</span>
            </TagBadge>

            {selectedGameVersions.map((version) => (
              <TagBadge
                key={`gv-${version}`}
                variant="filter"
                className="inline-flex whitespace-nowrap items-center"
                size="md"
                onClick={() => onRemoveGameVersionTag(version)}
              >
                <span>{version}</span>
                <Icon
                  icon="solar:close-circle-bold"
                  className="w-4 h-4 ml-1"
                />
              </TagBadge>
            ))}

            {currentSelectedLoaders.map((loader) => (
              <TagBadge
                key={`loader-${loader}`}
                variant="filter"
                className="inline-flex whitespace-nowrap items-center"
                size="md"
                onClick={() => onRemoveLoaderTag(loader)}
              >
                <span>{loader}</span>
                <Icon
                  icon="solar:close-circle-bold"
                  className="w-4 h-4 ml-1"
                />
              </TagBadge>
            ))}

            {currentSelectedCategories.map((category) => (
              <TagBadge
                key={`cat-${category}`}
                variant="filter"
                className="inline-flex whitespace-nowrap items-center"
                size="md"
                onClick={() => onRemoveCategoryTag(category)}
              >
                <span>{category}</span>
                <Icon
                  icon="solar:close-circle-bold"
                  className="w-4 h-4 ml-1"
                />
              </TagBadge>
            ))}

            {filterClientRequired && (
              <TagBadge
                key="client-req"
                variant="filter"
                className="inline-flex whitespace-nowrap items-center"
                size="md"
                onClick={onRemoveClientRequiredTag}
              >
                <span>{t('content.filters.client')}</span>
                <Icon
                  icon="solar:close-circle-bold"
                  className="w-4 h-4 ml-1"
                />
              </TagBadge>
            )}

            {filterServerRequired && (
              <TagBadge
                key="server-req"
                variant="filter"
                className="inline-flex whitespace-nowrap items-center"
                size="md"
                onClick={onRemoveServerRequiredTag}
              >
                <span>{t('content.filters.server')}</span>
                <Icon
                  icon="solar:close-circle-bold"
                  className="w-4 h-4 ml-1"
                />
              </TagBadge>
            )}
          </div>
        )}
      </div>
    </>
  );
};
