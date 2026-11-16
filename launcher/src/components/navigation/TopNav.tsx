import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { hexAlpha, readableOn, SURFACE } from "../ui/design-system";

export interface TopNavItem {
  id: string;
  icon: string;
  label: string;
  /** Aktion statt Seite (Einstellungen) – steht nicht in der Reiterleiste. */
  isAction?: boolean;
}

interface TopNavProps {
  items: TopNavItem[];
  activeItem: string;
  onItemClick: (id: string) => void;
}

/**
 * Reiterleiste in der Mitte der oberen Leiste. Der aktive Reiter ist eine gefüllte
 * Kapsel in der Akzentfarbe; ohne „Beschriftungen anzeigen“ bleiben nur die Symbole.
 */
export function TopNav({ items, activeItem, onItemClick }: TopNavProps) {
  const accentColor = useThemeStore((state) => state.accentColor);
  const showLabels = useThemeStore((state) => state.showNavLabels);
  const activeText = readableOn(accentColor.value);

  return (
    <nav
      aria-label="Navigation"
      className="flex items-center gap-1 border p-1"
      style={{
        backgroundColor: SURFACE.base,
        borderColor: SURFACE.line,
        borderRadius: "calc(var(--border-radius) + 4px)",
      }}
    >
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
              className={cn(
                "flex h-9 items-center gap-2 whitespace-nowrap text-[15px] leading-none transition-colors duration-150",
                "focus:outline-none focus-visible:ring-2 focus-visible:ring-white/40",
                showLabels ? "px-3.5" : "w-10 justify-center",
                !active && "text-white/60 hover:bg-white/[0.07] hover:text-white",
              )}
              style={
                active
                  ? {
                      backgroundColor: accentColor.value,
                      color: activeText,
                      boxShadow: `0 6px 18px -8px ${hexAlpha(accentColor.value, 0.9)}`,
                    }
                  : undefined
              }
            >
              {(active || !showLabels) && <Icon icon={item.icon} className="h-[18px] w-[18px] flex-shrink-0" />}
              {showLabels && <span className="font-minecraft">{item.label}</span>}
            </button>
          );
        })}
    </nav>
  );
}
