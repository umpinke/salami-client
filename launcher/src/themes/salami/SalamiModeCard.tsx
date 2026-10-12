import { useTranslation } from "react-i18next";
import { toast } from "react-hot-toast";
import { ToggleSwitch } from "../../components/ui/ToggleSwitch";
import { SALAMI_COLORS } from "./salami-theme";
import { SalamiSlice } from "./SalamiSlice";
import { useSalamiMode } from "./useSalamiMode";

/** Großer Schalter für den Salami-Modus (oben in Einstellungen → Darstellung). */
export function SalamiModeCard() {
  const { t } = useTranslation();
  const { enabled, setEnabled } = useSalamiMode();

  const toggle = (value: boolean) => {
    setEnabled(value);
    toast.success(t(value ? "salami.mode.enabled" : "salami.mode.disabled"));
  };

  return (
    <div
      className="relative overflow-hidden rounded-lg border-2 p-4 flex items-center gap-4 transition-colors"
      style={{
        borderColor: enabled ? SALAMI_COLORS.red : "rgba(255,255,255,0.1)",
        background: enabled
          ? `linear-gradient(135deg, ${SALAMI_COLORS.red}55, ${SALAMI_COLORS.pepper}cc)`
          : "rgba(0,0,0,0.2)",
      }}
    >
      {enabled && <div className="absolute inset-0 salami-fat-spots opacity-60 pointer-events-none" />}
      <SalamiSlice size={56} className={enabled ? "salami-spin shrink-0" : "shrink-0 opacity-70"} />
      <div className="relative flex-1 min-w-0">
        <div className="font-minecraft text-2xl text-white lowercase">{t("salami.mode.title")}</div>
        <p className="text-sm text-white/70 mt-1">{t("salami.mode.description")}</p>
        <p className="text-xs text-white/50 mt-1">{t("salami.mode.game_hint")}</p>
      </div>
      <div className="relative">
        <ToggleSwitch checked={enabled} onChange={toggle} size="lg" />
      </div>
    </div>
  );
}
