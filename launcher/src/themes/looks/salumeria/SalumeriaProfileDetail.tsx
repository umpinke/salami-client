import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { CONTENT_NAV_KEYS } from "../../../components/profiles/v3/ProfileLeftRailV3";
import type { LookProfileDetailProps } from "../types";

const NUMERALS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];

/**
 * Profilseite der Salumeria als „Carta“: oben ein Etikett wie auf einer Salami – links das
 * Kleingedruckte (Reifezeit, Gewicht, zuletzt angeschnitten), in der Mitte Medaillon, Name in
 * großer Serife und die Etiketten, rechts der rote Knopf und die Dienste als kursive Zeile.
 * Darunter links das Verzeichnis der Gänge mit römischen Ziffern, rechts die cremefarbene Karte
 * mit dem Inhalt (Mods als Speisekarte). Screenshots und Logs bleiben auf dunklem Grund.
 */
export function SalumeriaProfileDetail({
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
  const onPaper = CONTENT_NAV_KEYS.includes(nav.active) || nav.active === "worlds";
  const active = nav.items.find((item) => item.key === nav.active);
  const contentItems = nav.items.filter((item) => item.group === "content");
  const worldItems = nav.items.filter((item) => item.group === "world");

  const course = (item: (typeof nav.items)[number], index: number) => {
    const selected = item.key === nav.active;
    return (
      <button
        key={item.key}
        type="button"
        role="tab"
        aria-selected={selected}
        className="sal-course"
        onClick={() => nav.onChange(item.key)}
      >
        <span className="sal-course__no" aria-hidden>
          {NUMERALS[index] ?? index + 1}
        </span>
        <span className="sal-course__name">{item.label}</span>
        {typeof item.count === "number" && item.count > 0 && <span className="sal-course__count">{item.count}</span>}
      </button>
    );
  };

  return (
    <div className="sal-carta-page">
      <header className="sal-etikett">
        <div className="sal-etikett__side sal-etikett__side--left">
          <button type="button" className="sal-back" onClick={onBack}>
            <Icon icon="solar:alt-arrow-left-linear" className="h-3.5 w-3.5" />
            {backLabel}
          </button>
          <dl className="sal-fineprint">
            <div title={t("salami.look.salumeria.profile.aged_hint")} data-muted={!stats.hasPlaytime || undefined}>
              <dt>{t("salami.look.salumeria.profile.aged")}</dt>
              <dd>{stats.hasPlaytime ? stats.playtime : t("salami.look.salumeria.profile.aged_none")}</dd>
            </div>
            <div title={t("salami.look.salumeria.profile.weight_hint")}>
              <dt>{t("salami.look.salumeria.profile.weight")}</dt>
              <dd>{stats.disk ?? "…"}</dd>
            </div>
            <div title={t("salami.look.salumeria.profile.sliced_hint")}>
              <dt>{t("salami.look.salumeria.profile.sliced")}</dt>
              <dd>{stats.lastPlayed}</dd>
            </div>
          </dl>
        </div>

        <div className="sal-etikett__centre">
          <div className="sal-medal">{icon}</div>
          <h1 className="sal-etikett__name" dangerouslySetInnerHTML={{ __html: nameHtml }} />
          <div className="sal-etikett__chips">{chips}</div>
        </div>

        <div className="sal-etikett__side sal-etikett__side--right">
          <button
            type="button"
            className="sal-slice"
            data-running={launch.isLaunching || undefined}
            onClick={launch.onLaunch}
          >
            <span className="sal-slice__label">{launch.isLaunching ? t("profiles.stop") : t("profiles.play")}</span>
          </button>
          {launch.isLaunching && (
            <p className="sal-slice__status" title={launch.statusMessage ?? undefined}>
              <Icon icon="svg-spinners:ring-resize" className="h-3.5 w-3.5 flex-shrink-0" />
              <span>{launch.statusMessage || t("salami.look.salumeria.profile.status")}</span>
            </p>
          )}
          <div className="sal-service">
            <button type="button" onClick={onEdit}>
              {t("salami.look.salumeria.profile.settings")}
            </button>
            <button type="button" onClick={actions.onOpenFolder}>
              {t("salami.look.salumeria.profile.folder")}
            </button>
            <button type="button" onClick={actions.onDuplicate}>
              {t("salami.look.salumeria.profile.duplicate")}
            </button>
            <button type="button" onClick={actions.onExport}>
              {t("salami.look.salumeria.profile.export")}
            </button>
            <button type="button" onClick={actions.onSyncPacks}>
              {t("syncPacks.title")}
            </button>
            <span className="sal-service__more">{moreMenu}</span>
          </div>
        </div>
      </header>

      <div className="sal-carta-body">
        <nav className="sal-index" role="tablist" aria-orientation="vertical">
          <p className="sal-index__head">{t("salami.look.salumeria.profile.menu")}</p>
          {contentItems.map((item, index) => course(item, index))}
          {worldItems.length > 0 && (
            <p className="sal-index__head sal-index__head--cellar">{t("salami.look.salumeria.profile.cellar")}</p>
          )}
          {worldItems.map((item, index) => course(item, contentItems.length + index))}
        </nav>

        <section className={onPaper ? "sal-carta sal-carta--paper" : "sal-carta sal-carta--dark"}>
          {onPaper && active && (
            <h2 className="sal-carta__title">
              <span>{active.label}</span>
            </h2>
          )}
          <div className="sal-carta__content">{content}</div>
        </section>
      </div>
    </div>
  );
}
