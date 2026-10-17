import { useTranslation } from "react-i18next";
import { useOnlineCount } from "../../hooks/useOnlineCount";

/** „● 42 Spieler online“ – blendet sich aus, solange der Online-Zähler keine Zahl hat. */
export function OnlineCountBadge() {
  const { t } = useTranslation();
  const online = useOnlineCount();
  if (online === null) return null;

  return (
    <div
      className="flex items-center gap-2 rounded-full border border-white/10 bg-black/50 px-3 py-1.5 backdrop-blur-md"
      title={t("salami.play.online_hint")}
    >
      <span className="relative flex h-2.5 w-2.5">
        <span className="absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-60 motion-safe:animate-ping" />
        <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-green-400" />
      </span>
      <span className="font-minecraft text-sm text-white/80">{t("salami.play.online", { count: online })}</span>
    </div>
  );
}
