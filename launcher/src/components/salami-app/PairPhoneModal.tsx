"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { Modal } from "../ui/Modal";
import { Button } from "../ui/buttons/Button";
import { useConfirmDialog } from "../../hooks/useConfirmDialog";
import { openExternalUrl } from "../../services/tauri-service";
import { useThemeStore } from "../../store/useThemeStore";
import {
  SalamiAppService,
  errorText,
  type SalamiAppStatus,
  type SalamiDevice,
  type SalamiPairCode,
} from "./salami-app-service";
import "./i18n";

/** Neue Geräte während der Code sichtbar ist, alle paar Sekunden nachsehen. */
const DEVICE_POLL_MS = 5000;

interface PairPhoneModalProps {
  status: SalamiAppStatus;
  onClose: () => void;
}

function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/** „482913“ → „482 913“, damit man den Code leichter abtippt. */
function spacedCode(code: string): string {
  return code.length === 6 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
}

export function PairPhoneModal({ status, onClose }: PairPhoneModalProps) {
  const { t, i18n } = useTranslation();
  const accentColor = useThemeStore((s) => s.accentColor);
  const { confirm, confirmDialog } = useConfirmDialog();
  const [code, setCode] = useState<SalamiPairCode | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [loadingCode, setLoadingCode] = useState(false);
  const [devices, setDevices] = useState<SalamiDevice[] | null>(null);
  const [devicesError, setDevicesError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const knownDevices = useRef<Set<string> | null>(null);

  const formatDate = useCallback(
    (iso: string | null) => {
      if (!iso) return "";
      const date = new Date(iso);
      if (Number.isNaN(date.getTime())) return "";
      return date.toLocaleString(i18n.language, { dateStyle: "medium", timeStyle: "short" });
    },
    [i18n.language],
  );

  const newCode = useCallback(async () => {
    setLoadingCode(true);
    setCodeError(null);
    try {
      setCode(await SalamiAppService.pairCode());
    } catch (error) {
      setCode(null);
      setCodeError(errorText(error));
    } finally {
      setLoadingCode(false);
    }
  }, []);

  const loadDevices = useCallback(async () => {
    try {
      const list = await SalamiAppService.devices();
      setDevicesError(null);
      setDevices(list);
      const known = knownDevices.current;
      const fresh = known ? list.filter((device) => !known.has(device.id)) : [];
      knownDevices.current = new Set(list.map((device) => device.id));
      if (fresh.length > 0) {
        for (const device of fresh) {
          toast.success(t("salami.app.modal.paired", { name: device.name || t("salami.app.devices.unknown") }));
        }
        // Der Code ist jetzt verbraucht – gleich einen frischen für das nächste Handy
        void newCode();
      }
    } catch (error) {
      setDevicesError(errorText(error));
    }
  }, [newCode, t]);

  // Beim Öffnen genau einen Code erzeugen (nicht bei jedem Neuzeichnen)
  const opened = useRef(false);
  useEffect(() => {
    if (opened.current) return;
    opened.current = true;
    void newCode().then(loadDevices);
  }, [newCode, loadDevices]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const expiresAt = code ? new Date(code.expiresAt).getTime() : 0;
  const remaining = expiresAt - now;
  const expired = !!code && remaining <= 0;

  useEffect(() => {
    if (!code || expired) return;
    const timer = window.setInterval(() => void loadDevices(), DEVICE_POLL_MS);
    return () => window.clearInterval(timer);
  }, [code, expired, loadDevices]);

  const removeDevice = async (device: SalamiDevice) => {
    const name = device.name || t("salami.app.devices.unknown");
    const ok = await confirm({
      title: t("salami.app.devices.remove_title"),
      message: t("salami.app.devices.remove_confirm", { name }),
      confirmText: t("salami.app.devices.remove"),
      type: "danger",
    });
    if (!ok) return;
    try {
      await SalamiAppService.removeDevice(device.id);
      toast.success(t("salami.app.devices.removed"));
      await loadDevices();
    } catch (error) {
      toast.error(errorText(error));
    }
  };

  const qrSrc = code ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(code.qrSvg)}` : null;

  return (
    <Modal
      title={t("salami.app.modal.title")}
      titleIcon={<Icon icon="solar:smartphone-bold" className="w-6 h-6" style={{ color: accentColor.value }} />}
      onClose={onClose}
      width="md"
    >
      <div className="p-6 space-y-5 font-minecraft">
        {status.accountName && (
          <p className="text-sm text-white/60">{t("salami.app.modal.account", { name: status.accountName })}</p>
        )}
        {!status.enabled && (
          <div className="flex gap-2 rounded-lg border border-amber-400/30 bg-amber-400/10 p-3 text-xs text-amber-100">
            <Icon icon="solar:info-circle-bold" className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{t("salami.app.modal.sync_off")}</span>
          </div>
        )}

        <div className="flex flex-col sm:flex-row items-center gap-6 rounded-xl border border-white/10 bg-black/25 p-5">
          <div className="w-44 h-44 flex-shrink-0 rounded-lg bg-white p-2 flex items-center justify-center">
            {qrSrc && !expired ? (
              <img
                src={qrSrc}
                alt={code?.url ?? ""}
                className="w-full h-full"
                style={{ imageRendering: "pixelated" }}
                draggable={false}
              />
            ) : (
              <Icon
                icon={loadingCode ? "solar:refresh-bold" : "solar:qr-code-bold"}
                className={`w-16 h-16 text-black/25 ${loadingCode ? "animate-spin" : ""}`}
              />
            )}
          </div>

          <div className="flex-1 min-w-0 text-center sm:text-left space-y-3">
            <p className="text-xs uppercase tracking-wider text-white/50">{t("salami.app.modal.code")}</p>
            {code && !expired ? (
              <>
                <p className="text-5xl leading-none tracking-[0.15em] text-white select-all tabular-nums">
                  {spacedCode(code.code)}
                </p>
                <p className="text-xs text-white/50">
                  {t("salami.app.modal.valid_for", { time: formatRemaining(remaining) })}
                </p>
              </>
            ) : loadingCode ? (
              <p className="text-sm text-white/60">{t("salami.app.modal.loading")}</p>
            ) : codeError ? (
              <p className="text-sm text-red-300">{t("salami.app.modal.error", { error: codeError })}</p>
            ) : (
              <p className="text-sm text-white/60">{t("salami.app.modal.expired")}</p>
            )}
            {(expired || codeError) && !loadingCode && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void newCode()}
                icon={<Icon icon="solar:refresh-bold" />}
              >
                {codeError ? t("salami.app.modal.retry") : t("salami.app.modal.new_code")}
              </Button>
            )}
          </div>
        </div>

        <p className="text-sm text-white/70">{t("salami.app.modal.steps")}</p>

        <button
          type="button"
          onClick={() => void openExternalUrl(status.downloadUrl)}
          className="w-full flex items-center gap-3 rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 px-4 py-3 text-left transition-colors"
        >
          <Icon icon="solar:download-minimalistic-bold" className="w-5 h-5 flex-shrink-0" style={{ color: accentColor.value }} />
          <span className="flex-1 min-w-0">
            <span className="block text-sm text-white">{t("salami.app.modal.get_app")}</span>
            <span className="block text-xs text-white/50">{t("salami.app.modal.get_app_hint")}</span>
          </span>
          <Icon icon="solar:arrow-right-up-linear" className="w-4 h-4 text-white/50" />
        </button>

        <div className="space-y-2">
          <h4 className="text-xs uppercase tracking-wider text-white/50">{t("salami.app.devices.title")}</h4>
          {devicesError ? (
            <p className="text-xs text-red-300">{t("salami.app.devices.failed", { error: devicesError })}</p>
          ) : devices === null ? null : devices.length === 0 ? (
            <p className="text-sm text-white/50">{t("salami.app.devices.empty")}</p>
          ) : (
            <ul className="space-y-2">
              {devices.map((device) => (
                <li
                  key={device.id}
                  className="flex items-center gap-3 rounded-lg border border-white/10 bg-black/20 px-3 py-2"
                >
                  <Icon icon="solar:smartphone-2-bold" className="w-5 h-5 text-white/60 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-white truncate">{device.name || t("salami.app.devices.unknown")}</p>
                    <p className="text-xs text-white/45 truncate">
                      {[
                        device.pairedAt && t("salami.app.devices.paired_at", { date: formatDate(device.pairedAt) }),
                        device.lastSeen && t("salami.app.devices.last_seen", { date: formatDate(device.lastSeen) }),
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                  </div>
                  <Button size="sm" variant="ghost" onClick={() => void removeDevice(device)}>
                    {t("salami.app.devices.remove")}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      {confirmDialog}
    </Modal>
  );
}
