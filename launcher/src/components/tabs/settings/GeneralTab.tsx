"use client";

import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { cn } from "../../../lib/utils";
import { Select } from "../../ui/Select";
import { ToggleSwitch } from "../../ui/ToggleSwitch";
import { RangeSlider } from "../../ui/RangeSlider";
import { ColorPicker } from "../../ColorPicker";
import { ColorPickerModal } from "../../modals/ColorPickerModal";
import { SettingsSection } from "../../ui/settings/SettingsSection";
import { SettingRow } from "../../ui/settings/SettingRow";
import { useThemeStore } from "../../../store/useThemeStore";
import { useGlobalModal } from "../../../hooks/useGlobalModal";
import { useLauncherTheme } from "../../../hooks/useLauncherTheme";
import { LANGUAGE_OPTIONS, type SupportedLanguage } from "../../../i18n";
import { useSettingsConfig, useSettingsKeywords } from "./settings-context";
import { isWindows } from "../../../utils/platform";
import { useEffect, useRef, useState } from "react";
import { UI_SCALE_MAX, UI_SCALE_MIN, UI_SCALE_STEP, useUiScaleStore } from "../../../store/ui-scale-store";

export function GeneralTab() {
  const { t } = useTranslation();
  const kw = useSettingsKeywords();
  const { tempConfig, setTempConfig, saving } = useSettingsConfig();
  const { language, setLanguage, accentColor, borderRadius, setBorderRadius } = useThemeStore();
  const { showModal, hideModal } = useGlobalModal();
  const { isThemeActive } = useLauncherTheme();
  const isAccentColorDisabled = isThemeActive;

  const uiScale = useUiScaleStore((s) => s.scale);
  const setUiScale = useUiScaleStore((s) => s.setScale);
  const resetUiScale = useUiScaleStore((s) => s.reset);
  // Beim Ziehen erst loslassen, dann zoomen – sonst rutscht der Regler unter der Maus weg.
  const [uiScaleDraft, setUiScaleDraft] = useState<number | null>(null);
  const uiScaleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commitUiScale = (percent: number) => {
    if (uiScaleTimer.current) clearTimeout(uiScaleTimer.current);
    uiScaleTimer.current = null;
    setUiScale(percent / 100);
    setUiScaleDraft(null);
  };
  const draftUiScale = (percent: number) => {
    setUiScaleDraft(percent);
    if (uiScaleTimer.current) clearTimeout(uiScaleTimer.current);
    uiScaleTimer.current = setTimeout(() => commitUiScale(percent), 600);
  };
  useEffect(() => () => {
    if (uiScaleTimer.current) clearTimeout(uiScaleTimer.current);
  }, []);

  const handleConcurrentDownloadsChange = (value: number) => {
    if (tempConfig) setTempConfig({ ...tempConfig, concurrent_downloads: value });
  };
  const handleConcurrentIoLimitChange = (value: number) => {
    if (tempConfig) setTempConfig({ ...tempConfig, concurrent_io_limit: value });
  };

  return (
    <div className="space-y-6">
      <SettingsSection
        id="settings-section-language"
        title={t("settings.language")}
        icon="solar:global-bold"
        keywords={kw("settings.language", "sprache", "language", "locale")}
        description={t("settings.language.description")}
      >
        <SettingRow label={t("settings.language")} searchKeywords={kw("settings.language", "sprache", "locale")}>
          <div className="w-56">
            <Select
              value={language}
              onChange={(value) => setLanguage(value as SupportedLanguage)}
              options={LANGUAGE_OPTIONS.map((opt) => ({
                value: opt.value,
                label: opt.label,
                icon: <Icon icon={opt.flag} className="w-5 h-5" />,
              }))}
              size="sm"
              variant="flat"
            />
          </div>
        </SettingRow>
      </SettingsSection>

      <SettingsSection
        id="settings-section-accent"
        title={t("settings.accent_color.title")}
        icon="solar:palette-bold"
        keywords={kw("settings.accent_color.title", "color", "colour", "farbe", "akzent", "accent", "theme")}
        description={
          <>
            {t("settings.accent_color.description")}
            {isThemeActive && (
              <span className="text-white/50 ml-2">{t("settings.accent_color.disabled_theme")}</span>
            )}
          </>
        }
      >
        <div className="flex items-center gap-6 py-3">
          <div className="flex-1">
            <ColorPicker shape="square" size="md" showCustomOption={false} disabled={isAccentColorDisabled} />
          </div>

          <button
            onClick={() => {
              if (!isAccentColorDisabled) {
                showModal('color-picker-modal',
                  <ColorPickerModal
                    onClose={() => hideModal('color-picker-modal')}
                  />
                );
              }
            }}
            className={cn(
              "group flex items-center gap-3 px-4 py-3 rounded-lg border-2 border-dashed border-[#ffffff30] transition-all duration-200",
              isAccentColorDisabled
                ? "opacity-40 cursor-not-allowed"
                : "hover:border-[#ffffff50] cursor-pointer"
            )}
            title={isAccentColorDisabled ? t("settings.accent_color.custom_tooltip_disabled") : t("settings.accent_color.custom_tooltip")}
            disabled={isAccentColorDisabled}
          >
            <div
              className="w-8 h-8 rounded-md border-2 border-white/20 shadow-lg group-hover:scale-105 transition-transform"
              style={{ backgroundColor: accentColor.value }}
            />
            <div className="flex flex-col items-start">
              <span className="font-minecraft text-base text-white/80 group-hover:text-white transition-colors">
                {t("settings.accent_color.custom")}
              </span>
              <span className="text-xs text-white/60 font-minecraft">
                {accentColor.value}
              </span>
            </div>
            <Icon
              icon="solar:palette-bold"
              className="w-5 h-5 text-white/60 group-hover:text-white transition-colors"
            />
          </button>
        </div>
      </SettingsSection>

      <SettingsSection id="settings-section-behaviour" title={t("settings.sections.behaviour")} icon="solar:tuning-2-bold" keywords={kw("settings.sections.behaviour", "behaviour", "behavior", "verhalten")}>
        <SettingRow
          label={t("settings.auto_updates")}
          description={t("settings.auto_updates.tooltip")}
          searchKeywords={kw("settings.auto_updates", "update", "updates", "aktualisierung")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.auto_check_updates || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, auto_check_updates: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
        {isWindows() && (
          <SettingRow
            label={t("settings.clips.enabled")}
            description={t("settings.clips.enabled.short")}
            searchKeywords={kw(
              "settings.clips.enabled",
              "clips",
              "clip",
              "replay",
              "aufnahme",
              "record",
              "highlight",
            )}
            disabled={saving}
          >
            <ToggleSwitch
              checked={tempConfig?.clips?.enabled || false}
              onChange={(checked) =>
                tempConfig &&
                setTempConfig({
                  ...tempConfig,
                  clips: { ...tempConfig.clips, enabled: checked },
                })
              }
              disabled={saving}
              size="md"
            />
          </SettingRow>
        )}
        <SettingRow
          label={t("settings.discord_presence")}
          description={t("settings.discord_presence.tooltip")}
          searchKeywords={kw("settings.discord_presence", "discord", "presence", "status", "rich")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.enable_discord_presence || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, enable_discord_presence: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
        <SettingRow
          label={t("settings.beta_updates")}
          description={t("settings.beta_updates.tooltip")}
          searchKeywords={kw("settings.beta_updates", "beta", "update", "channel", "kanal")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.check_beta_channel || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, check_beta_channel: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
        <SettingRow
          label={t("settings.open_logs")}
          description={t("settings.open_logs.tooltip")}
          searchKeywords={kw("settings.open_logs", "logs", "log", "protokoll")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.open_logs_after_starting || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, open_logs_after_starting: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
        <SettingRow
          label={t("settings.hide_window")}
          description={t("settings.hide_window.tooltip")}
          searchKeywords={kw("settings.hide_window", "window", "fenster", "hide", "verstecken", "minimize")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.hide_on_process_start || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, hide_on_process_start: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
      </SettingsSection>

      <SettingsSection
        id="settings-section-ingame"
        title={t("settings.sections.ingame")}
        icon="solar:gamepad-bold"
        keywords={kw("settings.sections.ingame", "spiel", "game", "salami", "client")}
      >
        <SettingRow
          label={t("settings.combat_modules")}
          description={t("settings.combat_modules.tooltip")}
          searchKeywords={kw(
            "settings.combat_modules",
            "crystal",
            "kristall",
            "anchor",
            "totem",
            "utility",
            "kampf",
            "combat",
            "pvp",
          )}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.combat_modules ?? true}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, combat_modules: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
      </SettingsSection>

      <SettingsSection id="settings-section-interface" title={t("settings.sections.interface")} icon="solar:slider-horizontal-bold" keywords={kw("settings.sections.interface", "downloads", "interface", "oberfläche", "performance")}>
        <SettingRow
          label={t("settings.concurrent_downloads")}
          description={t("settings.concurrent_downloads.tooltip")}
          searchKeywords={kw("settings.concurrent_downloads", "download", "downloads", "herunterladen", "parallel")}
          disabled={saving}
          vertical
        >
          <RangeSlider
            value={tempConfig?.concurrent_downloads || 3}
            onChange={handleConcurrentDownloadsChange}
            min={1}
            max={10}
            step={1}
            disabled={saving}
            variant="flat"
            size="sm"
            minLabel="1"
            maxLabel="10"
            icon={<Icon icon="solar:multiple-forward-right-bold" className="w-3 h-3" />}
          />
        </SettingRow>
        <SettingRow
          label={t("settings.concurrent_io")}
          description={t("settings.concurrent_io.tooltip")}
          searchKeywords={kw("settings.concurrent_io", "io", "disk", "parallel", "festplatte")}
          disabled={saving}
          vertical
        >
          <RangeSlider
            value={tempConfig?.concurrent_io_limit || 10}
            onChange={handleConcurrentIoLimitChange}
            min={1}
            max={20}
            step={1}
            disabled={saving}
            variant="flat"
            size="sm"
            minLabel="1"
            maxLabel="20"
            icon={<Icon icon="solar:server-bold" className="w-3 h-3" />}
          />
        </SettingRow>
        <SettingRow
          label={t("settings.ui_scale")}
          description={t("settings.ui_scale.tooltip")}
          searchKeywords={kw("settings.ui_scale", "zoom", "größe", "groesse", "size", "scale", "skalierung", "kleiner", "größer")}
          vertical
        >
          <div className="flex items-center gap-3">
            <div className="flex-1">
              <RangeSlider
                value={uiScaleDraft ?? Math.round(uiScale * 100)}
                onChange={draftUiScale}
                onChangeEnd={commitUiScale}
                min={Math.round(UI_SCALE_MIN * 100)}
                max={Math.round(UI_SCALE_MAX * 100)}
                step={Math.round(UI_SCALE_STEP * 100)}
                variant="flat"
                size="sm"
                unit="%"
                recommendedValue={100}
                minLabel={`${Math.round(UI_SCALE_MIN * 100)}%`}
                maxLabel={`${Math.round(UI_SCALE_MAX * 100)}%`}
                icon={<Icon icon="solar:magnifer-zoom-in-bold" className="w-3 h-3" />}
              />
            </div>
            <button
              type="button"
              onClick={resetUiScale}
              disabled={uiScale === 1}
              className="px-3 py-1.5 rounded-lg text-xs font-minecraft text-white/80 bg-white/10 hover:bg-white/15 disabled:opacity-40 disabled:cursor-default"
            >
              {t("settings.ui_scale.reset")}
            </button>
          </div>
        </SettingRow>
        <SettingRow
          label={t("settings.border_radius")}
          description={t("settings.border_radius.tooltip")}
          searchKeywords={kw("settings.border_radius", "border", "rand", "ecken", "eckenradius", "corner", "radius", "rounding", "rundung")}
          disabled={saving}
          vertical
        >
          <RangeSlider
            value={borderRadius}
            onChange={setBorderRadius}
            min={0}
            max={20}
            step={1}
            disabled={saving}
            variant="flat"
            size="sm"
            minLabel="0px"
            maxLabel="20px"
            icon={<Icon icon="solar:widget-bold" className="w-3 h-3" />}
          />
        </SettingRow>
      </SettingsSection>
    </div>
  );
}
