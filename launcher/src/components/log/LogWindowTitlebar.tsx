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
      className="look-log-titlebar flex items-center justify-between h-11 pl-4 pr-1 select-none border-b border-white/[0.08]"
      data-tauri-drag-region
    >
      {/* Links: Wortmarke und Fenstertitel */}
      <div className="flex items-baseline gap-2.5 flex-1 min-w-0 pointer-events-none">
        <img src="/salami-logo-64.png" alt="" className="w-5 h-5 rounded-full self-center" />
        <span className="look-log-brand font-minecraft text-xl leading-none text-white">Salami</span>
        <span className="font-minecraft text-xl leading-none text-white/20">/</span>
        <span className="look-log-title font-minecraft text-xl leading-none text-white/55 truncate">{displayTitle}</span>
      </div>

      {/* Right: Window Controls */}
      <div className="flex items-center">
        {/* Minimize */}
        <button
          onClick={handleMinimize}
          className="w-10 h-9 flex items-center justify-center hover:bg-white/10 transition-colors"
          title={t('window.minimize')}
        >
          <Icon icon="mdi:minus" className="w-4 h-4 text-white/70" />
        </button>

        {/* Maximize */}
        <button
          onClick={handleMaximize}
          className="w-10 h-9 flex items-center justify-center hover:bg-white/10 transition-colors"
          title={t('window.maximize')}
        >
          <Icon icon="mdi:checkbox-blank-outline" className="w-3.5 h-3.5 text-white/70" />
        </button>

        {/* Close */}
        <button
          onClick={handleClose}
          className="w-10 h-9 flex items-center justify-center hover:bg-red-500/80 transition-colors"
          title={t('window.close')}
        >
          <Icon icon="mdi:close" className="w-4 h-4 text-white/70" />
        </button>
      </div>
    </div>
  );
}
