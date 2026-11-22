import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { LookSearchCardProps } from "../types";

/**
 * Ein Suchergebnis als Ware im Regal: Papierschachtel mit Bild und Namen, Preisschild mit den
 * Downloads, daneben Warenkorb und „Installieren“. Ein Klick auf die Ware öffnet die Detailseite.
 */
export function ThekeSearchCard({
  hit,
  installStatus,
  downloadsLabel,
  categories,
  installBusy,
  alreadyInstalled,
  installDisabled,
  onInstall,
  onOpen,
  onOpenAuthor,
  cartButton,
}: LookSearchCardProps) {
  const { t } = useTranslation();
  const initial = String(hit.title ?? "?").trim().charAt(0).toUpperCase() || "?";

  return (
    <div className="theke-ware-slot">
      <div
        role="button"
        tabIndex={0}
        className="theke-ware"
        title={t("modrinth.view_details", { title: hit.title })}
        onClick={onOpen}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onOpen();
          }
        }}
      >
        {installStatus?.is_included_in_norisk_pack ? (
          <span className="theke-ware-flag">{t("theke.ware.salami_pack")}</span>
        ) : (
          alreadyInstalled && <span className="theke-ware-flag theke-ware-flag--done">{t("theke.ware.in_profile")}</span>
        )}

        <div className="theke-ware-top">
          <div className="theke-ware-icon">
            {hit.icon_url ? <img src={hit.icon_url} alt="" loading="lazy" /> : <span>{initial}</span>}
          </div>
          <div className="min-w-0">
            <div className="theke-ware-name">{hit.title}</div>
            {hit.author && (
              <button
                type="button"
                className="theke-ware-author"
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenAuthor();
                }}
              >
                {t("theke.ware.by", { author: hit.author })}
              </button>
            )}
          </div>
        </div>

        <p className="theke-ware-desc">{hit.description}</p>

        {categories.length > 0 && (
          <div className="theke-ware-cats">
            {categories.map((category) => (
              <span key={category}>{category}</span>
            ))}
          </div>
        )}

        <div className="theke-ware-bottom">
          <span className="theke-price" title={t("theke.ware.downloads", { count: hit.downloads })}>
            <Icon icon="solar:download-minimalistic-bold" className="h-3 w-3" />
            {downloadsLabel}
          </span>
          <span className="theke-ware-actions" onClick={(e) => e.stopPropagation()}>
            {cartButton}
            <button
              type="button"
              className="theke-ware-buy"
              data-done={alreadyInstalled || undefined}
              disabled={installDisabled}
              onClick={onInstall}
            >
              <Icon
                icon={installBusy ? "solar:refresh-bold" : alreadyInstalled ? "solar:check-circle-bold" : "solar:download-minimalistic-bold"}
                className={installBusy ? "h-3.5 w-3.5 animate-spin" : "h-3.5 w-3.5"}
              />
              {installBusy ? t("modrinth.installing") : alreadyInstalled ? t("modrinth.installed") : t("modrinth.install")}
            </button>
          </span>
        </div>
      </div>
    </div>
  );
}
