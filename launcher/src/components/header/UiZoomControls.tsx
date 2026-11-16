import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { UI_SCALE_MAX, UI_SCALE_MIN, useUiScaleStore } from "../../store/ui-scale-store";

/** Lupe − / Prozent / Lupe + in der Kopfzeile: Größe der Launcher-Oberfläche. */
export function UiZoomControls() {
  const { t } = useTranslation();
  const scale = useUiScaleStore((s) => s.scale);
  const zoomIn = useUiScaleStore((s) => s.zoomIn);
  const zoomOut = useUiScaleStore((s) => s.zoomOut);
  const reset = useUiScaleStore((s) => s.reset);
  const button = "flex h-9 w-7 items-center justify-center text-white/70 transition-colors hover:text-white disabled:opacity-30 disabled:hover:text-white/70";

  return (
    <div className="hidden items-center rounded-lg border border-white/[0.09] bg-white/[0.045] min-[1140px]:flex">
      <button
        type="button"
        onClick={zoomOut}
        disabled={scale <= UI_SCALE_MIN}
        title={t("header.zoom_out")}
        aria-label={t("header.zoom_out")}
        className={button}
      >
        <Icon icon="solar:magnifer-zoom-out-linear" className="h-4 w-4" />
      </button>
      <button
        type="button"
        onClick={reset}
        title={t("header.zoom_reset")}
        aria-label={t("header.zoom_reset")}
        className="h-9 min-w-[2.4rem] px-0.5 text-xs tabular-nums text-white/70 transition-colors hover:text-white"
      >
        {Math.round(scale * 100)}%
      </button>
      <button
        type="button"
        onClick={zoomIn}
        disabled={scale >= UI_SCALE_MAX}
        title={t("header.zoom_in")}
        aria-label={t("header.zoom_in")}
        className={button}
      >
        <Icon icon="solar:magnifer-zoom-in-linear" className="h-4 w-4" />
      </button>
    </div>
  );
}
