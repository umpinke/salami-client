"use client";

import type { ButtonHTMLAttributes } from "react";
import { Icon } from "@iconify/react";

import { Tooltip } from "../ui/Tooltip";
import { cn } from "../../lib/utils";

interface Props extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  icon: string;
  label: string;
  tone?: "default" | "danger";
  tooltipPosition?: "top" | "bottom";
}

export function ClipIconButton({
  icon,
  label,
  tone = "default",
  tooltipPosition = "bottom",
  className,
  disabled,
  ...rest
}: Props) {
  return (
    <Tooltip content={label} position={tooltipPosition}>
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        className={cn(
          // Salami: wie die kleinen Knöpfe auf der Profil-Kachel – dunkles Feld ohne Rahmen
          "w-8 h-8 flex items-center justify-center bg-black/40 text-white/75 hover:text-white transition-colors duration-150",
          tone === "danger" ? "hover:bg-red-700/80" : "hover:bg-black/60",
          disabled && "cursor-not-allowed opacity-40",
          className,
        )}
        {...rest}
      >
        <Icon icon={icon} className="w-4 h-4" />
      </button>
    </Tooltip>
  );
}
