"use client";

/**
 * ProfileLeftRailV3 — einheitliche Linke-Achsen-Navigation fuer den
 * Detail-View. Merged die frueheren Main-Tabs (content/worlds/screenshots/logs)
 * und Content-Typen (mods/resourcepacks/...) zu einer Achse mit 2 Sektionen.
 */

import type React from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { Profile } from "../../../types/profile";
import { useThemeStore } from "../../../store/useThemeStore";
import { SURFACE, kante, readableOn } from "../../ui/design-system";

export type NavKey =
  | "mods" | "resourcepacks" | "shaderpacks" | "datapacks" | "nrc"
  | "worlds" | "screenshots" | "logs";

export const CONTENT_NAV_KEYS: NavKey[] = ["mods", "resourcepacks", "shaderpacks", "datapacks", "nrc"];

export interface NavItem {
  key: NavKey;
  icon: string;
  labelKey: string;
  count?: number;
}

export interface NavGroup {
  labelKey: string;
  items: NavItem[];
}

interface ProfileLeftRailV3Props {
  profile: Profile;
  activeNavItem: NavKey;
  onNavChange: (k: NavKey) => void;
}

/** Reiter der Profilseite in zwei Gruppen – auch für Looks mit eigener Profilseite. */
export function buildProfileNavGroups(profile: Profile): NavGroup[] {
  const modCount = profile.mod_count ?? profile.mods?.length ?? 0;
  return [
    {
      labelKey: "profiles.tabs.content",
      items: [
        { key: "mods",          icon: "solar:bolt-bold-duotone",         labelKey: "profiles.content.mods",          count: modCount },
        { key: "resourcepacks", icon: "solar:gallery-bold-duotone",      labelKey: "profiles.content.resourcePacks" },
        { key: "shaderpacks",   icon: "solar:sun-bold-duotone",          labelKey: "profiles.content.shaderPacks" },
        { key: "datapacks",     icon: "solar:database-bold-duotone",     labelKey: "profiles.content.dataPacks" },
        { key: "nrc",           icon: "solar:shield-check-bold-duotone", labelKey: "profiles.content.noriskClient" },
      ],
    },
    {
      labelKey: "profiles.v3.leftRail.world",
      items: [
        { key: "worlds",      icon: "solar:planet-bold-duotone",      labelKey: "profiles.tabs.worlds" },
        { key: "screenshots", icon: "solar:camera-bold-duotone",      labelKey: "profiles.tabs.screenshots" },
        { key: "logs",        icon: "solar:code-square-bold-duotone", labelKey: "profiles.tabs.logs" },
      ],
    },
  ];
}

export function ProfileLeftRailV3({ profile, activeNavItem, onNavChange }: ProfileLeftRailV3Props) {
  const { t } = useTranslation();
  const groups = buildProfileNavGroups(profile);

  return (
    <aside
      className="w-60 flex-shrink-0 border-l overflow-y-auto no-scrollbar py-3 flex flex-col"
      style={{ borderColor: SURFACE.line }}
    >
      {groups.map((group, gi) => (
        <div key={gi} className="mb-4">
          <div className="px-5 mb-1.5 text-sm text-white/40 font-minecraft">
            {t(group.labelKey)}
          </div>
          <div className="px-3 space-y-1">
            {group.items.map((item) => {
              const active = item.key === activeNavItem;
              return (
                <NavButton
                  key={item.key}
                  active={active}
                  icon={item.icon}
                  label={t(item.labelKey)}
                  count={item.count}
                  onClick={() => onNavChange(item.key)}
                />
              );
            })}
          </div>
        </div>
      ))}
    </aside>
  );
}

interface NavButtonProps {
  icon: string;
  label: string;
  count?: number;
  active?: boolean;
  onClick: () => void;
}

// Salami: aktiver Reiter = volle Akzentfarbe mit harter Unterkante (wie ein Knopf), nicht die graue Pille
const NavButton: React.FC<NavButtonProps> = ({ icon, label, count, active, onClick }) => {
  const accent = useThemeStore((s) => s.accentColor);
  return (
    <button
      onClick={onClick}
      className={`group w-full flex items-center gap-3 px-3 h-9 text-left transition-[background-color,color] duration-150 ${
        active ? "" : "text-white/65 hover:text-white"
      }`}
      style={{
        borderRadius: "calc(var(--border-radius) * 0.8)",
        ...(active
          ? { backgroundColor: accent.value, color: readableOn(accent.value), boxShadow: kante(accent.value) }
          : {}),
      }}
      onMouseEnter={(e) => { if (!active) e.currentTarget.style.backgroundColor = SURFACE.hover; }}
      onMouseLeave={(e) => { if (!active) e.currentTarget.style.backgroundColor = "transparent"; }}
    >
      <Icon icon={icon} className="w-5 h-5 flex-shrink-0" />
      <span className="flex-1 font-minecraft text-base truncate">{label}</span>
      {typeof count === "number" && count > 0 && (
        <span
          className="min-w-[22px] px-1.5 py-0.5 text-center text-xs font-minecraft tabular-nums leading-none"
          style={{
            borderRadius: 999,
            backgroundColor: active ? "rgba(0,0,0,0.22)" : SURFACE.raised,
            color: active ? "inherit" : "rgba(255,255,255,0.6)",
          }}
        >
          {count}
        </span>
      )}
    </button>
  );
};
