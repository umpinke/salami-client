"use client";

import { cn } from "../../../lib/utils";
import { useThemeStore } from "../../../store/useThemeStore";

interface DropdownDividerProps {
  className?: string;
}

export function DropdownDivider({ className }: DropdownDividerProps) {
  const accentColor = useThemeStore((state) => state.accentColor);

  return (
    <div
      className={cn("h-px w-full my-1", className)}
      style={{ backgroundColor: "rgba(255, 255, 255, 0.09)" }}
    />
  );
}
