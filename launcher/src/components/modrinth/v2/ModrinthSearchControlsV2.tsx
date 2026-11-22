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
import { kante, KANTE_SOFT, readableOn, SURFACE } from "../../ui/design-system";
import { Icon } from "@iconify/react";
import { useDisplayContextStore } from "../../../store/useDisplayContextStore";
import { useThemeStore } from "../../../store/useThemeStore";
import { cn } from "../../../lib/utils";
import { KanteButton, PLAIN_CARD, RemovableLabel as FilterChip, tagLabel } from "./FeinkostParts";

// Define Profile type locally, similar to ModrinthSearchV2.tsx
type Profile = any;

const PROJECT_TYPE_LABEL_KEYS: Record<ModrinthProjectType, string> = {
  mod: "modrinth.project_types.mods",
  modpack: "modrinth.project_types.modpacks",
  resourcepack: "modrinth.project_types.resource_packs",
  shader: "modrinth.project_types.shaders",
  datapack: "modrinth.project_types.datapacks",
};

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

  // Translated plural name per project type ("Ressourcenpakete", not "resourcepacks")
  const typeLabel = (type: ModrinthProjectType): string =>
    t(PROJECT_TYPE_LABEL_KEYS[type] ?? "", { defaultValue: type.charAt(0).toUpperCase() + type.slice(1) + 's' });

  // Create groups array for project types
  const groups: GroupTab[] = allProjectTypes.map(type => ({
    id: type,
    name: typeLabel(type),
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
      {/* Feinkost: Inhaltsarten als Theken-Reiter (aktiv = volle Akzentfarbe mit Unterkante), rechts die Quelle */}
      <div className="look-search-types mb-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div
          className="look-card look-tabs flex min-w-0 flex-wrap items-center gap-1 p-1"
          style={{ ...PLAIN_CARD, borderRadius: "calc(var(--border-radius) * 0.9)" }}
          role="tablist"
        >
          {groups.map((group) => {
            const active = group.id === projectType;
            return (
              <button
                key={group.id}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => onProjectTypeChange(group.id as ModrinthProjectType)}
                className={cn(
                  "flex h-9 flex-shrink-0 items-center px-3.5 font-minecraft text-base leading-none transition-[background-color,color,filter] duration-150 motion-reduce:transition-none",
                  active ? "hover:brightness-110" : "text-white/55 hover:bg-white/[0.06] hover:text-white",
                )}
                style={{
                  borderRadius: "calc(var(--border-radius) * 0.7)",
                  ...(active
                    ? {
                        backgroundColor: accentColor.value,
                        color: readableOn(accentColor.value),
                        boxShadow: kante(accentColor.value),
                      }
                    : {}),
                }}
              >
                {group.name}
              </button>
            );
          })}
        </div>

        {/* Quelle: kleiner Umschalter, die gewählte Plattform liegt erhaben */}
        <div
          className="look-card look-tabs flex flex-shrink-0 items-center gap-1 p-1 text-sm"
          style={{ ...PLAIN_CARD, borderRadius: "calc(var(--border-radius) * 0.9)" }}
        >
          {sources.map((source) => {
            const active = modSource === source.id;
            return (
              <button
                key={source.id}
                type="button"
                onClick={() => onModSourceChange(source.id)}
                title={source.title}
                aria-pressed={active}
                className={cn(
                  "flex h-8 items-center gap-1.5 px-2.5 font-minecraft transition-colors duration-150 motion-reduce:transition-none",
                  active ? "text-white" : "text-white/45 hover:text-white/80",
                )}
                style={{
                  borderRadius: "calc(var(--border-radius) * 0.7)",
                  backgroundColor: active ? SURFACE.raised : "transparent",
                  boxShadow: active ? `inset 0 0 0 1px ${SURFACE.lineStrong}, ${KANTE_SOFT}` : undefined,
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
      <div className="look-search-bar mb-3">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 flex-1">
            <SearchWithFilters
              className="min-w-0 flex-1 [&>div]:max-w-none"
              placeholder={t('content.search_placeholder', { type: projectType, typeLabel: typeLabel(projectType) })}
              searchValue={searchTerm}
              onSearchChange={onSearchTermChange}
              sortOptions={sortOptions}
              sortValue={sortOrder}
              onSortChange={(value) => onSortOrderChange(value as UnifiedSortType)}
            />

            <KanteButton
              onClick={onToggleSidebar}
              active={isSidebarVisible}
              className="h-10"
              title={isSidebarVisible ? t('content.filters.hide') : t('content.filters.show')}
              icon={<Icon icon="solar:tuning-2-bold" className="h-4 w-4" />}
            >
              <span>{t('modrinth.filters')}</span>
              {totalFilters > 0 && (
                <span
                  className="flex h-4 min-w-4 items-center justify-center px-1 text-[10px] leading-none"
                  style={{ backgroundColor: accentColor.value, color: readableOn(accentColor.value), borderRadius: 999 }}
                >
                  {totalFilters}
                </span>
              )}
            </KanteButton>
          </div>
        </div>

        {/* Gewählte Filter als Etiketten – Klick entfernt den Filter */}
        {totalFilters > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-1.5">
            <KanteButton
              tone="ghost"
              size="sm"
              onClick={onClearAllFilters}
              icon={<Icon icon="solar:trash-bin-trash-bold" className="h-4 w-4" />}
            >
              {t('content.filters.clear_all')}
            </KanteButton>

            {selectedGameVersions.map((version) => (
              <FilterChip key={`gv-${version}`} onRemove={() => onRemoveGameVersionTag(version)}>
                {version}
              </FilterChip>
            ))}

            {currentSelectedLoaders.map((loader) => (
              <FilterChip key={`loader-${loader}`} tone="red" onRemove={() => onRemoveLoaderTag(loader)}>
                {tagLabel(loader)}
              </FilterChip>
            ))}

            {currentSelectedCategories.map((category) => (
              <FilterChip key={`cat-${category}`} onRemove={() => onRemoveCategoryTag(category)}>
                {tagLabel(category)}
              </FilterChip>
            ))}

            {filterClientRequired && (
              <FilterChip key="client-req" onRemove={onRemoveClientRequiredTag}>
                {t('content.filters.client')}
              </FilterChip>
            )}

            {filterServerRequired && (
              <FilterChip key="server-req" onRemove={onRemoveServerRequiredTag}>
                {t('content.filters.server')}
              </FilterChip>
            )}
          </div>
        )}
      </div>
    </>
  );
};
