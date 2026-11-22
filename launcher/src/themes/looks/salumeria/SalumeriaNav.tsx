import { Icon } from "@iconify/react";
import { useThemeStore } from "../../../store/useThemeStore";
import { PixelIcon, hasPixelIcon } from "../../salami/PixelIcon";
import type { LookNavProps } from "../types";

/**
 * Navigation der Salumeria: reine Schrift in Kapitälchen, ohne Kapseln. Der aktive Eintrag trägt
 * eine rote Linie mit Raute darunter (Regeln in `look.css`, Klassen `sal-nav…`).
 */
export function SalumeriaNav({ items, activeItem, onItemClick }: LookNavProps) {
  const showLabels = useThemeStore((state) => state.showNavLabels);

  return (
    <nav aria-label="Navigation" className="sal-nav">
      {items
        .filter((item) => !item.isAction)
        .map((item) => {
          const active = item.id === activeItem;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onItemClick(item.id)}
              aria-current={active ? "page" : undefined}
              aria-label={item.label}
              title={showLabels ? undefined : item.label}
              className={active ? "sal-nav__item sal-nav__item--on" : "sal-nav__item"}
            >
              {showLabels ? (
                item.label
              ) : hasPixelIcon(item.id) ? (
                <PixelIcon name={item.id} className="h-[16px] w-[16px]" />
              ) : (
                <Icon icon={item.icon} className="h-[18px] w-[18px]" />
              )}
            </button>
          );
        })}
    </nav>
  );
}
