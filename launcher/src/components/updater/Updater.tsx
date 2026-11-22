"use client";

import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { Icon } from "@iconify/react";
import { useThemeStore } from "../../store/useThemeStore";
import { useFontStore } from "../../store/font-store";
import {
  BACKGROUND_EFFECTS,
  useBackgroundEffectStore,
} from "../../store/background-effect-store";
import { cn } from "../../lib/utils";
import { gsap } from "gsap";
import { Button } from "../ui/buttons/Button";
import { useTranslation } from "react-i18next";
import i18n from "../../i18n/i18n";
import { NebulaGrid } from "../effects/NebulaGrid";
import { NebulaParticles } from "../effects/NebulaParticles";
import { NebulaWaves } from "../effects/NebulaWaves";
import { NebulaVoxels } from "../effects/NebulaVoxels";
import { NebulaLightning } from "../effects/NebulaLightning";
import { NebulaLiquidChrome } from "../effects/NebulaLiquidChrome";
import { MatrixRainEffect } from "../effects/MatrixRainEffect";
import { EnchantmentParticlesEffect } from "../effects/EnchantmentParticlesEffect";
import { useEntranceAnimation } from "../../hooks/useEntranceAnimation";
import { useLook } from "../../themes/looks/registry";

interface UpdaterStatusPayload {
  message: string;
  status:
    | "checking"
    | "downloading"
    | "installing"
    | "uptodate"
    | "pending"
    | "error"
    | "finished"
    | "close";
  progress?: number;
  total?: number;
  chunk?: number;
}

// The backend sends English status texts; show the known ones in the UI language.
const UPDATER_MESSAGES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^Checking for .* updates\.\.\.$/, () => i18n.t('updater.checking')],
  [/^Update found, preparing download\.\.\.$/, () => i18n.t('updater.preparing')],
  [/^Update (.+) found!$/, (m) => i18n.t('updater.found', { version: m[1] })],
  [/^Downloading update: (\d+) bytes$/, (m) => i18n.t('updater.downloading_size', { mb: (Number(m[1]) / (1024 * 1024)).toFixed(1) })],
  [/^Installation complete\.$/, () => i18n.t('updater.installed')],
  [/^(Update installed successfully!|Update successful\.)$/, () => i18n.t('updater.success')],
  [/^Application is up to date\.$/, () => i18n.t('updater.up_to_date')],
  [/^Update check timed out\. Starting without update\.$/, () => i18n.t('updater.timed_out')],
  [/^Update download\/install failed: ([\s\S]+)$/, (m) => i18n.t('updater.failed', { error: m[1] })],
  [/^Update check error: ([\s\S]+)$/, (m) => i18n.t('updater.check_failed', { error: m[1] })],
];

function translateUpdaterMessage(message: string): string {
  for (const [pattern, render] of UPDATER_MESSAGES) {
    const match = message.match(pattern);
    if (match) return render(match);
  }
  return message;
}

export default function Updater() {
  const { t } = useTranslation();
  const [statusMessage, setStatusMessage] = useState<string>("");
  const [progress, setProgress] = useState<number | null>(null);
  const [status, setStatus] =
    useState<UpdaterStatusPayload["status"]>("checking");
  const [isThemeLoaded, setIsThemeLoaded] = useState(false);
  const logoRef = useRef<HTMLImageElement>(null);
  const progressRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const appWindow = getCurrentWindow();
  const closeTimerRef = useRef<NodeJS.Timeout | null>(null);

  const accentColor = useThemeStore((state) => state.accentColor);
  const look = useLook();
  const currentEffect = useBackgroundEffectStore(
    (state) => state.currentEffect,
  );

  useEffect(() => {
    const checkThemeLoaded = () => {
      if (accentColor && accentColor.value) {
        setIsThemeLoaded(true);
        return;
      }
      setTimeout(checkThemeLoaded, 50);
    };
    checkThemeLoaded();
  }, [accentColor]);

  useEffect(() => {
    useFontStore.getState().applyFontToDOM();
  }, []);

  useEntranceAnimation(
    containerRef,
    { opacity: 0, y: 20, scale: 0.95 },
    { opacity: 1, y: 0, scale: 1, duration: 0.6, ease: "back.out(1.2)" },
  );

  useEffect(() => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }

    const unlistenPromise = listen<UpdaterStatusPayload>(
      "updater_status",
      (event) => {
        const {
          message,
          status: newStatus,
          progress: eventProgress,
        } = event.payload;

        if (closeTimerRef.current) {
          clearTimeout(closeTimerRef.current);
          closeTimerRef.current = null;
        }

        setStatusMessage(translateUpdaterMessage(message));
        setStatus(newStatus);

        if (
          newStatus === "downloading" &&
          typeof eventProgress === "number" &&
          eventProgress >= 0 &&
          eventProgress <= 100
        ) {
          setProgress(eventProgress);
          setStatusMessage(t('updater.downloading', { progress: eventProgress }));

          if (progressRef.current) {
            gsap.to(progressRef.current, {
              width: `${eventProgress}%`,
              duration: 0.3,
              ease: "power1.out",
            });
          }
        } else {
          setProgress(null);
        }

        switch (newStatus) {
          case "uptodate":
          case "finished":
            appWindow
              .close()
              .catch((err: Error) =>
                console.error(
                  "Failed to close updater window on completion:",
                  err,
                ),
              );
            break;
          case "error":
            break;
          case "close":
            appWindow
              .close()
              .catch((err: Error) =>
                console.error(
                  "Failed to close updater window on 'close' event:",
                  err,
                ),
              );
            break;
        }
      },
    );

    return () => {
      unlistenPromise
        .then((f) => f())
        .catch((err: Error) =>
          console.error("Failed to unlisten updater events:", err),
        );
      if (closeTimerRef.current) {
        clearTimeout(closeTimerRef.current);
      }
    };
  }, [appWindow]);

  const getStatusIcon = () => {
    switch (status) {
      case "checking":
        return (
          <Icon icon="svg-spinners:ring-resize" className="w-4 h-4" />
        );
      case "downloading":
        return <Icon icon="solar:download-bold" className="w-4 h-4" />;
      case "installing":
        return <Icon icon="solar:box-bold" className="w-4 h-4" />;
      case "uptodate":
      case "finished":
        return <Icon icon="solar:check-circle-bold" className="w-4 h-4" />;
      case "error":
        return <Icon icon="solar:danger-triangle-bold" className="w-4 h-4" />;
      default:
        return <Icon icon="solar:info-circle-bold" className="w-4 h-4" />;
    }
  };

  const handleManualClose = () => {
    appWindow
      .close()
      .catch((err: Error) =>
        console.error("Failed to close updater window:", err),
      );
  };

  const renderBackgroundEffect = () => {
    const effect = currentEffect || BACKGROUND_EFFECTS.NEBULA_GRID;
    switch (effect) {
      case BACKGROUND_EFFECTS.NEBULA_PARTICLES:
        return <NebulaParticles opacity={0.1} />;
      case BACKGROUND_EFFECTS.NEBULA_WAVES:
        return <NebulaWaves opacity={0.1} />;
      case BACKGROUND_EFFECTS.NEBULA_VOXELS:
        return <NebulaVoxels opacity={0.1} />;
      case BACKGROUND_EFFECTS.NEBULA_LIGHTNING:
        return <NebulaLightning opacity={0.1} />;
      case BACKGROUND_EFFECTS.NEBULA_LIQUID_CHROME:
        return <NebulaLiquidChrome opacity={0.1} />;
      case BACKGROUND_EFFECTS.MATRIX_RAIN:
        return <MatrixRainEffect opacity={0.1} />;
      case BACKGROUND_EFFECTS.ENCHANTMENT_PARTICLES:
        return <EnchantmentParticlesEffect opacity={0.1} />;
      case BACKGROUND_EFFECTS.NEBULA_GRID:
      default:
        return <NebulaGrid opacity={0.1} />;
    }
  };

  if (!isThemeLoaded || !accentColor || !accentColor.value) {
    return (
      <div className="h-screen w-screen flex items-center justify-center bg-black">
        <div className="animate-pulse text-white text-xs font-smallcaps">
          {t('updater.loading_theme')}
        </div>
      </div>
    );
  }

  const safeAccentColor = accentColor.value || "#FFFFFF";

  return (
    // Ansatzpunkte für die Looks: look-updater (Fenster), -card, -brand, -logo, -title, -status
    // (data-status), -progress, -bar, -actions. Wie im Hauptfenster: malt der Look den Hintergrund
    // selbst, entfallen die Hintergrund-Effekte.
    <div className="look-updater relative h-screen w-screen overflow-hidden bg-black/80 backdrop-blur-md flex items-center justify-center">
      {!look.ownsBackground && renderBackgroundEffect()}

      <div
        ref={containerRef}
        className={cn(
          "look-updater-card relative flex flex-col items-center justify-between text-center",
          "border rounded-none",
          "w-full h-full",
        )}
        style={{
          backgroundColor: `${safeAccentColor}30`,
          borderColor: `${safeAccentColor}70`,
        }}
      >
        <div className="w-full pt-6" />

        <div className="flex-1 w-full flex flex-col items-center justify-center px-6 gap-8">
          <div className="look-updater-brand flex flex-col items-center">
            <img
              ref={logoRef}
              src="/logo.png"
              alt="Salami Client"
              className="look-updater-logo w-32 h-32 object-contain mb-1"
            />
            <p className="look-updater-title text-xs font-smallcaps text-white/70">
              {t('updater.title')}
            </p>
          </div>

          <div className="flex items-center justify-center mb-4">
            {status === "uptodate" || status === "finished" ? (
              <div
                data-status={status}
                className={cn(
                  "look-updater-status flex items-center justify-center gap-2 py-2 px-4",
                  "border rounded-md",
                )}
                style={{
                  backgroundColor: `${safeAccentColor}30`,
                  borderColor: `${safeAccentColor}70`,
                }}
              >
                <Icon
                  icon="solar:check-circle-bold"
                  className="w-5 h-5 text-green-400"
                />
                <span className="font-smallcaps text-xs text-white">
                  {t('updater.complete')}
                </span>
              </div>
            ) : status === "error" ? (
              <div
                data-status={status}
                className={cn(
                  "look-updater-status flex items-center justify-center gap-2 py-2 px-4",
                  "border rounded-md",
                )}
                style={{
                  backgroundColor: "#ef444430",
                  borderColor: "#ef444470",
                }}
              >
                <Icon
                  icon="solar:danger-triangle-bold"
                  className="w-5 h-5 text-red-400"
                />
                <span className="font-smallcaps text-xs text-white">
                  {statusMessage}
                </span>
              </div>
            ) : (
              <div
                data-status={status}
                className={cn(
                  "look-updater-status flex items-center justify-center gap-2 py-2 px-4",
                  "border rounded-md",
                )}
                style={{
                  backgroundColor: `${safeAccentColor}30`,
                  borderColor: `${safeAccentColor}70`,
                }}
              >
                {getStatusIcon()}
                <span className="font-smallcaps text-xs text-white">
                  {statusMessage || t('updater.initializing')}
                </span>
              </div>
            )}
          </div>

          {progress !== null && (
            <div
              className="look-updater-progress w-3/4 h-2.5 rounded-md overflow-hidden border"
              style={{
                backgroundColor: `${safeAccentColor}15`,
                borderColor: `${safeAccentColor}50`,
              }}
            >
              <div
                ref={progressRef}
                className="look-updater-bar h-full rounded-sm"
                style={{
                  width: `${progress}%`,
                  backgroundColor: safeAccentColor,
                }}
              />
            </div>
          )}
        </div>

        <div className="look-updater-actions w-full p-6 flex justify-center">
          {status === "error" && (
            <Button
              variant="destructive"
              size="sm"
              onClick={handleManualClose}
              icon={<Icon icon="solar:close-circle-bold" className="w-4 h-4" />}
            >
              {t('common.close')}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
