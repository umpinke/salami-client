import { useLayoutEffect, useRef, useState } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "../../../components/ui/Tooltip";
import { cn } from "../../../lib/utils";
import type { LookProfileDetailProps, LookProfileNavItem } from "../types";
import { CRAFT_ARROW, PixelArt, WerkbankIcon } from "./icons";

const plainName = (name: string) => name.replace(/§./g, "").trim();

/** Die Pixel-Schrift ist breit: lange Profilnamen bekommen eine kleinere Stufe. */
function nameStep(name: string): string {
  const length = plainName(name).length;
  if (length <= 16) return "wb-pd__name--lg";
  if (length <= 26) return "wb-pd__name--md";
  return "wb-pd__name--sm";
}

/**
 * Profilseite der Pixel-Werkbank als Inventar-Fenster: oben am Rand die Reiter wie im
 * Kreativ-Inventar, darin eine Tafel mit dem Profil im großen Slot, Name, Schildchen und der
 * Spielzeit als Erfahrungsleiste; ein Werkbank-Pfeil führt zum Ergebnis-Slot „Spielen“. Darunter
 * liegt das Inventar mit dem Inhalt des Reiters.
 */
export function WerkbankProfileDetail({
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
  // Bilder und Konsole liegen in einer dunklen Vertiefung, Listen im Inventar
  const well = nav.active === "screenshots" || nav.active === "logs";
  const contentTabs = nav.items.filter((item) => item.group === "content");
  const worldTabs = nav.items.filter((item) => item.group === "world");
  const modCount = nav.items.find((item) => item.key === "mods")?.count;

  // Spielzeit als Erfahrungsleiste: volle Stunden sind die Stufe, die Leiste füllt sich zur nächsten
  const seconds = Math.max(0, profile.playtime_seconds ?? 0);
  const level = Math.floor(seconds / 3600);
  const progress = stats.hasPlaytime ? (seconds % 3600) / 3600 : 0;

  // Alle Namen, solange die Zeile reicht. Wird es eng, verliert zuerst „Zurück“ seinen Namen, dann
  // tragen die hinteren Reiter nur ihr Symbol (der gewählte behält seinen Namen). Eingeklappte Namen
  // behalten ihre volle scrollWidth, daher lässt sich in jedem Zustand messen, was passen würde.
  const barRef = useRef<HTMLDivElement>(null);
  const backRef = useRef<HTMLButtonElement>(null);
  const tabRowRef = useRef<HTMLDivElement>(null);
  const [tabFit, setTabFit] = useState<"full" | "noback" | "compact">("full");
  const tabsKey = `${nav.active}|${backLabel}|${nav.items.map((item) => `${item.label}:${item.count ?? ""}`).join("|")}`;
  useLayoutEffect(() => {
    const bar = barRef.current;
    const back = backRef.current;
    const row = tabRowRef.current;
    if (!bar || !back || !row) return;
    const gapOf = (el: Element) => parseFloat(getComputedStyle(el).columnGap) || 0;
    // eingeklappt hebt ein negativer Rand den Abstand zum Symbol auf – der käme wieder dazu
    const hiddenPart = (label: HTMLElement | null, owner: Element) =>
      label && label.scrollWidth > label.clientWidth ? label.scrollWidth - label.clientWidth + gapOf(owner) : 0;
    const check = () => {
      const children = [...row.children] as HTMLElement[];
      let tabs = gapOf(row) * Math.max(0, children.length - 1);
      for (const child of children) {
        if (!child.classList.contains("wb-pd-tab")) {
          tabs += parseFloat(getComputedStyle(child).minWidth) || 0;
          continue;
        }
        tabs += child.offsetWidth + hiddenPart(child.querySelector<HTMLElement>(".wb-pd-tab__label"), child);
      }
      const backLabelEl = back.querySelector<HTMLElement>(".wb-pd__back-label");
      const backFull = back.offsetWidth + hiddenPart(backLabelEl, back);
      const backIcon = backFull - (backLabelEl ? backLabelEl.scrollWidth + gapOf(back) : 0);
      const room = bar.clientWidth - gapOf(bar) + 1;
      setTabFit(tabs + backFull <= room ? "full" : tabs + backIcon <= room ? "noback" : "compact");
    };
    check();
    const observer = new ResizeObserver(check);
    observer.observe(bar);
    for (const child of row.children) observer.observe(child);
    let alive = true;
    document.fonts?.ready.then(() => alive && check());
    return () => {
      alive = false;
      observer.disconnect();
    };
  }, [tabsKey]);

  const renderTab = (item: LookProfileNavItem) => {
    const active = item.key === nav.active;
    const count = typeof item.count === "number" && item.count > 0 ? item.count : null;
    return (
      <button
        key={item.key}
        type="button"
        role="tab"
        aria-selected={active}
        aria-label={count ? `${item.label} (${count})` : item.label}
        title={tabFit === "compact" && !active ? item.label : undefined}
        className={cn("wb-pd-tab", active && "wb-pd-tab--on")}
        onClick={() => nav.onChange(item.key)}
      >
        <span className="wb-pd-tab__item" aria-hidden="true">
          <WerkbankIcon name={item.key} className="wb-pd-tab__icon" />
          {count !== null && <span className="wb-pd-tab__count">{count > 999 ? "999" : count}</span>}
        </span>
        <span className="wb-pd-tab__label">{item.label}</span>
      </button>
    );
  };

  const tools: { key: string; icon: string; label: string; onClick: () => void }[] = [
    { key: "edit", icon: "settings", label: t("profiles.editProfile"), onClick: onEdit },
    { key: "folder", icon: "folder", label: t("profiles.openFolder"), onClick: actions.onOpenFolder },
    { key: "duplicate", icon: "duplicate", label: t("profiles.duplicate"), onClick: actions.onDuplicate },
    { key: "export", icon: "export", label: t("profiles.export"), onClick: actions.onExport },
    { key: "sync", icon: "sync", label: t("syncPacks.title"), onClick: actions.onSyncPacks },
  ];

  return (
    <div className="wb-pd">
      <div ref={barRef} className={cn("wb-pd__tabs", tabFit !== "full" && "wb-pd__tabs--noback")}>
        <button
          ref={backRef}
          type="button"
          className="wb-pd__back"
          onClick={onBack}
          title={tabFit !== "full" ? backLabel : undefined}
          aria-label={backLabel}
        >
          <WerkbankIcon name="back" className="wb-pd__back-icon" />
          <span className="wb-pd__back-label">{backLabel}</span>
        </button>
        <div
          ref={tabRowRef}
          className={cn("wb-pd__tabrow", tabFit === "compact" && "wb-pd__tabrow--compact")}
          role="tablist"
          aria-label={t("salami.werkbank.profile.tabs")}
        >
          {contentTabs.map(renderTab)}
          <span className="wb-pd__tabgap" aria-hidden="true" />
          {worldTabs.map(renderTab)}
        </div>
      </div>

      <section className="wb-pd__panel">
        <header className="wb-pd__head">
          <div className="wb-pd__icon">{icon}</div>

          <div className="wb-pd__info">
            <h1
              className={cn("wb-pd__name", nameStep(profile.name || profile.id))}
              title={plainName(profile.name || profile.id)}
              dangerouslySetInnerHTML={{ __html: nameHtml }}
            />
            <div className="wb-pd__chips">{chips}</div>
            <div
              className="wb-xp"
              data-empty={!stats.hasPlaytime || undefined}
              title={t("salami.werkbank.profile.level_hint", { count: level })}
            >
              <span className="wb-xp__bar">
                <span className="wb-xp__fill" style={{ width: `${Math.round(progress * 100)}%` }} />
                <span className="wb-xp__lvl">{level}</span>
              </span>
              <span className="wb-xp__text">
                {stats.hasPlaytime ? (
                  <>
                    <span className="wb-xp__label">{t("salami.werkbank.profile.playtime")}</span> {stats.playtime}
                  </>
                ) : (
                  <span className="wb-xp__label">{t("salami.werkbank.profile.never")}</span>
                )}
              </span>
            </div>
          </div>

          {/* Gegenstands-Tooltip mit den Kennzahlen */}
          <dl className="wb-pd__lore">
            <dt className="wb-pd__lore-title">{t("salami.werkbank.profile.info")}</dt>
            <dd>
              <WerkbankIcon name="clock" className="wb-pd__lore-icon" />
              <span className="wb-pd__lore-key">{t("salami.werkbank.profile.last_played")}</span>
              <span className="wb-pd__lore-val">{stats.lastPlayed}</span>
            </dd>
            <dd>
              <WerkbankIcon name="folder" className="wb-pd__lore-icon" />
              <span className="wb-pd__lore-key">{t("salami.werkbank.profile.size")}</span>
              <span className="wb-pd__lore-val">{stats.disk ?? "…"}</span>
            </dd>
            <dd className="wb-pd__lore-mod">
              <WerkbankIcon name="mods" className="wb-pd__lore-icon" />
              {typeof modCount === "number" && modCount > 0
                ? t("salami.werkbank.mods", { count: modCount })
                : `Minecraft ${profile.game_version}`}
            </dd>
          </dl>

          <PixelArt rows={CRAFT_ARROW} className="wb-pd__arrow" />

          <div className="wb-pd__result">
            <button
              type="button"
              className="wb-pd__launch"
              data-running={launch.isLaunching || undefined}
              onClick={launch.onLaunch}
            >
              <span className="wb-pd__launch-icon" aria-hidden="true">
                {launch.isLaunching ? <span className="wb-pd__stop" /> : <WerkbankIcon name="play" className="h-full w-full" />}
              </span>
              <span className="wb-pd__launch-text">
                <span className="wb-pd__launch-label">{launch.isLaunching ? t("profiles.stop") : t("profiles.play")}</span>
                {launch.isLaunching && (
                  <span className="wb-pd__launch-status" title={launch.statusMessage ?? undefined}>
                    <Icon icon="svg-spinners:ring-resize" className="h-3 w-3 flex-shrink-0" />
                    <span>{launch.statusMessage || t("salami.werkbank.profile.status")}</span>
                  </span>
                )}
              </span>
            </button>

            <div className="wb-pd__tools">
              {tools.map((tool) => (
                <Tooltip key={tool.key} content={tool.label} position="bottom" delay={0} wrapperClassName="wb-pd__tool-wrap">
                  <button type="button" className="wb-pd__tool" aria-label={tool.label} onClick={tool.onClick}>
                    <WerkbankIcon name={tool.icon} className="wb-pd__tool-icon" />
                  </button>
                </Tooltip>
              ))}
              <div className="wb-pd__tool wb-pd__more">{moreMenu}</div>
            </div>
          </div>
        </header>

        <div className={cn("wb-pd__inv", well && "wb-pd__inv--well")}>{content}</div>
      </section>
    </div>
  );
}
