"use client";

import type React from "react";

import { cn } from "../../../lib/utils";
import { useThemeStore } from "../../../store/useThemeStore";

interface DropdownFooterProps {
  className?: string;
  children: React.ReactNode;
}

export function DropdownFooter({ className, children }: DropdownFooterProps) {
  const accentColor = useThemeStore((state) => state.accentColor);

  return (
    <div
      className={cn(
        "px-4 py-3 border-t-2 flex items-center justify-end gap-2",
        className,
      )}
      style={{
        backgroundColor: "rgba(255, 255, 255, 0.03)",
        borderTopColor: "rgba(255, 255, 255, 0.09)",
      }}
    >
      {children}
    </div>
  );
}
