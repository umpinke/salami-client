import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { CONTENT_NAV_KEYS } from "../../../components/profiles/v3/ProfileLeftRailV3";
import type { LookProfileDetailProps } from "../types";

/**
 * Profilseite der Theke: das Profil als Produktkarte aus Butterbrotpapier (Siegel, Name, Etiketten,
 * Stempel zum Starten), darunter Karteireiter und der Bogen mit dem Inhalt – Mods als
 * „Zutatenliste“. Rechts ein Kassenbon mit Kennzahlen und den Profil-Aktionen.
 */
export function ThekeProfileDetail({
  profile,
  nameHtml,
  backLabel,
  onBack,
  icon,
  chips,
  launch,
  onEdit,
  stats,
  actions,
  moreMenu,
  nav,
  content,
}: LookProfileDetailProps) {
  const { t } = useTranslation();
  // Listen stehen auf Papier; Screenshots und Logs bleiben auf der dunklen Tafel (Bilder, Konsole).
  const onPaper = CONTENT_NAV_KEYS.includes(nav.active) || nav.active === "worlds";
  const active = nav.items.find((item) => item.key === nav.active);
  const sheetTitle = nav.active === "mods" ? t("theke.profile.ingredients") : active?.label;
  const modCount = nav.items.find((item) => item.key === "mods")?.count;

  return (
    <div className="theke-profile">
      <button type="button" className="theke-back" onClick={onBack}>
        <Icon icon="solar:arrow-left-linear" className="h-4 w-4" />
        {backLabel}
      </button>

      <div className="theke-profile-body">
        <section className="theke-card">
          <span className="theke-tape" aria-hidden />
          <header className="theke-card-head">
            <div className="theke-card-seal">{icon}</div>
            <div className="theke-card-title">
              <h1 dangerouslySetInnerHTML={{ __html: nameHtml }} />
              <div className="theke-card-chips">{chips}</div>
            </div>
            <button
              type="button"
              className="theke-card-stamp"
              data-running={launch.isLaunching || undefined}
              onClick={launch.onLaunch}
            >
              <span className="theke-card-stamp-label">
                {launch.isLaunching ? t("profiles.stop") : t("profiles.play")}
              </span>
              <small>{launch.isLaunching ? t("theke.profile.stamp_running") : t("theke.play.stamp_sub")}</small>
            </button>
          </header>

          <nav className="theke-dividers" role="tablist">
            {nav.items.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={item.key === nav.active}
                className="theke-divider"
                data-group={item.group}
                onClick={() => nav.onChange(item.key)}
              >
                {typeof item.count === "number" && item.count > 0 && <b>{item.count}</b>}
                {item.label}
              </button>
            ))}
          </nav>

          <div className={`theke-sheet ${onPaper ? "theke-sheet--paper" : "theke-sheet--slate"}`}>
            {onPaper && sheetTitle && <h2 className="theke-sheet-title">{sheetTitle}</h2>}
            <div className="theke-sheet-content">{content}</div>
          </div>
        </section>

        <aside className="theke-receipt">
          <h3>{t("theke.profile.receipt")}</h3>
          <div className="theke-receipt-sub">{t("theke.profile.receipt_sub", { version: profile.game_version })}</div>

          <div className="theke-receipt-row">
            <span>{t("theke.profile.last_played")}</span>
            <i aria-hidden />
            <span>{stats.lastPlayed}</span>
          </div>
          <div className="theke-receipt-row" data-muted={!stats.hasPlaytime || undefined}>
            <span>{t("theke.profile.playtime")}</span>
            <i aria-hidden />
            <span>{stats.playtime}</span>
          </div>
          <div className="theke-receipt-row">
            <span>{t("theke.profile.size")}</span>
            <i aria-hidden />
            <span>{stats.disk ?? "…"}</span>
          </div>
          {typeof modCount === "number" && modCount > 0 && (
            <div className="theke-receipt-row">
              <span>{t("theke.profile.mods")}</span>
              <i aria-hidden />
              <span>{modCount}</span>
            </div>
          )}

          {launch.isLaunching && (
            <div className="theke-receipt-status">
              <Icon icon="svg-spinners:ring-resize" className="h-4 w-4 flex-shrink-0" />
              <span title={launch.statusMessage ?? undefined}>{launch.statusMessage || t("theke.profile.status")}</span>
            </div>
          )}

          <hr />

          <div className="theke-receipt-actions">
            <button type="button" className="theke-receipt-btn theke-receipt-btn--hot" onClick={onEdit}>
              <Icon icon="solar:settings-bold" className="h-4 w-4" />
              {t("theke.profile.settings")}
            </button>
            <button type="button" className="theke-receipt-btn" onClick={actions.onOpenFolder}>
              <Icon icon="solar:folder-linear" className="h-4 w-4" />
              {t("theke.profile.folder")}
            </button>
            <button type="button" className="theke-receipt-btn" onClick={actions.onDuplicate}>
              <Icon icon="solar:copy-linear" className="h-4 w-4" />
              {t("theke.profile.duplicate")}
            </button>
            <button type="button" className="theke-receipt-btn" onClick={actions.onExport}>
              <Icon icon="solar:upload-linear" className="h-4 w-4" />
              {t("theke.profile.export")}
            </button>
            <button type="button" className="theke-receipt-btn" onClick={actions.onSyncPacks}>
              <Icon icon="solar:refresh-circle-linear" className="h-4 w-4" />
              {t("syncPacks.title")}
            </button>
            <div className="theke-receipt-more">{moreMenu}</div>
          </div>

          <div className="theke-barcode" aria-hidden />
          <p className="theke-receipt-thanks">{t("theke.profile.thanks")}</p>
        </aside>
      </div>
    </div>
  );
}
