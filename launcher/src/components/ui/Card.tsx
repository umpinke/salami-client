"use client";

import type React from "react";
import { forwardRef, type ReactNode, useRef, useState } from "react";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { 
  getVariantColors,
  hexAlpha,
  SURFACE,
  getBorderRadiusClass,
  createRadiusStyle,
  getAccessibilityProps,
  type ComponentVariant
} from "./design-system";

interface CardProps {
  children: ReactNode;
  className?: string;
  variant?: ComponentVariant;
  withAnimation?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  onContextMenu?: (e: React.MouseEvent) => void;
  role?: string;
  ariaLabel?: string;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  {
    children,
    className,
    variant = "default",
    withAnimation = true,
    onClick,
    onContextMenu,
    role,
    ariaLabel,
  },
  ref,
) {
  const cardRef = useRef<HTMLDivElement>(null);
  const accentColor = useThemeStore((state) => state.accentColor);
  const borderRadius = useThemeStore((state) => state.borderRadius);
  const isBackgroundAnimationEnabled = useThemeStore(
    (state) => state.isBackgroundAnimationEnabled,
  );
  const [isHovered, setIsHovered] = useState(false);

  const mergedRef = (node: HTMLDivElement) => {
    if (ref) {
      if (typeof ref === "function") {
        ref(node);
      } else {
        ref.current = node;
      }
    }
    cardRef.current = node;
  };

  const handleMouseEnter = () => {
    setIsHovered(true);
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
  };

  const colors = getVariantColors(variant, accentColor);
  const accessibilityProps = getAccessibilityProps({
    label: ariaLabel,
    disabled: false
  });

  const tinted = ["warning", "destructive", "info", "success"].includes(variant);
  const raised = variant === "3d" || variant === "elevated";

  const getBoxShadow = () => (raised ? "0 18px 40px -18px rgba(0,0,0,0.7)" : "none");
  const getBorderStyle = () => "border";
  const getBackgroundColor = () => {
    if (tinted) return hexAlpha(colors.main, 0.14);
    return isHovered && onClick ? SURFACE.hover : SURFACE.base;
  };
  const getBorderColor = () => (tinted ? hexAlpha(colors.main, 0.35) : SURFACE.line);

  return (
    <div
      ref={mergedRef}
      role={role || (onClick ? "button" : undefined)}
      tabIndex={onClick ? 0 : undefined}
      className={cn(
        "relative overflow-hidden transition-colors duration-150",
        getBorderRadiusClass(borderRadius),
        getBorderStyle(),
        onClick && "cursor-pointer focus:outline-none focus:ring-2 focus:ring-offset-2",
        className,
      )}
      style={{
        backgroundColor: getBackgroundColor(),
        borderColor: getBorderColor(),
        boxShadow: getBoxShadow(),
        
        ...createRadiusStyle(borderRadius, 1.2),
      }}
      onClick={onClick}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onContextMenu={onContextMenu}
      {...accessibilityProps}
    >
      {children}
    </div>
  );
});

Card.displayName = "Card";
