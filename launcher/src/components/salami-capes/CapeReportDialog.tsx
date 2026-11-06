import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { CAPE_REPORT_REASONS, reportCape, type CapeReportReason } from "../../services/salami-cape-service";
import { CapeFace } from "./CapeFace";
import "./i18n";

const MAX_NOTE = 200;

interface Props {
  /** Cape-ID auf dem Cape-Server. */
  id: string;
  /** Bild als Data-URL, falls schon geladen. */
  image: string | null;
  onClose: () => void;
  /** Gemeldet (oder schon früher gemeldet) – die Galerie blendet das Cape dann aus. */
  onReported: (id: string) => void;
}

/** Dialog zum Melden eines Galerie-Capes, das gegen die Cape-Regeln verstößt. */
export function CapeReportDialog({ id, image, onClose, onReported }: Props) {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor);
  const [reason, setReason] = useState<CapeReportReason | null>(null);
  const [note, setNote] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !sending) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, sending]);

  const send = async () => {
    if (!reason || sending) return;
    setSending(true);
    try {
      const status = await reportCape(id, reason, note.trim());
      toast.success(t(status === "already_reported" ? "salami.capes.report.already" : "salami.capes.report.sent"));
      onReported(id);
    } catch (error) {
      toast.error(t("salami.capes.error", { error: error instanceof Error ? error.message : String(error) }));
      setSending(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-6 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !sending) onClose();
      }}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="cape-report-title"
        className="flex w-full max-w-[460px] flex-col gap-4 rounded-2xl border border-white/10 bg-[#1d1412] p-5 shadow-2xl"
        onSubmit={(e) => {
          e.preventDefault();
          void send();
        }}
      >
        <header className="flex items-center gap-3">
          <Icon icon="solar:flag-bold" className="h-6 w-6 text-red-300" />
          <h2 id="cape-report-title" className="text-lg font-semibold text-white">
            {t("salami.capes.report.title")}
          </h2>
        </header>

        <div className="flex gap-4">
          <div className="flex h-[112px] w-[70px] shrink-0 items-center justify-center rounded-lg bg-black/30">
            {image ? <CapeFace src={image} width={70} /> : <Icon icon="solar:gallery-bold" className="h-8 w-8 text-white/40" />}
          </div>
          <p className="text-sm leading-relaxed text-white/75">{t("salami.capes.report.intro")}</p>
        </div>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-semibold text-white">{t("salami.capes.report.reason")}</legend>
          {CAPE_REPORT_REASONS.map((value) => (
            <label
              key={value}
              className={cn(
                "flex cursor-pointer items-center gap-3 rounded-xl border px-3 py-2 text-sm text-white transition-colors",
                reason === value ? "bg-white/10" : "border-white/10 bg-black/25 hover:bg-white/5",
              )}
              style={reason === value ? { borderColor: accent.value } : undefined}
            >
              <input
                type="radio"
                name="cape-report-reason"
                value={value}
                checked={reason === value}
                onChange={() => setReason(value)}
                className="accent-current"
                style={{ color: accent.value }}
              />
              {t(`salami.capes.report.reasons.${value}`)}
            </label>
          ))}
        </fieldset>

        <label className="flex flex-col gap-1.5 text-sm text-white/80">
          {t("salami.capes.report.note")}
          <textarea
            value={note}
            maxLength={MAX_NOTE}
            rows={2}
            onChange={(e) => setNote(e.target.value)}
            placeholder={t("salami.capes.report.note_placeholder")}
            className="resize-none rounded-lg bg-black/40 px-3 py-2 text-sm text-white outline-none placeholder:text-white/35 focus:ring-2 focus:ring-white/20"
          />
        </label>

        <p className="text-xs leading-relaxed text-white/55">{t("salami.capes.report.hint")}</p>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={sending}
            className="rounded-xl bg-white/10 px-4 py-2 text-sm font-semibold text-white hover:bg-white/15 disabled:opacity-50"
          >
            {t("salami.capes.report.cancel")}
          </button>
          <button
            type="submit"
            disabled={!reason || sending}
            className="flex items-center gap-2 rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {sending ? <Icon icon="svg-spinners:ring-resize" className="h-4 w-4" /> : <Icon icon="solar:flag-bold" className="h-4 w-4" />}
            {t("salami.capes.report.send")}
          </button>
        </div>
      </form>
    </div>
  );
}
