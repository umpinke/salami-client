import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { getCurrentWindow } from "@tauri-apps/api/window";

const appWindow = getCurrentWindow();

interface LogWindowTitlebarProps {
  title?: string;
}

export function LogWindowTitlebar({ title }: LogWindowTitlebarProps) {
  const { t } = useTranslation();
  const displayTitle = title || t('logs.window_title');

  const handleMinimize = () => {
    appWindow.minimize();
  };

  const handleMaximize = () => {
    appWindow.toggleMaximize();
  };

  const handleClose = () => {
    appWindow.close();
  };

  return (
    <div
      className="flex items-center justify-between h-11 px-3 select-none border-b border-white/[0.06]"
      data-tauri-drag-region
    >
      {/* Left: Title */}
      <div className="flex items-center gap-2.5 flex-1 h-full pointer-events-none">
        <img src="/salami-logo-64.png" alt="" className="w-6 h-6 rounded-full" />
        <span className="text-sm font-semibold text-white">Salami Client</span>
        <span className="h-4 w-px bg-white/15" />
        <span className="text-sm text-white/60 truncate">{displayTitle}</span>
      </div>

      {/* Right: Window Controls */}
      <div className="flex items-center gap-1">
        {/* Minimize */}
        <button
          onClick={handleMinimize}
          className="w-8 h-8 flex items-center justify-center rounded hover:bg-white/10 transition-colors"
          title={t('window.minimize')}
        >
          <Icon icon="mdi:minus" className="w-4 h-4 text-white/70" />
        </button>

        {/* Maximize */}
        <button
          onClick={handleMaximize}
          className="w-8 h-8 flex items-center justify-center rounded hover:bg-white/10 transition-colors"
          title={t('window.maximize')}
        >
          <Icon icon="mdi:checkbox-blank-outline" className="w-3.5 h-3.5 text-white/70" />
        </button>

        {/* Close */}
        <button
          onClick={handleClose}
          className="w-8 h-8 flex items-center justify-center rounded hover:bg-red-500/80 transition-colors"
          title={t('window.close')}
        >
          <Icon icon="mdi:close" className="w-4 h-4 text-white/70" />
        </button>
      </div>
    </div>
  );
}
