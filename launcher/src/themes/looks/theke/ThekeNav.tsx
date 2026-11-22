import { Icon } from "@iconify/react";
import type { CSSProperties } from "react";
import { readableOn } from "../../../components/ui/design-system";
import { cn } from "../../../lib/utils";
import { useThemeStore } from "../../../store/useThemeStore";
import { PixelIcon, hasPixelIcon } from "../../salami/PixelIcon";
import type { LookNavProps } from "../types";

/**
 * Hauptnavigation der Theke: Kreidetafeln, die an einer Stange unter der Markise hängen. Die
 * aktive Tafel ist in der Akzentfarbe gestrichen und hängt ein Stück tiefer. Ohne „Beschriftungen
 * anzeigen“ bleiben nur die Symbole (der Name steht dann im Tooltip).
 */
export function ThekeNav({ items, activeItem, onItemClick }: LookNavProps) {
  const showLabels = useThemeStore((state) => state.showNavLabels);
  const accent = useThemeStore((state) => state.accentColor.value);

  return (
    <nav
      aria-label="Navigation"
      className="theke-nav"
      style={{ "--theke-on-accent": readableOn(accent) } as CSSProperties}
    >
      <span className="theke-rail" aria-hidden="true" />
      <div className="theke-signs">
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
                className={cn("theke-sign", active && "is-active")}
              >
                <span className="theke-sign-board">
                  {(!showLabels || active) &&
                    (hasPixelIcon(item.id) ? (
                      <PixelIcon name={item.id} className="theke-sign-icon" />
                    ) : (
                      <Icon icon={item.icon} className="theke-sign-icon" />
                    ))}
                  {showLabels && <span>{item.label}</span>}
                </span>
              </button>
            );
          })}
      </div>
    </nav>
  );
}
