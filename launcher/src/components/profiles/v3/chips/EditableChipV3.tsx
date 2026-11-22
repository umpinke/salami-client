"use client";

/**
 * EditableChipV3 — Inline-edit chip built on top of `ThemedDropdown` so it
 * inherits the V3 accent-tinted popover recipe used everywhere else
 * (Filter/Sort/NoriskPack/Version dropdowns in LocalContentTabV3).
 *
 * Visually identical to the local `Chip` atom in ProfileDetailViewV3 plus:
 *  · hover pen-icon + subtle white/10 bg when active
 *  · click → ThemedDropdown panel (accent-blur shell)
 *  · optional Save/Cancel footer for text-editors (disable for list-pickers
 *    that commit on click)
 *  · `onOpen` fires once when the popover opens — used for lazy-fetching
 *    picker data on demand instead of on mount
 */

import { useEffect, useRef, useState } from "react";
import type React from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { useThemeStore } from "../../../../store/useThemeStore";
import { ThemedDropdown } from "../shared/ThemedDropdown";
import { hexAlpha, kante, readableOn } from "../../../ui/design-system";
import { SALAMI_COLORS } from "../../../../themes/salami/salami-theme";

interface EditableChipV3Props {
  icon?: string;
  children: React.ReactNode;
  renderEditor: (ctx: { close: () => void; commit: () => void }) => React.ReactNode;
  onSave?: () => void;
  onCancel?: () => void;
  onOpen?: () => void;
  disabled?: boolean;
  disabledReason?: string;
  /** Render Save/Cancel footer. Default true. Set false for list-pickers. */
  withSaveCancel?: boolean;
  /** Tailwind width class understood by `ThemedDropdown` (w-48 … w-72). */
  width?: string;
  /** Salami: Etikett in Creme (Standard) oder Salami-Rot – wie `DeliLabel`. */
  /** "ghost": noch leer (z. B. keine Gruppe) – gestrichelter Umriss statt Etikett */
  tone?: "fat" | "red" | "ghost";
}

export function EditableChipV3({
  icon,
  children,
  renderEditor,
  onSave,
  onCancel,
  onOpen,
  disabled,
  disabledReason,
  withSaveCancel = true,
  width = "w-56",
  tone = "fat",
}: EditableChipV3Props) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const accent = useThemeStore((s) => s.accentColor);

  // Fire onOpen exactly when the popover transitions closed → open
  useEffect(() => {
    if (open) onOpen?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const commit = () => {
    onSave?.();
    setOpen(false);
  };

  const close = () => {
    setOpen(false);
    onCancel?.();
  };

  return (
    <div className="relative inline-flex">
      {/* Salami: Etikett wie an der Feinkosttheke (DeliLabel) – Creme mit Lochung, klickbar */}
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => !disabled && setOpen((v) => !v)}
        title={disabled ? disabledReason : undefined}
        className={`look-label look-label--${tone} group inline-flex items-center gap-2 py-1.5 pl-2 pr-2.5 text-sm font-minecraft leading-none transition-[filter,transform] duration-150
          ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer hover:brightness-105 active:translate-y-px"}`}
        style={{
          background: tone === "red" ? SALAMI_COLORS.red : tone === "ghost" ? "transparent" : SALAMI_COLORS.fat,
          color: tone === "red" ? SALAMI_COLORS.fat : tone === "ghost" ? "rgba(243, 227, 211, 0.6)" : SALAMI_COLORS.pepper,
          border: tone === "ghost" ? "1px dashed rgba(243, 227, 211, 0.35)" : undefined,
          borderRadius: "4px 10px 10px 4px",
          boxShadow: open && !disabled
            ? `0 2px 0 rgba(0,0,0,0.35), 0 0 0 2px ${hexAlpha(accent.value, 0.75)}`
            : "0 2px 0 rgba(0,0,0,0.35)",
        }}
      >
        {tone !== "ghost" && (
          <span
            className="h-2 w-2 flex-shrink-0"
            style={{ borderRadius: 999, background: tone === "red" ? SALAMI_COLORS.redDark : SALAMI_COLORS.casing }}
          />
        )}
        {icon && <Icon icon={icon} className="w-3.5 h-3.5 flex-shrink-0 opacity-70" />}
        <span>{children}</span>
        {disabled ? (
          <Icon icon="solar:lock-keyhole-minimalistic-bold" className="w-3.5 h-3.5 opacity-60 flex-shrink-0" />
        ) : (
          <Icon
            icon="solar:pen-linear"
            className={`w-3.5 h-3.5 flex-shrink-0 transition-opacity ${
              open ? "opacity-90" : "opacity-45 group-hover:opacity-90"
            }`}
          />
        )}
      </button>

      <ThemedDropdown
        open={open && !disabled}
        onClose={close}
        width={width}
        align="left"
        triggerRef={triggerRef}
      >
        {renderEditor({ close, commit })}
        {withSaveCancel && (
          <>
            <div className="my-1 border-t border-white/10" />
            <div className="flex items-center justify-end gap-1 px-2 py-1">
              <button
                onClick={close}
                className="px-2.5 py-1 rounded text-xs font-minecraft text-white/60 hover:text-white hover:bg-white/10 transition-colors"
              >
                {t("profiles.v3.chips.cancel")}
              </button>
              <button
                onClick={commit}
                style={{
                  backgroundColor: accent.value,
                  color: readableOn(accent.value),
                  boxShadow: kante(accent.value, 2),
                }}
                className="px-2.5 py-1 rounded text-xs font-minecraft transition-[filter,transform] hover:brightness-110 active:translate-y-px"
              >
                {t("profiles.v3.chips.save")}
              </button>
            </div>
          </>
        )}
      </ThemedDropdown>
    </div>
  );
}
