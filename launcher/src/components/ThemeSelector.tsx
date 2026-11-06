"use client";

import { Icon } from "@iconify/react";
import { cn } from "../lib/utils";
import { useLauncherTheme } from "../hooks/useLauncherTheme";
import { LAUNCHER_THEMES } from "../store/launcher-theme-store";

interface ThemeSelectorProps {
  disabled?: boolean;
}

export function ThemeSelector({ disabled }: ThemeSelectorProps) {
  const { selectedThemeId, toggleTheme } = useLauncherTheme();
  const themes = Object.values(LAUNCHER_THEMES);

  return (
    <div className="flex flex-wrap gap-3">
      {themes.map((theme) => {
        const isSelected = selectedThemeId === theme.id;

        return (
          <button
            key={theme.id}
            onClick={() => {
              if (!disabled) {
                toggleTheme(theme.id);
              }
            }}
            disabled={disabled}
            className={cn(
              "relative flex items-center gap-3 px-4 py-3 rounded-lg border-2 transition-all duration-200",
              isSelected
                ? "border-white/60 bg-white/10"
                : "border-[#ffffff20] bg-black/20",
              disabled
                ? "opacity-40 cursor-not-allowed"
                : "hover:border-[#ffffff40] hover:bg-white/5 cursor-pointer"
            )}
          >
            <div
              className={cn(
                "w-8 h-8 rounded-md border-2 border-white/20 shadow-lg transition-transform",
                isSelected && "scale-105"
              )}
              style={{
                backgroundColor: theme.accentColor.value,
                boxShadow: isSelected ? `0 0 12px ${theme.accentColor.value}50` : undefined,
              }}
            />
            <div className="flex flex-col items-start">
              <span
                className={cn(
                  "font-minecraft text-base transition-colors",
                  isSelected ? "text-white" : "text-white/80"
                )}
              >
                {theme.name}
              </span>
            </div>
            {isSelected && (
              <Icon
                icon="solar:check-circle-bold"
                className="w-5 h-5 text-white absolute top-2 right-2"
              />
            )}
          </button>
        );
      })}
    </div>
  );
}
