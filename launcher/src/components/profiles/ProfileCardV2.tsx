"use client";

import type React from "react";
import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";

import type { Profile } from "../../types/profile";
import { ProfileIconV2 } from "./ProfileIconV2";
import { toast } from "react-hot-toast";
import { ProfileActionButtons, type ProfileActionButton } from "../ui/ProfileActionButtons";
import { SettingsContextMenu, type ContextMenuItem } from "../ui/SettingsContextMenu";
import { Icon } from "@iconify/react";
import { useProfileSettingsStore } from "../../store/profile-settings-store";
import { useProfileDuplicateStore } from "../../store/profile-duplicate-store";
import { useThemeStore } from "../../store/useThemeStore";
import { useGlobalModal } from "../../hooks/useGlobalModal";
import { ExportProfileModal } from "./ExportProfileModal";
import { useProfileLaunch } from "../../hooks/useProfileLaunch.tsx";
import { Tooltip } from "../ui/Tooltip";
import { useProfileStore } from "../../store/profile-store";
import { useMinecraftAuthStore } from "../../store/minecraft-auth-store";
import { usePlayerAvatar } from "../../hooks/usePlayerAvatar";
import { parseMotdToHtml } from "../../utils/motd-utils";
import { useTranslation } from "react-i18next";
import { usePinnedProfilesStore } from "../../store/usePinnedProfilesStore";
import { useResolvedLoaderVersion } from "../../hooks/useResolvedLoaderVersion";
import { loaderIconSrc } from "../../lib/loader-icons";
import { SURFACE, hexAlpha, kopfband, mixHex } from "../ui/design-system";
import { DeliLabel } from "../../themes/salami/SalamiStage";

// Custom JSX component for tooltip content
function StandardVersionTooltipContent() {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      <div className="text-left">
        <div className="text-sm leading-relaxed text-white">
          {t('profiles.card.standardVersionInfo')}
        </div>
      </div>
      <div className="flex items-start gap-2">
        <Icon icon="solar:lightbulb-bold" className="text-yellow-400 text-base flex-shrink-0" />
        <div className="text-gray-300 text-xs leading-snug italic">
          <span className="text-yellow-300 font-medium">{t('profiles.card.tip')}:</span> {t('profiles.card.createOwnProfiles')}
        </div>
      </div>
    </div>
  );
}

interface ProfileCardV2Props {
  profile: Profile;
  onPlay?: (profile: Profile) => void;
  onSettings?: (profile: Profile) => void;
  onMods?: (profile: Profile) => void;
  onDelete?: (profileId: string, profileName: string) => void;
  onOpenFolder?: (profile: Profile) => void;
  layoutMode?: "list" | "grid" | "compact";
  variant?: "default" | "3d";
}

export function ProfileCardV2({
  profile,
  onPlay,
  onSettings,
  onMods,
  onDelete,
  onOpenFolder,
  layoutMode = "list",
  variant = "default",
}: ProfileCardV2Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [isHovered, setIsHovered] = useState(false);
  const [modsButtonHovered, setModsButtonHovered] = useState(false);
  const accentColor = useThemeStore((state) => state.accentColor);
  const { openContextMenuId, setOpenContextMenuId } = useThemeStore();
  
  // Settings context menu state
  const [isContextMenuOpen, setIsContextMenuOpen] = useState(false);
  const contextMenuId = `profile-${profile.id}`;
  const [contextMenuPosition, setContextMenuPosition] = useState({ x: 0, y: 0 });
  const settingsButtonRef = useRef<HTMLButtonElement>(null);

  const { isPinned, togglePin } = usePinnedProfilesStore();
  const pinned = isPinned(profile.id);

  // Profile settings store
  const { openModal } = useProfileSettingsStore();
  
  // Profile duplicate store
  const { openModal: openDuplicateModal } = useProfileDuplicateStore();

  // Global modal system
  const { showModal, hideModal } = useGlobalModal();

  const resolvedLoaderVersion = useResolvedLoaderVersion(profile);

  // Get accounts from Minecraft Auth Store
  const accounts = useMinecraftAuthStore((state) => state.accounts);
  
  // Find preferred account if one is set
  const preferredAccount = profile.preferred_account_id 
    ? accounts.find(acc => acc.id === profile.preferred_account_id)
    : null;

  // Load preferred account avatar
  const preferredAccountAvatarUrl = usePlayerAvatar({
    uuid: preferredAccount?.id,
    overlay: true,
  });

  // Settings context menu items
  const contextMenuItems: ContextMenuItem[] = [
    {
      id: "edit",
      label: t('profiles.editProfile'),
      icon: "solar:settings-bold",
      onClick: (profile) => {
        console.log("Edit Profile clicked for:", profile.name);
        openModal(profile);
      },
    },
    {
      id: "duplicate",
      label: t('profiles.duplicate'),
      icon: "solar:copy-bold",
      onClick: (profile) => {
        console.log("Duplicate Profile clicked for:", profile.name);
        openDuplicateModal(profile);
      },
    },
    {
      id: "export",
      label: t('profiles.export'),
      icon: "solar:download-bold",
      onClick: (profile) => {
        showModal(`export-profile-${profile.id}`, (
          <ExportProfileModal
            profile={profile}
            isOpen={true}
            onClose={() => hideModal(`export-profile-${profile.id}`)}
          />
        ));
      },
    },
    {
      id: "open-folder",
      label: t('profiles.openFolder'),
      icon: "solar:folder-bold",
      onClick: (profile) => {
        if (onOpenFolder) {
          onOpenFolder(profile);
        } else {
          toast.success(t('profiles.toast.opening_folder', { name: profile.name }));
          console.log("Opening folder for profile:", profile.name);
        }
      },
    },
    ...(profile.modpack_info?.source ? [{
      id: "switch_modpack",
      label: t('profiles.modpackVersions'),
      icon: "solar:refresh-circle-bold",
      onClick: (profile) => {
        console.log("Switch modpack version for profile:", profile.name);
        if (profile.modpack_info?.source) {
          // Import ModpackVersionsModal dynamically to avoid circular imports
          import("../modals/ModpackVersionsModal").then(({ ModpackVersionsModal }) => {
            showModal(`modpack-versions-${profile.id}`, (
              <ModpackVersionsModal
                isOpen={true}
                onClose={() => hideModal(`modpack-versions-${profile.id}`)}
                versions={null}
                modpackName={profile.name}
                profileId={profile.id}
                onSwitchComplete={async () => {
                  console.log("Modpack version switched successfully for:", profile.name);
                  // Refresh profiles to ensure the profile prop is updated
                  try {
                    const { fetchProfiles } = useProfileStore.getState();
                    await fetchProfiles();
                  } catch (err) {
                    console.error("Failed to refresh profiles after modpack switch:", err);
                  }
                }}
              />
            ));
          });
        }
      },
    }] : []),
    {
      id: "delete",
      label: t('profiles.delete'),
      icon: "solar:trash-bin-trash-bold",
      destructive: true,
      separator: true, // Trennstrich vor Delete
      onClick: (profile) => {
        if (onDelete) {
          onDelete(profile.id, profile.name);
        } else {
          toast.error(t('profiles.toast.delete_fallback', { name: profile.name }));
          console.log("Deleting profile:", profile.name);
        }
      },
    },
  ];

  // Profile launch hook
  const { isLaunching, statusMessage, handleLaunch } = useProfileLaunch({
    profileId: profile.id,
    onLaunchSuccess: () => {
      console.log("Profile launched successfully:", profile.name);
    },
    onLaunchError: (error) => {
      console.error("Profile launch error:", error);
    },
    skipLastPlayedUpdate: variant === "3d", // Skip for featured profiles in 3D mode
  });



  // Close this menu if another context menu opens globally
  useEffect(() => {
    if (openContextMenuId && openContextMenuId !== contextMenuId && isContextMenuOpen) {
      setIsContextMenuOpen(false);
    }
  }, [openContextMenuId, contextMenuId, isContextMenuOpen]);







  // Get mod loader icon - reused from ProfileCard.tsx
  const getModLoaderIcon = () => loaderIconSrc(profile.loader);

  // Get 3D styling for variant (matching Button.tsx exactly)
  const get3DStyling = () => {
    if (variant !== "3d") return {};

    const colors = {
      main: accentColor.value,
      light: accentColor.hoverValue || accentColor.value,
      dark: accentColor.value,
    };

    const shadowDepth = "short";

    const backgroundColor = isHovered ? `${colors.main}47` : `${colors.main}29`;
    const borderColor = isHovered ? `${colors.main}80` : `${colors.main}47`;
    const borderBottomColor = borderColor;

    const part1Y = shadowDepth === "short" ? "4px" : "8px";
    const part2Y = shadowDepth === "short" ? "6px" : "10px";
    const part2Blur = shadowDepth === "short" ? "10px" : "15px";
    const boxShadow = "none";

    return {
      backgroundColor,
      border: "1px solid",
      borderBottom: "1px solid",
      borderColor,
      borderBottomColor,
      boxShadow,
      transition: "all 0.2s ease",
    };
  };

  // Get 3D styling for small buttons (settings/mods buttons)
  const get3DButtonStyling = (isHovered: boolean = false) => {
    if (variant !== "3d") return {};

    const colors = {
      main: accentColor.value,
      light: accentColor.hoverValue || accentColor.value,
      dark: accentColor.value,
    };

    const backgroundColor = isHovered ? "rgba(255, 255, 255, 0.11)" : "rgba(255, 255, 255, 0.08)";
    const borderColor = "rgba(255, 255, 255, 0.09)";
    const borderBottomColor = borderColor;

    const boxShadow = "none";

    return {
      backgroundColor,
      border: "1px solid",
      borderBottom: "1px solid",
      borderColor,
      borderBottomColor,
      boxShadow,
      color: "#ffffff",
      transform: isHovered ? "scale(1.05)" : "scale(1)",
      filter: isHovered ? "brightness(1.1)" : "brightness(1)",
      transition: "all 0.2s ease",
    };
  };

  // Format last played date
  const formatLastPlayed = (lastPlayed: string | null): string => {
    if (!lastPlayed) return t('profiles.card.neverPlayed');

    const date = new Date(lastPlayed);
    const now = new Date();
    const diffInMs = now.getTime() - date.getTime();
    const diffInMinutes = Math.floor(diffInMs / (1000 * 60));
    const diffInHours = Math.floor(diffInMinutes / 60);
    const diffInDays = Math.floor(diffInHours / 24);
    const diffInWeeks = Math.floor(diffInDays / 7);
    const diffInMonths = Math.floor(diffInDays / 30);
    const diffInYears = Math.floor(diffInDays / 365);

    if (diffInMinutes < 1) return t('profiles.card.justNow');
    if (diffInMinutes < 60) return t('profiles.card.minutesAgo', { count: diffInMinutes });
    if (diffInHours < 24) return t('profiles.card.hoursAgo', { count: diffInHours });
    if (diffInDays < 7) return t('profiles.card.daysAgo', { count: diffInDays });
    if (diffInWeeks < 4) return t('profiles.card.weeksAgo', { count: diffInWeeks });
    if (diffInMonths < 12) return t('profiles.card.monthsAgo', { count: diffInMonths });

    return t('profiles.card.yearsAgo', { count: diffInYears });
  };



  // Action button configuration
  const actionButtons: ProfileActionButton[] = [
    {
      id: "play",
      label: isLaunching ? t('profiles.stop') : t('profiles.play'),
      icon: isLaunching ? "solar:stop-bold" : "solar:play-bold",
      variant: isLaunching ? "destructive" : "primary",
      tooltip: isLaunching ? t('profiles.stopPlaying') : t('profiles.startPlaying'),
      onClick: (profile, e) => {
        if (onPlay) {
          onPlay(profile);
        } else {
          handleLaunch();
        }
      },
    },
    {
      id: "mods",
      label: "MODS",
      icon: "solar:box-bold",
      variant: "secondary",
      tooltip: t('profiles.manageMods'),
      onClick: (profile, e) => {
        if (onMods) {
          onMods(profile);
        } else {
          toast.success(t('profiles.toast.managing_mods', { name: profile.name }));
          console.log("Managing mods for profile:", profile.name);
        }
      },
    },
    {
      id: "settings",
      label: "SETTINGS",
      icon: "solar:settings-bold",
      variant: "icon-only",
      tooltip: t('profiles.profileOptions'),
             onClick: (profile, e) => {
         e.preventDefault();
         e.stopPropagation();
         
         // Close any other open context menus first
         if (openContextMenuId && openContextMenuId !== contextMenuId) {
           setOpenContextMenuId(null);
         }
         
         // Simple toggle like CustomDropdown
         const newState = !isContextMenuOpen;
         setIsContextMenuOpen(newState);
         setOpenContextMenuId(newState ? contextMenuId : null);
         
         // Calculate position when opening
         if (!isContextMenuOpen) {
           const buttonRect = e.currentTarget.getBoundingClientRect();
           const cardRect = e.currentTarget.closest('.relative')?.getBoundingClientRect();
           
           if (cardRect) {
             setContextMenuPosition({
               x: buttonRect.right - cardRect.left - 200, // Position menu to the left of the button
               y: buttonRect.bottom - cardRect.top + 4,   // Position below the button
             });
           }
         }
       },
    },
  ];

  const openContextMenuAt = (x: number, y: number) => {
    if (openContextMenuId && openContextMenuId !== contextMenuId) {
      setOpenContextMenuId(null);
    }
    setIsContextMenuOpen(true);
    setOpenContextMenuId(contextMenuId);
    setContextMenuPosition({ x, y });
  };

  // Salami: Kachel im Feinkost-Stil – Kopfband mit Salami-Muster, Etiketten, eigener Spielen-Knopf
  if (layoutMode === "grid" && variant === "default") {
    const loaderLabel =
      profile.loader === "vanilla"
        ? "Vanilla"
        : `${(profile.loader ?? "").charAt(0).toUpperCase()}${(profile.loader ?? "").slice(1)}`;
    return (
      <div
        className="look-card group relative flex cursor-pointer flex-col border transition-all duration-200"
        style={{
          backgroundColor: SURFACE.base,
          borderColor: isHovered ? hexAlpha(accentColor.value, 0.55) : SURFACE.line,
          borderRadius: "var(--border-radius)",
          transform: isHovered ? "translateY(-2px)" : undefined,
          boxShadow: isHovered ? `0 10px 24px -12px ${hexAlpha(accentColor.value, 0.6)}` : undefined,
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={(e) => {
          const target = e.target as Element;
          if (target.closest("button")) return;
          if (onMods) onMods(profile);
          else navigate(`/profilesv2/${profile.id}`);
        }}
        onContextMenu={(e) => {
          e.preventDefault();
          e.stopPropagation();
          const cardRect = e.currentTarget.getBoundingClientRect();
          openContextMenuAt(e.clientX - cardRect.left, e.clientY - cardRect.top);
        }}
      >
        {/* Kopfband: Salami-Anschnitt */}
        <div
          className="salami-fat-spots relative h-16"
          // eigene Rundung oben, seit die Karte nicht mehr abschneidet (sonst würde das „…“-Menü gekappt)
          data-card-band
          style={{
            ...kopfband(),
            borderTopLeftRadius: "calc(var(--border-radius) - 1px)",
            borderTopRightRadius: "calc(var(--border-radius) - 1px)",
          }}
        >
          {profile.is_standard_version && (
            <Tooltip content={<StandardVersionTooltipContent />}>
              <div className="absolute left-[92px] top-2.5 flex h-6 items-center gap-1 px-2 font-minecraft text-xs text-[#2b1d1a]" style={{ background: "#f3e3d3", borderRadius: 999 }}>
                <Icon icon="solar:star-bold" className="h-3.5 w-3.5" />
                Salami
              </div>
            </Tooltip>
          )}
          <div className="absolute right-2 top-2 z-20 flex gap-1">
            <button
              type="button"
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); togglePin(profile.id); }}
              className={`flex h-7 w-7 items-center justify-center bg-black/30 transition-colors hover:bg-black/50 ${pinned ? "text-white" : "text-white/60 hover:text-white"}`}
              style={{ borderRadius: "calc(var(--border-radius) * 0.6)" }}
              title={pinned ? t('profiles.card.unpin') : t('profiles.card.pin')}
              data-action="pin"
            >
              <Icon icon={pinned ? "solar:pin-bold" : "solar:pin-bold-duotone"} className="h-4 w-4" />
            </button>
            <button
              type="button"
              ref={settingsButtonRef}
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                if (isContextMenuOpen) {
                  setIsContextMenuOpen(false);
                  setOpenContextMenuId(null);
                  return;
                }
                const buttonRect = e.currentTarget.getBoundingClientRect();
                const cardRect = e.currentTarget.closest(".group")?.getBoundingClientRect();
                if (cardRect) openContextMenuAt(buttonRect.right - cardRect.left - 200, buttonRect.bottom - cardRect.top + 4);
              }}
              className="flex h-7 w-7 items-center justify-center bg-black/30 text-white/70 transition-colors hover:bg-black/50 hover:text-white"
              style={{ borderRadius: "calc(var(--border-radius) * 0.6)" }}
              title={t('profiles.profileOptions')}
              data-action="settings"
            >
              <Icon icon="solar:menu-dots-bold" className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Profilbild ragt ins Kopfband */}
        <div
          className="absolute left-4 top-7 z-10 h-16 w-16 overflow-hidden border-[3px]"
          style={{ borderColor: "#f3e3d3", backgroundColor: SURFACE.solid, borderRadius: "calc(var(--border-radius) * 0.8)" }}
        >
          <ProfileIconV2 profile={profile} size="lg" className="h-full w-full" />
        </div>

        <div className="flex flex-1 flex-col gap-2.5 px-4 pb-4 pt-9">
          <h3
            className="truncate font-minecraft text-xl normal-case leading-tight text-white"
            title={profile.name}
          >
            <span dangerouslySetInnerHTML={{ __html: parseMotdToHtml(profile.name) }} />
          </h3>
          <div className="flex flex-wrap items-center gap-1.5">
            <DeliLabel size="sm">{profile.game_version}</DeliLabel>
            <DeliLabel size="sm" tone="red">{loaderLabel}</DeliLabel>
            {preferredAccount && (
              <span className="flex items-center gap-1 text-xs text-white/55">
                {preferredAccountAvatarUrl && (
                  <img src={preferredAccountAvatarUrl} alt="" className="h-4 w-4 rounded-sm" style={{ imageRendering: "pixelated" }} />
                )}
                {preferredAccount.username}
              </span>
            )}
          </div>
          <div className="mt-auto flex items-center justify-between gap-3 pt-1">
            <span className="min-w-0 truncate text-xs text-white/45">
              {isLaunching ? (statusMessage || t('profiles.card.starting')) : formatLastPlayed(profile.last_played)}
            </span>
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                if (onPlay) onPlay(profile);
                else handleLaunch();
              }}
              className="look-btn look-btn--primary flex h-9 flex-shrink-0 items-center gap-1.5 px-4 font-minecraft text-base text-white transition-[filter] hover:brightness-110"
              style={{
                backgroundColor: isLaunching ? "#7a2425" : accentColor.value,
                borderRadius: "calc(var(--border-radius) * 0.8)",
                boxShadow: `0 3px 0 ${mixHex(accentColor.value, "#000000", 0.45)}`,
              }}
              title={isLaunching ? t('profiles.stopPlaying') : t('profiles.startPlaying')}
            >
              <Icon icon={isLaunching ? "solar:stop-bold" : "solar:play-bold"} className="h-4 w-4" />
              {isLaunching ? t('profiles.stop') : t('profiles.play')}
            </button>
          </div>
        </div>

        <SettingsContextMenu
          profile={profile}
          isOpen={isContextMenuOpen}
          position={contextMenuPosition}
          items={contextMenuItems}
          onClose={() => {
            setIsContextMenuOpen(false);
            setOpenContextMenuId(null);
          }}
          triggerButtonRef={settingsButtonRef}
        />
      </div>
    );
  }

  // Salami: Liste und Kompakt im Feinkost-Stil – Zeile/kleine Kachel wie die Profil-Kachel
  // (Creme-Rahmen ums Profilbild, Etiketten, eigener Spielen-Knopf mit harter Unterkante)
  if (variant === "default" && (layoutMode === "list" || layoutMode === "compact")) {
    const isCompact = layoutMode === "compact";
    const loaderLabel =
      profile.loader === "vanilla"
        ? "Vanilla"
        : `${(profile.loader ?? "").charAt(0).toUpperCase()}${(profile.loader ?? "").slice(1)}`;
    const loaderVersion =
      profile.loader !== "vanilla"
        ? (resolvedLoaderVersion?.version || profile.loader_version || "").replace(/\s*\(stable\)\s*$/i, "")
        : "";
    const smallButton = `flex flex-shrink-0 items-center justify-center text-white/60 transition-colors hover:bg-white/10 hover:text-white ${isCompact ? "h-7 w-7" : "h-8 w-8"}`;
    return (
      <div
        className={`group relative flex cursor-pointer items-center border transition-all duration-200 ${isCompact ? "gap-3 p-2.5" : "gap-4 p-3"}`}
        style={{
          backgroundColor: isHovered ? SURFACE.hover : SURFACE.base,
          borderColor: isHovered ? hexAlpha(accentColor.value, 0.55) : SURFACE.line,
          borderRadius: "var(--border-radius)",
          transform: isHovered ? "translateY(-2px)" : undefined,
          boxShadow: isHovered ? `0 10px 24px -14px ${hexAlpha(accentColor.value, 0.6)}` : undefined,
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={(e) => {
          const target = e.target as Element;
          if (target.closest("button")) return;
          if (onMods) onMods(profile);
          else navigate(`/profilesv2/${profile.id}`);
        }}
        onContextMenu={(e) => {
          e.preventDefault();
          e.stopPropagation();
          const cardRect = e.currentTarget.getBoundingClientRect();
          openContextMenuAt(e.clientX - cardRect.left, e.clientY - cardRect.top);
        }}
      >
        <div className="relative flex-shrink-0">
          <div
            className={`overflow-hidden ${isCompact ? "h-12 w-12 border-2" : "h-14 w-14 border-[3px]"}`}
            style={{ borderColor: "#f3e3d3", backgroundColor: SURFACE.solid, borderRadius: "calc(var(--border-radius) * 0.8)" }}
          >
            <ProfileIconV2 profile={profile} size="md" className="h-full w-full" />
          </div>
          {profile.is_standard_version && (
            <div className="absolute -right-1.5 -top-1.5 z-10">
              <Tooltip content={<StandardVersionTooltipContent />}>
                <div
                  className="flex h-5 w-5 items-center justify-center text-[#2b1d1a]"
                  style={{ background: "#f3e3d3", borderRadius: 999, boxShadow: "0 2px 0 rgba(0,0,0,0.35)" }}
                >
                  <Icon icon="solar:star-bold" className="h-3 w-3" />
                </div>
              </Tooltip>
            </div>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-1.5">
            <h3
              className={`truncate font-minecraft normal-case leading-tight text-white ${isCompact ? "text-base" : "text-lg"}`}
              title={profile.name}
            >
              <span dangerouslySetInnerHTML={{ __html: parseMotdToHtml(profile.name) }} />
            </h3>
            {isCompact && (pinned || isHovered) && (
              <button
                type="button"
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); togglePin(profile.id); }}
                className={`flex-shrink-0 transition-colors ${pinned ? "text-white" : "text-white/40 hover:text-white"}`}
                title={pinned ? t('profiles.card.unpin') : t('profiles.card.pin')}
                data-action="pin"
              >
                <Icon icon={pinned ? "solar:pin-bold" : "solar:pin-bold-duotone"} className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
          <div className="mt-1.5 flex min-w-0 items-center gap-1.5">
            {isLaunching ? (
              <span className="truncate text-xs text-white/55">{statusMessage || t('profiles.card.starting')}</span>
            ) : (
              <>
                <DeliLabel size="sm">{profile.game_version}</DeliLabel>
                {!isCompact && (
                  <DeliLabel size="sm" tone="red">
                    {loaderLabel}{loaderVersion ? ` ${loaderVersion}` : ""}
                  </DeliLabel>
                )}
                <span className="min-w-0 truncate text-xs text-white/45">{formatLastPlayed(profile.last_played)}</span>
              </>
            )}
          </div>
        </div>

        {!isCompact && preferredAccount && (
          <Tooltip content={t('profiles.launchWith', { account: preferredAccount.username })}>
            <span className="flex flex-shrink-0 items-center gap-1.5 text-sm text-white/55">
              {preferredAccountAvatarUrl && (
                <img src={preferredAccountAvatarUrl} alt="" className="h-5 w-5 rounded-sm" style={{ imageRendering: "pixelated" }} />
              )}
              <span className="max-w-[120px] truncate">{preferredAccount.username}</span>
            </span>
          </Tooltip>
        )}

        <div className={`flex flex-shrink-0 items-center gap-1 transition-opacity duration-150 ${isHovered || isContextMenuOpen || (!isCompact && pinned) ? "opacity-100" : "opacity-0"}`}>
          {!isCompact && (
            <button
              type="button"
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); togglePin(profile.id); }}
              className={`${smallButton} ${pinned ? "!text-white" : ""}`}
              style={{ borderRadius: "calc(var(--border-radius) * 0.6)" }}
              title={pinned ? t('profiles.card.unpin') : t('profiles.card.pin')}
              data-action="pin"
            >
              <Icon icon={pinned ? "solar:pin-bold" : "solar:pin-bold-duotone"} className="h-4 w-4" />
            </button>
          )}
          <button
            type="button"
            ref={settingsButtonRef}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (isContextMenuOpen) {
                setIsContextMenuOpen(false);
                setOpenContextMenuId(null);
                return;
              }
              const buttonRect = e.currentTarget.getBoundingClientRect();
              const cardRect = e.currentTarget.closest(".group")?.getBoundingClientRect();
              if (cardRect) openContextMenuAt(buttonRect.right - cardRect.left - 200, buttonRect.bottom - cardRect.top + 4);
            }}
            className={smallButton}
            style={{ borderRadius: "calc(var(--border-radius) * 0.6)" }}
            title={t('profiles.profileOptions')}
            data-action="settings"
          >
            <Icon icon="solar:menu-dots-bold" className="h-4 w-4" />
          </button>
        </div>

        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            if (onPlay) onPlay(profile);
            else handleLaunch();
          }}
          className={`flex flex-shrink-0 items-center justify-center gap-1.5 font-minecraft text-white transition-[filter,transform] hover:brightness-110 active:translate-y-px ${isCompact ? "h-9 w-9" : "h-10 px-4 text-base"}`}
          style={{
            backgroundColor: isLaunching ? "#7a2425" : accentColor.value,
            borderRadius: "calc(var(--border-radius) * 0.8)",
            boxShadow: `0 3px 0 ${mixHex(accentColor.value, "#000000", 0.45)}`,
          }}
          title={isLaunching ? t('profiles.stopPlaying') : t('profiles.startPlaying')}
        >
          <Icon icon={isLaunching ? "solar:stop-bold" : "solar:play-bold"} className="h-4 w-4" />
          {!isCompact && (isLaunching ? t('profiles.stop') : t('profiles.play'))}
        </button>

        <SettingsContextMenu
          profile={profile}
          isOpen={isContextMenuOpen}
          position={contextMenuPosition}
          items={contextMenuItems}
          onClose={() => {
            setIsContextMenuOpen(false);
            setOpenContextMenuId(null);
          }}
          triggerButtonRef={settingsButtonRef}
        />
      </div>
    );
  }

    // Grid layout (more compact, similar to ProfileCard.tsx)
  if (layoutMode === "grid" || layoutMode === "compact") {
    const isCompact = layoutMode === "compact";
    const iconSize = isCompact ? 16 : 20; // Smaller icons for compact mode
    const padding = isCompact ? "p-3" : "p-4"; // Less padding for compact mode
    const gap = isCompact ? "gap-2" : "gap-3"; // Smaller gaps for compact mode
    
    return (
      <div
        className={`relative flex flex-col ${gap} ${padding} rounded-lg ${variant === "3d" ? "backdrop-blur-md" : "bg-black/20 border border-white/10 hover:border-white/20"} transition-all duration-200 cursor-pointer`}
        style={variant === "3d" ? get3DStyling() : {}}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={(e) => {
          // Don't trigger if clicking on action buttons or play overlay
          const target = e.target as Element;
          if (e.target === e.currentTarget || (!target.closest('button') && !target.closest('.play-overlay'))) {
            if (variant === "3d") {
              // In 3D mode, launch the profile when clicking the card
              if (onPlay) {
                onPlay(profile);
              } else {
                handleLaunch();
              }
            } else {
              // Default behavior: manage mods
              if (onMods) {
                onMods(profile);
              } else {
                toast.success(t('profiles.toast.managing_mods', { name: profile.name }));
                console.log("Managing mods for profile:", profile.name);
              }
            }
          }
        }}
        onContextMenu={(e) => {
          e.preventDefault();
          e.stopPropagation();

          if (openContextMenuId && openContextMenuId !== contextMenuId) {
            setOpenContextMenuId(null);
          }
          setIsContextMenuOpen(true);
          setOpenContextMenuId(contextMenuId);

          const cardRect = e.currentTarget.getBoundingClientRect();
          setContextMenuPosition({
            x: e.clientX - cardRect.left,
            y: e.clientY - cardRect.top,
          });
        }}
      >
        {/* Standard version badge */}
        {profile.is_standard_version && (
          <div className={`absolute ${isCompact ? 'top-2 left-2' : 'top-3 left-3'} z-20`}>
            <Tooltip content={<StandardVersionTooltipContent />}>
              <div className="flex items-center justify-center w-6 h-6 rounded-full ">
                <Icon icon="solar:star-bold" className="w-4 h-4 text-yellow-400" />
              </div>
            </Tooltip>
          </div>
        )}

        <div className={`absolute ${isCompact ? 'top-2 right-2' : 'top-3 right-3'} z-20 flex flex-col gap-1`}>
          {variant === "default" && (
            <button
            ref={settingsButtonRef}
                         onClick={(e) => {
               e.preventDefault();
               e.stopPropagation();

               // Close any other open context menus first
               if (openContextMenuId && openContextMenuId !== contextMenuId) {
                 setOpenContextMenuId(null);
               }

               // Simple toggle like CustomDropdown
               const newState = !isContextMenuOpen;
               setIsContextMenuOpen(newState);
               setOpenContextMenuId(newState ? contextMenuId : null);

               // Calculate position when opening
               if (!isContextMenuOpen) {
                 const buttonRect = e.currentTarget.getBoundingClientRect();
                 const cardRect = e.currentTarget.closest('.relative')?.getBoundingClientRect();

                 if (cardRect) {
                   setContextMenuPosition({
                     x: buttonRect.right - cardRect.left - 200, // Position menu to the left of the button
                     y: buttonRect.bottom - cardRect.top + 4,   // Position below the button
                   });
                 }
               }
             }}
            className={`${isCompact ? 'w-6 h-6' : 'w-8 h-8'} flex items-center justify-center rounded transition-all duration-200 bg-black/30 hover:bg-black/50 text-white/70 hover:text-white border border-white/10 hover:border-white/20`}
            title={t('profiles.profileOptions')}
            data-action="settings"
          >
            <Icon icon="solar:settings-bold" className={isCompact ? 'w-3 h-3' : 'w-4 h-4'} />
          </button>
          )}

          {/* Mods button */}
          <button
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (onMods) {
                onMods(profile);
              } else {
                // Navigate to specific profile detail view for mod management
                navigate(`/profilesv2/${profile.id}`);
              }
            }}
            className={`${variant === "3d" ? (isCompact ? 'w-auto px-2 h-6' : 'w-auto px-3 h-8') : (isCompact ? 'w-6 h-6' : 'w-8 h-8')} flex items-center justify-center gap-1 rounded transition-all duration-200 ${variant === "3d" ? "" : "bg-black/30 hover:bg-black/50 text-white/70 hover:text-white border border-white/10 hover:border-white/20"}`}
            style={variant === "3d" ? get3DButtonStyling(modsButtonHovered) : {}}
            onMouseEnter={() => setModsButtonHovered(true)}
            onMouseLeave={() => setModsButtonHovered(false)}
            title={t('profiles.manageMods')}
          >
            <Icon icon="solar:box-bold" className={isCompact ? 'w-3 h-3' : 'w-4 h-4'} />
            {variant === "3d" && (
              <span className={`font-minecraft ${isCompact ? 'text-xs' : 'text-sm'}`}>{t('profiles.content.mods')}</span>
            )}
          </button>
        </div>

        <div className={`flex items-center ${isCompact ? 'gap-3' : 'gap-4'} relative z-10 w-full`}>
          <div className={`relative ${isCompact ? 'w-16 h-16' : 'w-20 h-20'} flex-shrink-0 rounded-lg flex items-center justify-center overflow-hidden border-2 transition-all duration-200`}
            style={{
              backgroundColor: isHovered ? `${accentColor.value}20` : 'transparent',
              borderColor: isHovered ? `${accentColor.value}60` : 'transparent',
            }}
          >
            <ProfileIconV2 profile={profile} size={isCompact ? "md" : "lg"} className="w-full h-full" />
            
            {/* Play button overlay - similar to ProfileCard.tsx */}
            {(isLaunching || isHovered) && (
              <div className="play-overlay absolute inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm transition-opacity duration-150 cursor-pointer rounded-lg">
                <button
                  onClick={() => handleLaunch()}
                  className={`${isCompact ? 'w-8 h-8' : 'w-12 h-12'} flex items-center justify-center text-white hover:text-white/80 transition-colors`}
                  disabled={false}
                >
                  {isLaunching ? (
                    <Icon icon="solar:stop-bold" className={isCompact ? 'w-6 h-6' : 'w-8 h-8'} />
                  ) : (
                    <Icon icon="solar:play-bold" className={isCompact ? 'w-6 h-6' : 'w-8 h-8'} />
                  )}
                </button>
              </div>
            )}
          </div>

          <div className={`flex-grow min-w-0 mr-auto pr-2 ${isCompact ? 'max-w-[calc(100%-64px)]' : 'max-w-[calc(100%-80px)]'}`}>
            <div className="flex items-center gap-2 mb-0.5">
              <h3
                className={`font-minecraft text-white ${isCompact ? 'text-base' : 'text-lg'} whitespace-nowrap overflow-hidden text-ellipsis normal-case`}
                style={{ textShadow: '0 2px 4px rgba(0,0,0,0.7)' }}
                title={profile.name}
              >
                <span dangerouslySetInnerHTML={{ __html: parseMotdToHtml(profile.name) }} />
              </h3>
              {(pinned || isHovered) && (
                <button
                  onClick={(e) => { e.preventDefault(); e.stopPropagation(); togglePin(profile.id); }}
                  className={`flex-shrink-0 transition-all duration-200 ${pinned ? 'text-white' : 'text-white/40 hover:text-white'}`}
                  title={pinned ? t('profiles.card.unpin') : t('profiles.card.pin')}
                  data-action="pin"
                >
                  <Icon icon={pinned ? "solar:pin-bold" : "solar:pin-bold-duotone"} className="w-4 h-4" />
                </button>
              )}
              {preferredAccount && (
                <Tooltip content={t('profiles.launchWith', { account: preferredAccount.username })}>
                  <div className="flex items-center gap-1.5 text-white/60">
                    {preferredAccountAvatarUrl && (
                      <img
                        src={preferredAccountAvatarUrl}
                        alt={preferredAccount.username}
                        className={`${isCompact ? 'w-4 h-4' : 'w-5 h-5'} rounded-sm pixelated flex-shrink-0`}
                        style={{ imageRendering: 'pixelated' }}
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                    )}
                    <span className={`truncate max-w-[100px] ${isCompact ? 'text-base' : 'text-lg'} lowercase`}>{preferredAccount.username}</span>
                  </div>
                </Tooltip>
              )}
            </div>
            {isLaunching ? (
              <div
                className="text-white/60 text-xs font-minecraft opacity-70 whitespace-nowrap overflow-hidden text-ellipsis max-w-full"
                style={{ textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}
              >
                {statusMessage || t('profiles.card.starting')}
              </div>
            ) : (
              isCompact ? (
                 // Compact mode: Only MC version + last played
                 <div className="flex items-center gap-1.5 text-xs font-minecraft" style={{ textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}>
                   {/* Minecraft Version */}
                   <div className="text-white/70 flex items-center gap-0.5">
                     <img
                       src="/icons/minecraft.png"
                       alt="Minecraft"
                       className="w-2.5 h-2.5 object-contain"
                     />
                     <span>{profile.game_version}</span>
                   </div>

                   <div className="w-px h-2.5 bg-white/30"></div>

                   {/* Last Played */}
                   <div className="text-white/50">
                     {formatLastPlayed(profile.last_played)}
                   </div>
                 </div>
               ) : (
                 // Grid mode: Full info display
                 <div className="flex items-center gap-2 text-xs font-minecraft" style={{ textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}>
                   {/* Minecraft Version */}
                   <div className="text-white/70 flex items-center gap-1">
                     <img
                       src="/icons/minecraft.png"
                       alt="Minecraft"
                       className="w-3 h-3 object-contain"
                     />
                     <span>{profile.game_version}</span>
                   </div>
                   
                   <div className="w-px h-3 bg-white/30"></div>
                   
                   {/* Loader Version */}
                   <div className="text-white/60 flex items-center gap-1">
                     <img
                       src={getModLoaderIcon()}
                       alt={profile.loader || t('common.vanilla')}
                       className="w-3 h-3 object-contain"
                     />
                     <span>
                       {profile.loader === "vanilla" 
                         ? "Vanilla" 
                         : `${resolvedLoaderVersion?.version || profile.loader_version || t('common.unknown')}`
                       }
                     </span>
                   </div>
                   
                   <div className="w-px h-3 bg-white/30"></div>
                   
                   {/* Last Played */}
                   <div className="text-white/50">
                     {formatLastPlayed(profile.last_played)}
                   </div>
                 </div>
               )
            )}
          </div>
        </div>

        {/* Settings Context Menu */}
                 <SettingsContextMenu
           profile={profile}
           isOpen={isContextMenuOpen}
           position={contextMenuPosition}
           items={contextMenuItems}
           onClose={() => {
             setIsContextMenuOpen(false);
             setOpenContextMenuId(null);
           }}
           triggerButtonRef={settingsButtonRef}
         />
      </div>
    );
  }

  // List layout (original layout)
  return (
    <div
      className={`relative flex items-center gap-4 p-3 rounded-lg ${variant === "3d" ? "backdrop-blur-md" : "bg-black/20 border border-white/10 hover:border-white/20"} transition-all duration-200 cursor-pointer`}
      style={variant === "3d" ? get3DStyling() : {}}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={(e) => {
        // Don't trigger if clicking on action buttons
        const target = e.target as Element;
        if (e.target === e.currentTarget || !target.closest('button')) {
          if (variant === "3d") {
            // In 3D mode, launch the profile when clicking the card
            if (onPlay) {
              onPlay(profile);
            } else {
              handleLaunch();
            }
          } else {
            // Default behavior: manage mods
            if (onMods) {
              onMods(profile);
            } else {
              toast.success(t('profiles.toast.managing_mods', { name: profile.name }));
              console.log("Managing mods for profile:", profile.name);
            }
          }
        }
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        e.stopPropagation();

        if (openContextMenuId && openContextMenuId !== contextMenuId) {
          setOpenContextMenuId(null);
        }
        setIsContextMenuOpen(true);
        setOpenContextMenuId(contextMenuId);

        const cardRect = e.currentTarget.getBoundingClientRect();
        setContextMenuPosition({
          x: e.clientX - cardRect.left,
          y: e.clientY - cardRect.top,
        });
      }}
    >
      {/* Profile Icon */}
      <div className="relative">
        <ProfileIconV2 profile={profile} size="md" />
        {profile.is_standard_version && (
          <div className="absolute -top-1 -right-1 z-10">
            <Tooltip content={<StandardVersionTooltipContent />}>
              <div className="flex items-center justify-center w-6 h-6 rounded-full ">
                <Icon icon="solar:star-bold" className="w-4 h-4 text-yellow-400" />
              </div>
            </Tooltip>
          </div>
        )}
      </div>

      {/* Profile Info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <h3
            className="text-white font-minecraft text-sm whitespace-nowrap overflow-hidden text-ellipsis normal-case"
            style={{ textShadow: '0 2px 4px rgba(0,0,0,0.7)' }}
            title={profile.name}
          >
            <span dangerouslySetInnerHTML={{ __html: parseMotdToHtml(profile.name) }} />
          </h3>
          {(pinned || isHovered) && (
            <button
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); togglePin(profile.id); }}
              className={`flex-shrink-0 transition-all duration-200 ${pinned ? 'text-white' : 'text-white/40 hover:text-white'}`}
              title={pinned ? t('profiles.card.unpin') : t('profiles.card.pin')}
              data-action="pin"
            >
              <Icon icon={pinned ? "solar:pin-bold" : "solar:pin-bold-duotone"} className="w-4 h-4" />
            </button>
          )}
          {preferredAccount && (
            <Tooltip content={t('profiles.launchWith', { account: preferredAccount.username })}>
              <div className="flex items-center gap-1.5 text-white/60">
                {preferredAccountAvatarUrl && (
                  <img
                    src={preferredAccountAvatarUrl}
                    alt={preferredAccount.username}
                    className="w-5 h-5 rounded-sm pixelated flex-shrink-0"
                    style={{ imageRendering: 'pixelated' }}
                    onError={(e) => {
                      e.currentTarget.style.display = 'none';
                    }}
                  />
                )}
                <span className="truncate max-w-[100px] text-lg lowercase">{preferredAccount.username}</span>
              </div>
            </Tooltip>
          )}
        </div>
        
        {isLaunching ? (
          <div
            className="text-white/60 text-xs font-minecraft opacity-70 whitespace-nowrap overflow-hidden text-ellipsis max-w-full"
            style={{ textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}
          >
            {statusMessage || t('profiles.card.starting')}
          </div>
        ) : (
          <div className="flex items-center gap-2 text-xs font-minecraft" style={{ textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}>
          {/* Minecraft Version */}
          <div className="text-white/70 flex items-center gap-1">
            <img
              src="/icons/minecraft.png"
              alt="Minecraft"
              className="w-3 h-3 object-contain"
            />
            <span>{profile.game_version}</span>
          </div>
          
          <div className="w-px h-3 bg-white/30"></div>
          
          {/* Loader Version */}
          <div className="text-white/60 flex items-center gap-1">
            <img
              src={getModLoaderIcon()}
              alt={profile.loader || t('common.vanilla')}
              className="w-3 h-3 object-contain"
            />
            <span>
              {profile.loader === "vanilla"
                ? t('common.vanilla')
                : `${resolvedLoaderVersion?.version || profile.loader_version || t('common.unknown')}`
              }
            </span>
          </div>
          
          <div className="w-px h-3 bg-white/30"></div>
          
          {/* Last Played */}
          <div className="text-white/50">
            {formatLastPlayed(profile.last_played)}
          </div>
        </div>
        )}
      </div>

      {/* Action Buttons */}
      <ProfileActionButtons
        profile={profile}
        actions={actionButtons}
        useFlexSpacer={true}
        flexSpacerAfterIndex={1}
      />

             {/* Settings Context Menu */}
       <SettingsContextMenu
         profile={profile}
         isOpen={isContextMenuOpen}
         position={contextMenuPosition}
         items={contextMenuItems}
         onClose={() => {
           setIsContextMenuOpen(false);
           setOpenContextMenuId(null);
         }}
         triggerButtonRef={undefined} // List layout doesn't have direct button ref
       />


    </div>
  );
}
