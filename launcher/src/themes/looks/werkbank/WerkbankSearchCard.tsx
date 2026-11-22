import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { LookSearchCardProps } from "../types";
import { WerkbankIcon } from "./icons";

/**
 * Ein Suchergebnis als Gegenstand im Kreativ-Inventar: ein großer Slot mit Bild, Name und
 * Ersteller, darunter die Beschreibung grau wie die Zeilen eines Gegenstands-Tooltips und die
 * Kategorien blau wie der Mod-Name darin. Unten die Downloads als grüne Zahl, Warenkorb und ein
 * gefaster „Installieren“-Knopf. Ein Klick auf den Gegenstand öffnet die Detailseite.
 */
export function WerkbankSearchCard({
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
    <div className="wb-sc-cell">
      <div
        role="button"
        tabIndex={0}
        className="wb-sc"
        data-installed={alreadyInstalled || undefined}
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
        <div className="wb-sc__top">
          <span className="wb-sc__slot">
            {hit.icon_url ? <img src={hit.icon_url} alt="" loading="lazy" /> : <span className="wb-sc__initial">{initial}</span>}
          </span>
          <div className="wb-sc__title">
            <span className="wb-sc__name">{hit.title}</span>
            {hit.author && (
              <button
                type="button"
                className="wb-sc__author"
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenAuthor();
                }}
              >
                {t("salami.werkbank.ware.by", { author: hit.author })}
              </button>
            )}
          </div>
        </div>

        {(inPack || alreadyInstalled) && (
          <span className={inPack ? "wb-sc__flag" : "wb-sc__flag wb-sc__flag--done"}>
            {inPack ? t("salami.werkbank.ware.salami_pack") : t("salami.werkbank.ware.in_profile")}
          </span>
        )}

        <p className="wb-sc__desc">{hit.description}</p>

        {categories.length > 0 && <p className="wb-sc__cats">{categories.join(" · ")}</p>}

        <div className="wb-sc__bottom">
          <span className="wb-sc__downloads" title={t("salami.werkbank.ware.downloads", { count: hit.downloads })}>
            <WerkbankIcon name="download" className="wb-sc__dl-icon" />
            {downloadsLabel}
          </span>
          <span className="wb-sc__actions" onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
            {cartButton}
            <button
              type="button"
              className="wb-sc__install"
              data-done={alreadyInstalled || undefined}
              disabled={installDisabled}
              onClick={onInstall}
            >
              {installBusy ? (
                <Icon icon="solar:refresh-bold" className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <WerkbankIcon name={alreadyInstalled ? "check" : "download"} className="h-3.5 w-3.5" />
              )}
              <span>{installBusy ? t("modrinth.installing") : alreadyInstalled ? t("modrinth.installed") : t("modrinth.install")}</span>
            </button>
          </span>
        </div>
      </div>
    </div>
  );
}
