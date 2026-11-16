"use client";

import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { Modal } from "../ui/Modal";
import { useThemeStore } from "../../store/useThemeStore";
import { IconButton } from "../ui/buttons/IconButton";
import { Button } from "../ui/buttons/Button";
import { openExternalUrl } from "../../services/tauri-service";
import { BRAND } from "../../config/salami";
import { hexAlpha, SURFACE } from "../ui/design-system";

interface CreditsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function CreditsModal({ isOpen, onClose }: CreditsModalProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);
  
  if (!isOpen) return null;

  const handleOpenUrl = async (url: string) => {
    try {
      await openExternalUrl(url);
    } catch (error) {
      console.error("Failed to open external URL:", error);
    }
  };

  return (
    <Modal
      title={t('credits_modal.title')}
      titleIcon={<Icon icon="solar:code-bold" className="w-6 h-6" />}
      onClose={onClose}
      width="md"
    >
      <div className="px-6 pb-6 pt-2">
        {/* Zeilen mit Haarlinien statt Kästen */}
        <div>
          {/* Salami Client selbst */}
          <div className="flex items-center gap-4 border-b py-4" style={{ borderColor: SURFACE.line }}>
            <img src={BRAND.logoSmall} alt="" className="h-10 w-10 shrink-0 rounded-full" />
            <div className="min-w-0 flex-1">
              <p className="font-minecraft text-2xl lowercase leading-none text-white">{BRAND.name}</p>
              <p className="mt-1.5 font-minecraft text-xs text-white/50">{t("credits_modal.salami_role")}</p>
            </div>
            <IconButton
              icon={<Icon icon="solar:code-bold" className="w-4 h-4" />}
              onClick={() => handleOpenUrl(BRAND.sourceUrl)}
              variant="default"
              size="sm"
              title={t("about.source_code")}
            />
          </div>

          {/* Herkunft des Launcher-Codes (GPL-3.0 verlangt den Hinweis) */}
          <div className="flex items-center gap-4 border-b py-4" style={{ borderColor: SURFACE.line }}>
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center"
              style={{ backgroundColor: hexAlpha(accentColor.value, 0.16), borderRadius: "var(--border-radius)" }}
            >
              <Icon icon="solar:code-2-bold" className="h-5 w-5" style={{ color: accentColor.value }} />
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-minecraft text-[11px] lowercase text-white/40">{t("credits_modal.built_on")}</p>
              <p className="mt-0.5 font-minecraft text-sm leading-snug text-white/80">{t("about.attribution")}</p>
            </div>
            <IconButton
              icon={<Icon icon="solar:arrow-right-up-bold" className="w-4 h-4" />}
              onClick={() => handleOpenUrl("https://github.com/NoRiskClient/noriskclient-launcher")}
              variant="default"
              size="sm"
              title="NoRisk Client Launcher"
            />
          </div>

          {/* Oberfläche des ursprünglichen Launchers */}
          <div className="flex items-center gap-4 border-b py-4" style={{ borderColor: SURFACE.line }}>
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center"
              style={{ backgroundColor: "rgba(255,255,255,0.05)", borderRadius: "var(--border-radius)" }}
            >
              <Icon icon="solar:code-bold" className="h-5 w-5 text-white/50" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-minecraft text-[11px] lowercase text-white/40">{t("credits_modal.original_ui")}</p>
              <p className="mt-0.5 flex items-baseline gap-2 font-minecraft text-sm text-white/80">
                Deadmake
                <span className="text-xs text-white/40">aka Maggus</span>
              </p>
            </div>
            <IconButton
              icon={<Icon icon="solar:global-bold" className="w-4 h-4" />}
              onClick={() => handleOpenUrl("https://deadmake.dev")}
              variant="default"
              size="sm"
              title="deadmake.dev"
            />
          </div>

          {/* Lizenzen */}
          <div className="flex justify-center pt-5">
            <Button
              variant="ghost"
              className="flex items-center gap-2 px-6 py-3 hover:bg-white/5 transition-colors"
              onClick={() => handleOpenUrl(`${BRAND.sourceUrl}/blob/main/launcher/LICENSE`)}
            >
              <Icon icon="solar:arrow-right-up-bold" className="w-5 h-5" />
              <span className="font-minecraft text-sm lowercase">{t('credits_modal.view_licenses')}</span>
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
