import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { LookSearchCardProps } from "../types";

/**
 * Ein Eintrag im gedruckten Katalog: oben eine doppelte Linie, darüber in Kapitälchen die Sorte
 * und rechts die Downloads, dann der Name in großer Serife mit dem Bild als kleinem Stich im
 * Rahmen, der Hersteller kursiv, die Beschreibung, und unter einer Haarlinie Warenkorb und
 * „Installieren“. Ein Klick auf den Eintrag öffnet die Detailseite.
 */
export function SalumeriaSearchCard({
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
  const inPack = !!installStatus?.is_included_in_norisk_pack;

  return (
    <div className="sal-catalog-slot">
      <article
        role="button"
        tabIndex={0}
        className="sal-catalog"
        title={t("modrinth.view_details", { title: hit.title })}
        onClick={onOpen}
        onKeyDown={(e) => {
          if (e.target !== e.currentTarget) return;
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onOpen();
          }
        }}
      >
        <div className="sal-catalog__kicker">
          <span className="sal-catalog__sort">
            {categories.length > 0 ? categories.join(" · ") : hit.source}
          </span>
          <span
            className="sal-catalog__downloads"
            title={t("salami.look.salumeria.catalog.downloads", { count: hit.downloads })}
          >
            <Icon icon="solar:download-minimalistic-linear" className="h-3 w-3" />
            {downloadsLabel}
          </span>
        </div>

        <div className="sal-catalog__head">
          <div className="min-w-0 flex-1">
            <h3 className="sal-catalog__name">{hit.title}</h3>
            {hit.author && (
              <button
                type="button"
                className="sal-catalog__author"
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenAuthor();
                }}
              >
                {t("salami.look.salumeria.catalog.by", { author: hit.author })}
              </button>
            )}
          </div>
          <div className="sal-catalog__plate" aria-hidden>
            {hit.icon_url ? <img src={hit.icon_url} alt="" loading="lazy" /> : <span>{initial}</span>}
          </div>
        </div>

        <p className="sal-catalog__desc">{hit.description}</p>

        <div className="sal-catalog__foot">
          <span className="sal-catalog__note">
            {inPack
              ? t("salami.look.salumeria.catalog.in_pack")
              : alreadyInstalled
                ? t("salami.look.salumeria.catalog.in_profile")
                : null}
          </span>
          <span className="sal-catalog__actions" onClick={(e) => e.stopPropagation()}>
            {cartButton}
            <button
              type="button"
              className="sal-catalog__install"
              data-done={alreadyInstalled || undefined}
              disabled={installDisabled}
              onClick={onInstall}
            >
              <Icon
                icon={
                  installBusy
                    ? "solar:refresh-linear"
                    : alreadyInstalled
                      ? "solar:check-read-linear"
                      : "solar:download-minimalistic-linear"
                }
                className={installBusy ? "h-3.5 w-3.5 animate-spin" : "h-3.5 w-3.5"}
              />
              {installBusy ? t("modrinth.installing") : alreadyInstalled ? t("modrinth.installed") : t("modrinth.install")}
            </button>
          </span>
        </div>
      </article>
    </div>
  );
}
