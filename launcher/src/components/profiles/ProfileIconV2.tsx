"use client";

import type React from "react";
import { useEffect, useState } from "react";
import type { Profile } from "../../types/profile";
import { ProfileIcon } from "./ProfileIcon";
import { useThemeStore } from "../../store/useThemeStore";
import { SalamiSlice } from "../../themes/salami/SalamiSlice";
import { SALAMI_COLORS } from "../../themes/salami/salami-theme";

interface ProfileIconV2Props {
  profile: Profile;
  size?: "sm" | "md" | "lg";
  className?: string;
}

export function ProfileIconV2({
  profile,
  size = "md",
  className = "",
}: ProfileIconV2Props) {
  const accentColor = useThemeStore((state) => state.accentColor);

  const sizeClasses = {
    sm: "w-12 h-12",
    md: "w-16 h-16", 
    lg: "w-20 h-20",
  };

  const letterSizes = {
    sm: "text-lg",
    md: "text-2xl",
    lg: "text-3xl",
  };

  const iconSizes = {
    sm: "w-6 h-6",
    md: "w-8 h-8",
    lg: "w-10 h-10",
  };

  // Salami: ohne eigenes Bild eine Salamischeibe mit dem Anfangsbuchstaben statt NoRisks Paket-Symbol
  if (!profile.banner) {
    const letter = profile.name.replace(/§./g, "").trim().charAt(0).toUpperCase() || "S";
    return (
      <div
        className={`${sizeClasses[size]} relative flex items-center justify-center overflow-hidden ${className}`}
        style={{ backgroundColor: SALAMI_COLORS.pepper }}
      >
        <SalamiSlice size={64} className="absolute inset-0 m-auto h-[88%] w-[88%]" />
        <span
          className={`relative font-minecraft leading-none ${letterSizes[size]}`}
          style={{ color: SALAMI_COLORS.fat, textShadow: "0 2px 0 rgba(43,29,26,0.85), 0 0 6px rgba(43,29,26,0.7)" }}
        >
          {letter}
        </span>
      </div>
    );
  }

  return (
    <div
      className={`${sizeClasses[size]} rounded-lg border flex items-center justify-center overflow-hidden ${className}`}
      style={{
        backgroundColor: `${accentColor.value}1f`,
        borderColor: `${accentColor.value}33`,
      }}
    >
      <ProfileIcon
        profileId={profile.id}
        banner={profile.banner}
        profileName={profile.name}
        accentColor={accentColor.value}
        onSuccessfulUpdate={() => {}}
        isEditable={false}
        variant="bare"
        className="w-full h-full"
        placeholderIcon="ph:package-duotone"
        iconClassName={iconSizes[size]}
      />
    </div>
  );
}
