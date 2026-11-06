"use client";

import { useEffect, useState, type InputHTMLAttributes } from "react";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { Button } from "../../ui/buttons/Button";
import { SettingsSection } from "../../ui/settings/SettingsSection";
import { SettingRow } from "../../ui/settings/SettingRow";
import { ToggleSwitch } from "../../ui/ToggleSwitch";
import { useConfirmDialog } from "../../../hooks/useConfirmDialog";
import { openExternalUrl } from "../../../services/tauri-service";
import { getAppVersion } from "../../../services/launcher-config-service";
import { BRAND } from "../../../config/salami";
import { useSettingsConfig, useSettingsKeywords } from "./settings-context";

export function AdvancedTab() {
  const { t } = useTranslation();
  const kw = useSettingsKeywords();
  const { tempConfig, setTempConfig, saving } = useSettingsConfig();
  const { confirm, confirmDialog } = useConfirmDialog();

  const [isHooksExpanded, setIsHooksExpanded] = useState(false);
  const [isPreLaunchEditEnabled, setIsPreLaunchEditEnabled] = useState(false);
  const [isWrapperEditEnabled, setIsWrapperEditEnabled] = useState(false);
  const [isPostExitEditEnabled, setIsPostExitEditEnabled] = useState(false);
  const [appVersion, setAppVersion] = useState<string | null>(null);

  useEffect(() => {
    getAppVersion().then(setAppVersion).catch(() => setAppVersion(null));
  }, []);

  // Salami: Start-Befehle werden erst beim Verlassen des Feldes gespeichert – das Backend
  // fragt dann einmal per nativem Dialog nach (nicht bei jedem Tastendruck).
  const commitHook = (hook: "pre_launch" | "wrapper" | "post_exit", value: string) => {
    if (!tempConfig) return;
    const next = value.trim() ? value : null;
    if ((tempConfig.hooks?.[hook] ?? null) === next) return;
    setTempConfig({ ...tempConfig, hooks: { ...tempConfig.hooks, [hook]: next } });
  };

  return (
    <div className="space-y-6">
      <SettingsSection id="settings-section-login_cache" title={t("settings.sections.login_cache")} icon="solar:login-3-bold" keywords={kw("settings.sections.login_cache", "login", "cache", "anmeldung")}>
        <SettingRow
          label={t("settings.browser_login")}
          description={t("settings.browser_login.tooltip")}
          searchKeywords={kw("settings.browser_login", "browser", "login", "anmeldung", "auth", "microsoft")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.use_browser_based_login || false}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, use_browser_based_login: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
        <SettingRow
          label={t("settings.cache_natives")}
          description={t("settings.cache_natives.tooltip")}
          searchKeywords={kw("settings.cache_natives", "cache", "natives", "extraction", "performance")}
          disabled={saving}
        >
          <ToggleSwitch
            checked={tempConfig?.cache_natives_extraction ?? true}
            onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, cache_natives_extraction: checked })}
            disabled={saving}
            size="md"
          />
        </SettingRow>
      </SettingsSection>

      <SettingsSection
        id="settings-section-gamedir"
        title={t("settings.game_data_dir.title")}
        icon="solar:folder-bold"
        keywords={kw("settings.game_data_dir.title", "game", "data", "directory", "ordner", "verzeichnis", "pfad", "path", "folder")}
        description={t("settings.game_data_dir.description")}
      >
        <div className="flex gap-3 py-3">
          <input
            type="text"
            value={tempConfig?.custom_game_directory || ""}
            placeholder={t("settings.game_data_dir.placeholder")}
            className="flex-1 p-3 rounded-md bg-black/40 border border-[#ffffff20] text-white placeholder-white/40 font-minecraft focus:outline-none focus:ring-2 focus:ring-white/30"
            disabled={saving}
            readOnly
          />
          {tempConfig?.custom_game_directory && (
            <Button
              variant="ghost"
              className="px-4 py-3 border border-[#ffffff20] hover:bg-red-500/20 hover:border-red-500/30 transition-colors"
              disabled={saving}
              onClick={() => {
                if (tempConfig) {
                  setTempConfig({
                    ...tempConfig,
                    custom_game_directory: null,
                  });
                }
              }}
              title={t("settings.game_data_dir.reset_tooltip")}
            >
              <Icon icon="solar:close-circle-bold" className="w-5 h-5 text-red-400" />
            </Button>
          )}
          <Button
            variant="ghost"
            className="px-4 py-3 border border-[#ffffff20] hover:bg-white/5 transition-colors"
            disabled={saving}
            onClick={async () => {
              try {
                const { open } = await import('@tauri-apps/plugin-dialog');
                const directory = await open({
                  multiple: false,
                  directory: true,
                });

                if (directory && tempConfig) {
                  setTempConfig({
                    ...tempConfig,
                    custom_game_directory: directory,
                  });
                }
              } catch (error) {
                console.error('Fehler beim Ordner-Dialog:', error);
              }
            }}
            title={t("settings.game_data_dir.select_tooltip")}
          >
            <Icon icon="solar:folder-open-bold" className="w-5 h-5" />
          </Button>
        </div>
      </SettingsSection>

      <SettingsSection
        id="settings-section-hooks"
        title={t("settings.hooks.title")}
        icon="solar:code-bold"
        keywords={kw("settings.hooks.title", "hook", "hooks", "script", "skript", "command", "befehl", "wrapper")}
        description={t("settings.hooks.description")}
        headerActions={
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsHooksExpanded((v) => !v)}
            icon={
              <Icon
                icon={isHooksExpanded ? "solar:alt-arrow-up-bold" : "solar:alt-arrow-down-bold"}
                className="w-5 h-5"
              />
            }
          >
            {isHooksExpanded ? t("settings.hooks.hide") : t("settings.hooks.show")}
          </Button>
        }
      >
        {isHooksExpanded && (
          <div className="space-y-6 py-3">
            <div className="p-4 rounded-lg border border-[#ffffff20] hover:bg-black/30 transition-colors">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Icon icon="solar:play-circle-bold" className="w-5 h-5 text-white" />
                  <h5 className="font-smallcaps text-base text-white">{t("settings.hooks.pre_launch.title")}</h5>
                </div>
                <Button
                  variant={isPreLaunchEditEnabled ? "secondary" : "ghost"}
                  size="sm"
                  onClick={async () => {
                    if (isPreLaunchEditEnabled) {
                      setIsPreLaunchEditEnabled(false);
                      return;
                    }
                    const confirmed = await confirm({
                      title: t("settings.hooks.pre_launch.confirm_title"),
                      message:
                        t("settings.hooks.pre_launch.confirm_message"),
                      confirmText: t("common.enable"),
                      cancelText: t("common.cancel"),
                      type: "warning",
                      fullscreen: true,
                    });
                    if (confirmed) {
                      setIsPreLaunchEditEnabled(true);
                      toast.success(t("settings.hooks.pre_launch.enabled"));
                    }
                  }}
                  icon={
                    <Icon
                      icon={isPreLaunchEditEnabled ? "solar:lock-unlocked-bold" : "solar:lock-keyhole-bold"}
                      className="w-4 h-4"
                    />
                  }
                >
                  {isPreLaunchEditEnabled ? t("settings.hooks.disable_editing") : t("settings.hooks.enable_editing")}
                </Button>
              </div>
              <p className="text-sm text-white/60 font-minecraft mb-4">
                {t("settings.hooks.pre_launch.description")}
              </p>
              <HookInput
                value={tempConfig?.hooks?.pre_launch || ""}
                onCommit={(value) => commitHook("pre_launch", value)}
                placeholder={t("settings.hooks.pre_launch.placeholder")}
                className="w-full p-3 rounded-md bg-black/40 border border-[#ffffff20] text-white placeholder-white/40 font-minecraft focus:outline-none focus:ring-2 focus:ring-white/30"
                disabled={saving || !isPreLaunchEditEnabled}
                title={!isPreLaunchEditEnabled ? t("settings.hooks.pre_launch.disabled_tooltip") : undefined}
              />
            </div>

            <div className="p-4 rounded-lg border border-[#ffffff20] hover:bg-black/30 transition-colors">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Icon icon="solar:shield-bold" className="w-5 h-5 text-white" />
                  <h5 className="font-smallcaps text-base text-white">{t("settings.hooks.wrapper.title")}</h5>
                </div>
                <Button
                  variant={isWrapperEditEnabled ? "secondary" : "ghost"}
                  size="sm"
                  onClick={async () => {
                    if (isWrapperEditEnabled) {
                      setIsWrapperEditEnabled(false);
                      return;
                    }
                    const confirmed = await confirm({
                      title: t("settings.hooks.wrapper.confirm_title"),
                      message:
                        t("settings.hooks.wrapper.confirm_message"),
                      confirmText: t("common.enable"),
                      cancelText: t("common.cancel"),
                      type: "warning",
                      fullscreen: true,
                    });
                    if (confirmed) {
                      setIsWrapperEditEnabled(true);
                      toast.success(t("settings.hooks.wrapper.enabled"));
                    }
                  }}
                  icon={
                    <Icon
                      icon={isWrapperEditEnabled ? "solar:lock-unlocked-bold" : "solar:lock-keyhole-bold"}
                      className="w-4 h-4"
                    />
                  }
                >
                  {isWrapperEditEnabled ? t("settings.hooks.disable_editing") : t("settings.hooks.enable_editing")}
                </Button>
              </div>
              <p className="text-sm text-white/60 font-minecraft mb-4">
                {t("settings.hooks.wrapper.description")}
              </p>
              <HookInput
                value={tempConfig?.hooks?.wrapper || ""}
                onCommit={(value) => commitHook("wrapper", value)}
                placeholder={t("settings.hooks.wrapper.placeholder")}
                className="w-full p-3 rounded-md bg-black/40 border border-[#ffffff20] text-white placeholder-white/40 font-minecraft focus:outline-none focus:ring-2 focus:ring-white/30"
                disabled={saving || !isWrapperEditEnabled}
                title={!isWrapperEditEnabled ? t("settings.hooks.wrapper.disabled_tooltip") : undefined}
              />
            </div>

            <div className="p-4 rounded-lg border border-[#ffffff20] hover:bg-black/30 transition-colors">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Icon icon="solar:stop-circle-bold" className="w-5 h-5 text-white" />
                  <h5 className="font-smallcaps text-base text-white">{t("settings.hooks.post_exit.title")}</h5>
                </div>
                <Button
                  variant={isPostExitEditEnabled ? "secondary" : "ghost"}
                  size="sm"
                  onClick={async () => {
                    if (isPostExitEditEnabled) {
                      setIsPostExitEditEnabled(false);
                      return;
                    }
                    const confirmed = await confirm({
                      title: t("settings.hooks.post_exit.confirm_title"),
                      message:
                        t("settings.hooks.post_exit.confirm_message"),
                      confirmText: t("common.enable"),
                      cancelText: t("common.cancel"),
                      type: "warning",
                      fullscreen: true,
                    });
                    if (confirmed) {
                      setIsPostExitEditEnabled(true);
                      toast.success(t("settings.hooks.post_exit.enabled"));
                    }
                  }}
                  icon={
                    <Icon
                      icon={isPostExitEditEnabled ? "solar:lock-unlocked-bold" : "solar:lock-keyhole-bold"}
                      className="w-4 h-4"
                    />
                  }
                >
                  {isPostExitEditEnabled ? t("settings.hooks.disable_editing") : t("settings.hooks.enable_editing")}
                </Button>
              </div>
              <p className="text-sm text-white/60 font-minecraft mb-4">
                {t("settings.hooks.post_exit.description")}
              </p>
              <HookInput
                value={tempConfig?.hooks?.post_exit || ""}
                onCommit={(value) => commitHook("post_exit", value)}
                placeholder={t("settings.hooks.post_exit.placeholder")}
                className="w-full p-3 rounded-md bg-black/40 border border-[#ffffff20] text-white placeholder-white/40 font-minecraft focus:outline-none focus:ring-2 focus:ring-white/30"
                disabled={saving || !isPostExitEditEnabled}
                title={!isPostExitEditEnabled ? t("settings.hooks.post_exit.disabled_tooltip") : undefined}
              />
            </div>

            <div className="mt-6 p-4 rounded-lg border border-orange-500/30 bg-orange-900/20">
              <div className="flex items-start gap-3">
                <Icon icon="solar:danger-triangle-bold" className="w-6 h-6 text-orange-400 flex-shrink-0 mt-1" />
                <div>
                  <h4 className="text-sm font-smallcaps text-orange-300 mb-2">
                    {t("settings.hooks.warning.title")}
                  </h4>
                  <p className="text-sm text-orange-200/80 font-minecraft">
                    {t("settings.hooks.warning.description")}
                  </p>
                </div>
              </div>
            </div>

            <div className="mt-6 p-4 rounded-lg border border-[#ffffff20] bg-black/10">
              <div className="flex items-start gap-3">
                <Icon icon="solar:info-circle-bold" className="w-6 h-6 text-blue-400 flex-shrink-0 mt-1" />
                <div>
                  <h4 className="text-sm font-smallcaps text-blue-300 mb-2">
                    {t("settings.hooks.examples.title")}
                  </h4>
                  <div className="space-y-2 text-sm text-blue-200/80 font-minecraft">
                    <p><strong>Pre-Launch:</strong> <code>echo "Starting game..."</code></p>
                    <p><strong>Wrapper:</strong> <code>firejail</code> or <code>gamemoderun</code></p>
                    <p><strong>Post-Exit:</strong> <code>notify-send "Game finished"</code></p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </SettingsSection>

      <SettingsSection
        id="settings-section-about"
        title={t("settings.about.title")}
        icon="solar:info-circle-bold"
        keywords={kw("settings.about.title", "about", "info", "version", "license", "licenses", "lizenz", "lizenzen", "gpl", "source", "quellcode", "credits")}
      >
        <div className="space-y-3 py-3">
          <p className="text-sm text-white/80 font-minecraft">
            {BRAND.name} {appVersion ? `v${appVersion}` : ""}
          </p>
          <p className="text-sm text-white/60 font-minecraft">{t("about.attribution")}</p>
          <div className="flex flex-wrap gap-3">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void openExternalUrl(BRAND.sourceUrl)}
              icon={<Icon icon="solar:code-bold" className="w-5 h-5" />}
            >
              {t("about.source_code")}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void openExternalUrl(`${BRAND.sourceUrl}/blob/main/launcher/LICENSE`)}
              icon={<Icon icon="solar:document-text-bold" className="w-5 h-5" />}
            >
              {t("settings.licenses.view")}
            </Button>
          </div>
        </div>
      </SettingsSection>

      {confirmDialog}
    </div>
  );
}

/** Textfeld, das erst beim Verlassen (oder mit Enter) übernimmt. */
function HookInput({
  value,
  onCommit,
  ...rest
}: { value: string; onCommit: (value: string) => void } & Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
>) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  return (
    <input
      type="text"
      {...rest}
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={() => onCommit(draft)}
      onKeyDown={(e) => {
        if (e.key === "Enter") (e.target as HTMLInputElement).blur();
      }}
    />
  );
}
