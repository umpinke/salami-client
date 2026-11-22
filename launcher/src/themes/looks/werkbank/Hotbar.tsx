import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { selectFriendsBadge, useFriendsStore } from "../../../features/friends/friends-store";
import { useOnlineCount } from "../../../hooks/useOnlineCount";
import { cn } from "../../../lib/utils";
import { useThemeStore } from "../../../store/useThemeStore";
import type { LookNavProps } from "../types";
import { WerkbankIcon } from "./icons";

/** Solange so etwas offen ist, schalten die Zifferntasten nicht um. */
const OVERLAY_SELECTOR = '.look-modal, .look-menu, [role="dialog"], [aria-modal="true"], .fixed.inset-0';

function isTyping(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el || typeof el.closest !== "function") return false;
  return !!el.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"], [role="textbox"], [role="slider"]');
}

/**
 * Die Hauptnavigation als Hotbar: nummerierte Slots mit Pixel-Symbolen, der aktive mit weißem
 * Auswahlrahmen. Die Zifferntasten 1–9 wählen den Slot – wie im Spiel.
 */
export function Hotbar({ items, activeItem, onItemClick }: LookNavProps) {
  const { t } = useTranslation();
  const showLabels = useThemeStore((state) => state.showNavLabels);
  const friendsBadge = useFriendsStore(selectFriendsBadge);

  // immer die aktuellen Einträge, ohne den Tasten-Lauscher bei jedem Rendern neu anzumelden
  const latest = useRef({ items, onItemClick });
  latest.current = { items, onItemClick };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.repeat) return;
      if (event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
      if (!/^[1-9]$/.test(event.key)) return;
      if (isTyping(event.target) || isTyping(document.activeElement)) return;
      if (document.querySelector(OVERLAY_SELECTOR)) return;
      const item = latest.current.items[Number(event.key) - 1];
      if (!item) return;
      event.preventDefault();
      latest.current.onItemClick(item.id);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <>
      <nav aria-label={t("salami.werkbank.hotbar")} className={cn("wb-hotbar", !showLabels && "wb-hotbar--icons")}>
        {items.map((item, index) => {
          const active = item.id === activeItem;
          // die Zahl hinter „Freunde“ steht hier als Stapelzahl am Symbol, nicht im Namen
          const label =
            item.id === "friends" ? t("nav.friends") : item.isAction ? t("salami.werkbank.options") : item.label;
          const count = item.id === "friends" ? friendsBadge : 0;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onItemClick(item.id)}
              aria-current={active ? "page" : undefined}
              aria-label={item.label}
              aria-keyshortcuts={index < 9 ? String(index + 1) : undefined}
              title={showLabels ? undefined : item.label}
              className={cn("wb-hb", active && "wb-hb--on")}
            >
              {index < 9 && (
                <span className="wb-hb__num" aria-hidden="true">
                  {index + 1}
                </span>
              )}
              <span className="wb-hb__item">
                <WerkbankIcon name={item.id} className="wb-hb__icon" />
                {count > 0 && (
                  <span className="wb-hb__count" aria-hidden="true">
                    {count > 99 ? "99" : count}
                  </span>
                )}
              </span>
              {showLabels && <span className="wb-hb__label">{label}</span>}
            </button>
          );
        })}
      </nav>
      <TopBarStatus />
    </>
  );
}

/**
 * „● 42 Salami-Spieler online“ in der Mitte der oberen Leiste (dort ist bei dieser Navigation
 * Platz). Die Leiste gehört dem gemeinsamen Layout; der Text hängt sich in ihre leere Mittelspalte.
 */
function TopBarStatus() {
  const { t } = useTranslation();
  const online = useOnlineCount();
  const [slot, setSlot] = useState<Element | null>(null);

  useEffect(() => {
    setSlot(document.querySelector(".look-topbar > .h-full[data-tauri-drag-region]"));
  }, []);

  if (!slot || !slot.isConnected || online === null) return null;

  return createPortal(
    <span className="wb-online" title={t("salami.play.online_hint")} data-tauri-drag-region>
      <span className="wb-online__dot" aria-hidden="true" data-tauri-drag-region />
      <span className="wb-online__long" data-tauri-drag-region>
        {t("salami.werkbank.online", { count: online })}
      </span>
      <span className="wb-online__short" data-tauri-drag-region>
        {t("salami.werkbank.online_short", { count: online })}
      </span>
    </span>,
    slot,
  );
}
