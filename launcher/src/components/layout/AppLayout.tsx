"use client";

import { selectFriendsBadge, useFriendsStore } from "../../features/friends/friends-store";
import "../../features/friends/i18n";
import type React from "react";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { gsap } from "gsap";
import { Icon } from "@iconify/react";

import type { TopNavItem } from ".././navigation/TopNav";
import { SalamiBackdrop } from "../effects/SalamiBackdrop";
import { mixHex, SURFACE } from "../ui/design-system";
import { Logo } from "../ui/Logo";
import { CreditsModal } from "../modals/CreditsModal";
import { UserProfileBar } from ".././header/UserProfileBar";
import { NavigationHistory } from "../ui/NavigationHistory";
import { useThemeStore } from "../../store/useThemeStore";
import {
  BACKGROUND_EFFECTS,
  useBackgroundEffectStore,
} from "../../store/background-effect-store";
import { useQualitySettingsStore } from "../../store/quality-settings-store";
import { MatrixRainEffect } from ".././effects/MatrixRainEffect";
import { EnchantmentParticlesEffect } from ".././effects/EnchantmentParticlesEffect";
import { NebulaWaves } from ".././effects/NebulaWaves";
import { NebulaParticles } from ".././effects/NebulaParticles";
import { NebulaGrid } from ".././effects/NebulaGrid";
import { NebulaVoxels } from ".././effects/NebulaVoxels";
import { NebulaLightning } from ".././effects/NebulaLightning";
import { NebulaLiquidChrome } from ".././effects/NebulaLiquidChrome";
import PlainBackground from "../effects/PlainBackground";
import CustomMediaBackground from "../effects/CustomMediaBackground";
import { Snowfall } from "../../features/snow-effect/Snowfall";
import { useSnowEffectStore } from "../../store/snow-effect-store";
import { useLauncherTheme } from "../../hooks/useLauncherTheme";
import * as ConfigService from "../../services/launcher-config-service";
import { BRAND } from "../../config/salami";
import { SalamiFall } from "../../themes/salami/SalamiFall";
import { SALAMI_THEME_ID } from "../../themes/salami/salami-theme";
import { useClipsStore } from "../../store/clips-store";
import { checkUpdateAvailable, downloadAndInstallUpdate } from "../../services/nrc-service";
import type { UpdateInfo } from "../../types/updater";
import { ProfileWizardV2Modal } from "../modals/ProfileWizardV2Modal";
import { useLook } from "../../themes/looks/registry";
import { ProfileSettingsModal } from "../modals/ProfileSettingsModal";
import { SettingsModal } from "../modals/SettingsModal";
import { ProfileDuplicateModal } from "../modals/ProfileDuplicateModal";
import { exit } from '@tauri-apps/plugin-process';
import { Tooltip } from "../ui/Tooltip";
import { toast } from 'react-hot-toast';
import { useTranslation } from "react-i18next";
import { parseErrorMessage } from "../../utils/error-utils";

const appConfig = {
  version: "v0.5.22",
};

interface AppLayoutProps {
  children: ReactNode;
  activeTab: string;
  onNavChange: (tabId: string) => void;
}

export function AppLayout({
  children,
  activeTab,
  onNavChange,
}: AppLayoutProps) {
  const { t } = useTranslation();
  const launcherRef = useRef<HTMLDivElement>(null);
  const backgroundPatternRef = useRef<HTMLDivElement>(null);
  const minimizeRef = useRef<HTMLDivElement>(null);
  const maximizeRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLDivElement>(null);
  const { currentEffect, customMediaUrl, customMediaOnlyOnPlay, customMediaHideEffects } = useBackgroundEffectStore();
  const isCustomMediaVisible = Boolean(customMediaUrl) && (!customMediaOnlyOnPlay || activeTab === 'play');
  const shouldShowEffects = !(isCustomMediaVisible && customMediaHideEffects);

  const clipsEnabled = useClipsStore((state) => state.enabled);
  const refreshClips = useClipsStore((state) => state.refresh);

  useEffect(() => {
    void refreshClips();
  }, [refreshClips]);

  // Freunde: Zustand einmal laden, damit der Zähler auch ohne offenen Reiter stimmt.
  const initFriends = useFriendsStore((state) => state.init);
  const friendsBadge = useFriendsStore(selectFriendsBadge);
  useEffect(() => {
    void initFriends();
  }, [initFriends]);

  const navItems: TopNavItem[] = [
    { id: "play", icon: "solar:play-bold", label: t("nav.play") },
    { id: "profiles", icon: "solar:user-id-bold", label: t("nav.profiles") },
    { id: "mods", icon: "solar:widget-bold", label: t("nav.mods") },
    { id: "skins", icon: "solar:emoji-funny-circle-bold", label: t("nav.skins") },
    // Shop (früher „Cosmetics“): Route bleibt /capes
    { id: "capes", icon: "solar:shop-bold", label: t("nav.capes") },
    {
      id: "friends",
      icon: "solar:users-group-rounded-bold",
      // ungelesene Nachrichten und offene Anfragen stehen hinter dem Namen
      label: friendsBadge > 0 ? `${t("nav.friends")} · ${friendsBadge}` : t("nav.friends"),
    },
    ...(clipsEnabled
      ? [{ id: "clips", icon: "solar:videocamera-record-bold", label: t("nav.clips") }]
      : []),
    { id: "settings", icon: "solar:settings-bold", label: t("nav.settings"), isAction: true },
  ];
  const { qualityLevel } = useQualitySettingsStore();
  const { isBackgroundAnimationEnabled, accentColor: themeAccentColor, accentColor } = useThemeStore();
  const { isEnabled: isSnowEnabled } = useSnowEffectStore();
  const { selectedTheme, isThemeActive } = useLauncherTheme();
  const look = useLook();
  // Salami: warmes Pfefferbraun als Grund statt neutralem Schwarz
  const getComplementaryBackground = () => mixHex("#15100e", themeAccentColor.value, 0.04);

  const getComplementaryBackgroundWithAlpha = (alpha: number) =>
    getComplementaryBackground().replace("rgb(", "rgba(").replace(")", `, ${alpha})`);

  const backgroundColor = getComplementaryBackground();

  const getQualityParams = () => {
    switch (qualityLevel) {
      case "low":
        return { particleCount: 30, opacity: 0.2, speed: 0.5 };
      case "high":
        return { particleCount: 80, opacity: 0.4, speed: 1.5 };
      default:
        return { particleCount: 50, opacity: 0.3, speed: 1 };
    }
  };

  const qualityParams = getQualityParams();

  useEffect(() => {
    const ctx = gsap.context(() => {
      gsap.from(launcherRef.current, {
        opacity: 0,
        scale: 0.95,
        duration: 0.8,
        ease: "power3.out",
      });

      if (backgroundPatternRef.current) {
        gsap.to(backgroundPatternRef.current, {
          backgroundPosition: "100% 100%",
          duration: 120,
          repeat: -1,
          ease: "none",
        });
      }
    });

    const setupWindowControls = async () => {
      try {
        const tauriModule = await import("@tauri-apps/api/window").catch(
          () => null,
        );

        if (tauriModule) {
          const { Window } = tauriModule;
          const currentWindow = Window.getCurrent();

          if (minimizeRef.current) {
            minimizeRef.current.addEventListener("click", () =>
              currentWindow.minimize(),
            );
          }

          if (maximizeRef.current) {
            maximizeRef.current.addEventListener("click", () =>
              currentWindow.toggleMaximize(),
            );
          }

          if (closeRef.current) {
            closeRef.current.addEventListener("click", () =>
              exit(0),
            );
          }
        } else {
          console.log(
            "Tauri API not available, window controls will be decorative only",
          );
        }
      } catch (error) {
        console.error("Failed to initialize window controls:", error);
      }
    };

    setupWindowControls();

    return () => ctx.revert();
  }, []);

  const renderBackgroundEffect = () => {
    // Show theme background image only on play screen - override all other effects
    if (isThemeActive && selectedTheme?.backgroundImage && activeTab === "play") {
      return (
        <div
          className="absolute inset-0 bg-cover bg-center bg-no-repeat"
          style={{
            backgroundImage: `url(${selectedTheme.backgroundImage})`,
          }}
        />
      );
    }

    // Regular background effects for other tabs or when no theme background
    switch (currentEffect) {
      case BACKGROUND_EFFECTS.MATRIX_RAIN:
        return (
          <MatrixRainEffect
            speed={qualityParams.speed}
            opacity={qualityParams.opacity}
            forceEnable={false}
          />
        );
      case BACKGROUND_EFFECTS.ENCHANTMENT_PARTICLES:
        return (
          <EnchantmentParticlesEffect
            opacity={qualityParams.opacity}
            particleCount={qualityParams.particleCount}
            speed={qualityParams.speed}
            forceEnable={false}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_WAVES:
        return (
          <NebulaWaves
            opacity={qualityParams.opacity}
            speed={qualityParams.speed}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_PARTICLES:
        return (
          <NebulaParticles
            opacity={qualityParams.opacity}
            particleCount={qualityParams.particleCount}
            speed={qualityParams.speed}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_GRID:
        return (
          <NebulaGrid
            opacity={qualityParams.opacity}
            speed={qualityParams.speed}
            gridSize={30}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_VOXELS:
        return (
          <NebulaVoxels
            opacity={qualityParams.opacity}
            cubeCount={qualityParams.particleCount}
            speed={qualityParams.speed}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_LIGHTNING:
        return (
          <NebulaLightning
            opacity={qualityParams.opacity * 2}
            speed={qualityParams.speed}
            intensity={qualityParams.speed * 1.2}
            size={1.5}
          />
        );
      case BACKGROUND_EFFECTS.NEBULA_LIQUID_CHROME:
        return (
          <NebulaLiquidChrome
            opacity={qualityParams.opacity * 2}
            speed={qualityParams.speed * 0.2}
            amplitude={0.5}
            frequencyX={3}
            frequencyY={2}
          />
        );
      case BACKGROUND_EFFECTS.RETRO_GRID:
        const hexToRgbaWithLowOpacity = (hex: string) => {
          const r = parseInt(hex.slice(1, 3), 16);
          const g = parseInt(hex.slice(3, 5), 16);
          const b = parseInt(hex.slice(5, 7), 16);
          return `rgba(${r}, ${g}, ${b}, 0.05)`;
        };
        return (
          <div 
            className="absolute inset-0"
            style={{ backgroundColor: hexToRgbaWithLowOpacity(themeAccentColor.value) }}
          ></div>
        );
      case BACKGROUND_EFFECTS.SALAMI_RINGS:
        return (
          <SalamiBackdrop
            accentColorValue={themeAccentColor.value}
            focus={activeTab === "play" ? { x: 72, y: 54 } : { x: 92, y: 4 }}
            animated={isBackgroundAnimationEnabled}
          />
        );
      case BACKGROUND_EFFECTS.PLAIN_BACKGROUND:
        return <PlainBackground accentColorValue={themeAccentColor.value} />;
      default:
        return (
          <div className="absolute inset-0 bg-red-500/20">
            Unknown effect: {currentEffect}
          </div>
        );
    }
  };

  return (
    <div
      ref={launcherRef}
      className="look-shell h-screen w-full border overflow-hidden relative flex flex-col"
      style={{
        backgroundColor: isCustomMediaVisible ? getComplementaryBackgroundWithAlpha(0.55) : backgroundColor,
        borderColor: SURFACE.line,
      }}
    >
      {/* Hintergrund liegt unter Leiste und Inhalt – die Leiste ist kein eigener Kasten mehr */}
      <div className="absolute inset-0">
        <CustomMediaBackground activeTab={activeTab} />
        {shouldShowEffects && !look.ownsBackground && renderBackgroundEffect()}
        {look.Backdrop && <look.Backdrop activeTab={activeTab} />}
        {/* Snow overlay - independent of theme/background */}
        {shouldShowEffects && isSnowEnabled && <Snowfall />}
        {shouldShowEffects && selectedTheme?.id === SALAMI_THEME_ID && <SalamiFall />}
      </div>

      <TopBar
        items={navItems}
        activeTab={activeTab}
        onNavChange={onNavChange}
        minimizeRef={minimizeRef}
        maximizeRef={maximizeRef}
        closeRef={closeRef}
      />

      {look.navPlacement === "below-top" && (
        <div className="look-nav-row relative z-20 flex-shrink-0">
          <look.Nav items={navItems} activeItem={navItemFor(activeTab)} onItemClick={onNavChange} />
        </div>
      )}

      <div className="look-content relative z-10 flex-1 overflow-hidden custom-scrollbar">
        {children}
      </div>

      {look.navPlacement === "bottom" && (
        <div className="look-nav-row relative z-20 flex-shrink-0">
          <look.Nav items={navItems} activeItem={navItemFor(activeTab)} onItemClick={onNavChange} />
        </div>
      )}

      {/* Global Modals Portal */}
      <ProfileWizardV2Modal />
      <ProfileSettingsModal />
      <SettingsModal />
      <ProfileDuplicateModal />
    </div>
  );
}

interface TopBarProps {
  items: TopNavItem[];
  activeTab: string;
  onNavChange: (tabId: string) => void;
  minimizeRef: React.RefObject<HTMLDivElement>;
  maximizeRef: React.RefObject<HTMLDivElement>;
  closeRef: React.RefObject<HTMLDivElement>;
}

/**
 * Leiste oben: Marke, Navigation als Reiter, Konto und Fenster-Knöpfe in einer Zeile –
 * statt NoRisks Seitenleiste mit eigener Kopfzeile.
 */
/** Salami: Detailseiten (profilesv2/<id>, mods/<quelle>/<id>, sync-packs) markieren ihren Reiter mit. */
function navItemFor(path: string): string {
  const first = path.split("/")[0];
  if (first === "profilesv2" || first === "sync-packs") return "profiles";
  return first;
}

function TopBar({ items, activeTab, onNavChange, minimizeRef, maximizeRef, closeRef }: TopBarProps) {
  const { t } = useTranslation();
  const look = useLook();
  const accentColor = useThemeStore((state) => state.accentColor);
  const [appVersion, setAppVersion] = useState<string | null>(null);
  const [availableUpdate, setAvailableUpdate] = useState<UpdateInfo | null>(null);
  const [isUpdating, setIsUpdating] = useState(false);
  const [showCredits, setShowCredits] = useState(false);

  const handleUpdateClick = async () => {
    if (isUpdating) return; // Prevent multiple simultaneous downloads

    setIsUpdating(true);
    try {
      await toast.promise(
        downloadAndInstallUpdate(),
        {
          loading: t('header.update.downloading'),
          success: t('header.update.success'),
          error: (err) => t('header.update.failed', { error: parseErrorMessage(err) }),
        }
      );
    } catch (error) {
      console.error("Failed to download and install update:", error);
      // Toast error is already handled by the promise toast
    } finally {
      setIsUpdating(false);
    }
  };

  useEffect(() => {
    const fetchVersion = async () => {
      try {
        setAppVersion(await ConfigService.getAppVersion());
      } catch (error) {
        console.error("Failed to fetch app version:", error);
        setAppVersion("?.?.?");
      }
    };

    const checkForUpdates = async () => {
      try {
        const updateInfo = await checkUpdateAvailable();
        if (updateInfo) {
          console.log("Update available:", updateInfo);
          setAvailableUpdate(updateInfo);
        }
      } catch (error) {
        console.error("Failed to check for updates:", error);
        // Don't show error to user, just silently fail
      }
    };

    fetchVersion();
    checkForUpdates();

    // Check for updates every 4 hours
    const updateCheckInterval = setInterval(checkForUpdates, 4 * 60 * 60 * 1000);
    return () => clearInterval(updateCheckInterval);
  }, []);

  const settingsItem = items.find((item) => item.isAction);

  return (
    <header
      className="look-topbar relative z-20 grid h-[60px] flex-shrink-0 grid-cols-[minmax(0,1fr)_auto_minmax(max-content,1fr)] items-center gap-3 pl-4 pr-2"
      data-tauri-drag-region
    >
      <div className="flex min-w-0 items-center gap-2" data-tauri-drag-region>
        <button
          type="button"
          onClick={() => setShowCredits(true)}
          aria-label={BRAND.name}
          title={`${BRAND.name} v${appVersion ?? "?.?.?"}`}
          className="flex flex-shrink-0 items-center gap-2.5 py-1 pl-1 pr-2 transition-colors hover:bg-white/5"
        >
          <Logo size="sm" className="!h-8 !w-8" />
          <span className="hidden flex-col leading-none min-[1180px]:flex">
            <span className="font-minecraft text-[17px] text-white">{BRAND.shortName}</span>
            <span className="mt-0.5 text-[10px] text-white/40">v{appVersion ?? "?.?.?"}</span>
          </span>
        </button>

        <NavigationHistory className="text-white/60" />

        {availableUpdate && (
          <Tooltip content={isUpdating ? t('header.update.tooltip_updating') : t('header.update.tooltip_available', { version: availableUpdate.version })}>
            <button
              type="button"
              onClick={handleUpdateClick}
              disabled={isUpdating}
              className="flex h-8 items-center gap-1.5 px-2.5 text-xs disabled:cursor-not-allowed disabled:opacity-60"
              style={{ backgroundColor: accentColor.value, color: "#fff" }}
            >
              <Icon icon="solar:download-minimalistic-bold" className={`h-4 w-4 ${isUpdating ? "animate-pulse" : ""}`} />
              <span>{availableUpdate.version}</span>
            </button>
          </Tooltip>
        )}
      </div>

      {/* Salami: jeder Look bringt seine eigene Navigation mit – hier nur, wenn sie in die Leiste gehört */}
      {look.navPlacement === "top" ? (
        <look.Nav items={items} activeItem={navItemFor(activeTab)} onItemClick={onNavChange} />
      ) : (
        <div data-tauri-drag-region className="h-full" />
      )}

      <div className="flex min-w-0 items-center justify-end gap-2" data-tauri-drag-region>
        <UserProfileBar />

        {settingsItem && (
          <button
            type="button"
            onClick={() => onNavChange(settingsItem.id)}
            title={settingsItem.label}
            aria-label={settingsItem.label}
            className="flex h-9 w-9 flex-shrink-0 items-center justify-center text-white/60 transition-colors hover:bg-white/10 hover:text-white"
          >
            <Icon icon={settingsItem.icon} className="h-5 w-5" />
          </button>
        )}

        <WindowControls
          minimizeRef={minimizeRef}
          maximizeRef={maximizeRef}
          closeRef={closeRef}
        />
      </div>

      <CreditsModal isOpen={showCredits} onClose={() => setShowCredits(false)} />
    </header>
  );
}

interface WindowControlsProps {
  minimizeRef: React.RefObject<HTMLDivElement>;
  maximizeRef: React.RefObject<HTMLDivElement>;
  closeRef: React.RefObject<HTMLDivElement>;
}

function WindowControls({
  minimizeRef,
  maximizeRef,
  closeRef,
}: WindowControlsProps) {
  const { t } = useTranslation();
  const glyph = "h-[11px] w-[11px]";
  const base = "titlebar-button-borderless flex h-9 w-10 items-center justify-center text-white/55 transition-colors cursor-pointer";
  return (
    <div className="ml-1 flex flex-shrink-0 items-center">
      <div ref={minimizeRef} className={`${base} hover:bg-white/10 hover:text-white`} title={t('window.minimize')}>
        <svg viewBox="0 0 11 11" className={glyph} fill="none" stroke="currentColor" strokeWidth="1.2"><path d="M1 5.5h9" /></svg>
      </div>
      <div ref={maximizeRef} className={`${base} hover:bg-white/10 hover:text-white`} title={t('window.maximize')}>
        <svg viewBox="0 0 11 11" className={glyph} fill="none" stroke="currentColor" strokeWidth="1.2"><rect x="1.5" y="1.5" width="8" height="8" rx="1.5" /></svg>
      </div>
      <div ref={closeRef} className={`${base} hover:bg-red-500 hover:text-white`} title={t('window.close')}>
        <svg viewBox="0 0 11 11" className={glyph} fill="none" stroke="currentColor" strokeWidth="1.2"><path d="M1.5 1.5l8 8M9.5 1.5l-8 8" /></svg>
      </div>
    </div>
  );
}
