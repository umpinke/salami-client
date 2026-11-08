"use client";

import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { SettingsSection } from "../ui/settings/SettingsSection";
import { SettingRow } from "../ui/settings/SettingRow";
import { ToggleSwitch } from "../ui/ToggleSwitch";
import { Button } from "../ui/buttons/Button";
import { useGlobalModal } from "../../hooks/useGlobalModal";
import { useConfirmDialog } from "../../hooks/useConfirmDialog";
import { openExternalUrl } from "../../services/tauri-service";
import { useSettingsConfig, useSettingsKeywords } from "../tabs/settings/settings-context";
import { PairPhoneModal } from "./PairPhoneModal";
import { SalamiAppService, errorText, type SalamiAppStatus } from "./salami-app-service";
import "./i18n";

const MODAL_ID = "salami-app-pair";

/** Einstellungen → Allgemein: Sync-Schalter, „Handy koppeln“ und „Daten löschen“. */
export function SalamiAppSettings() {
  const { t } = useTranslation();
  const kw = useSettingsKeywords();
  const { tempConfig, setTempConfig, saving } = useSettingsConfig();
  const { showModal, hideModal } = useGlobalModal();
  const { confirm, confirmDialog } = useConfirmDialog();
  const [status, setStatus] = useState<SalamiAppStatus | null>(null);
  const [deleting, setDeleting] = useState(false);

  const syncOn = tempConfig?.salami_app_sync ?? true;

  const refresh = useCallback(async () => {
    try {
      setStatus(await SalamiAppService.status());
    } catch {
      setStatus(null);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh, syncOn]);

  if (!status?.available) return null;

  const openPairing = () => {
    showModal(
      MODAL_ID,
      <PairPhoneModal
        status={{ ...status, enabled: syncOn }}
        onClose={() => {
          hideModal(MODAL_ID);
          void refresh();
        }}
      />,
    );
  };

  const deleteData = async () => {
    const ok = await confirm({
      title: t("salami.app.delete.confirm_title"),
      message: t("salami.app.delete.confirm_message"),
      confirmText: t("salami.app.delete.confirm_button"),
      type: "danger",
    });
    if (!ok) return;
    setDeleting(true);
    try {
      await SalamiAppService.deleteData();
      // Der Launcher hat den Schalter schon ausgestellt; hier nachziehen, damit das nächste
      // automatische Speichern ihn nicht wieder anstellt.
      if (tempConfig) setTempConfig({ ...tempConfig, salami_app_sync: false });
      toast.success(t("salami.app.delete.done"));
    } catch (error) {
      toast.error(t("salami.app.delete.failed", { error: errorText(error) }));
    } finally {
      setDeleting(false);
      void refresh();
    }
  };

  return (
    <SettingsSection
      id="settings-section-salami-app"
      title={t("salami.app.title")}
      icon="solar:smartphone-bold"
      keywords={kw("salami.app.title", "app", "handy", "phone", "smartphone", "android", "koppeln", "pair", "sync", "screenshots", "spielzeit", "playtime", "streak")}
      description={t("salami.app.description")}
    >
      <SettingRow
        label={t("salami.app.sync")}
        description={
          <>
            {t("salami.app.sync.description")}{" "}
            <button
              type="button"
              className="underline underline-offset-2 hover:text-white"
              onClick={() => void openExternalUrl(status.privacyUrl)}
            >
              {t("salami.app.privacy")}
            </button>
          </>
        }
        searchKeywords={kw("salami.app.sync", "sync", "synchronisieren", "screenshots", "spielzeit", "playtime", "datenschutz", "privacy")}
        disabled={saving}
      >
        <ToggleSwitch
          checked={syncOn}
          onChange={(checked) => tempConfig && setTempConfig({ ...tempConfig, salami_app_sync: checked })}
          disabled={saving}
          size="md"
        />
      </SettingRow>

      {syncOn && status.storageFull && (
        <div className="flex gap-2 my-2 rounded-lg border border-amber-400/30 bg-amber-400/10 p-3 font-minecraft text-xs text-amber-100">
          <Icon icon="solar:danger-triangle-bold" className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span>{t("salami.app.storage_full")}</span>
        </div>
      )}

      <SettingRow
        label={t("salami.app.pair")}
        description={
          status.accountName
            ? t("salami.app.pair.description", { name: status.accountName })
            : t("salami.app.pair.description_no_account")
        }
        searchKeywords={kw("salami.app.pair", "koppeln", "pair", "qr", "code", "handy", "phone")}
      >
        <Button
          size="sm"
          variant="secondary"
          onClick={openPairing}
          disabled={!status.accountName}
          icon={<Icon icon="solar:qr-code-bold" />}
        >
          {t("salami.app.pair")}
        </Button>
      </SettingRow>

      <SettingRow
        label={t("salami.app.delete")}
        description={t("salami.app.delete.description")}
        searchKeywords={kw("salami.app.delete", "löschen", "delete", "daten", "data", "dsgvo", "gdpr")}
      >
        <Button
          size="sm"
          variant="destructive"
          onClick={() => void deleteData()}
          disabled={deleting || !status.accountName}
          icon={<Icon icon={deleting ? "solar:refresh-bold" : "solar:trash-bin-trash-bold"} className={deleting ? "animate-spin" : undefined} />}
        >
          {t("salami.app.delete")}
        </Button>
      </SettingRow>
      {confirmDialog}
    </SettingsSection>
  );
}
