"use client";

import type React from "react";
import { forwardRef, useRef, useState } from "react";
import { cn } from "../../../lib/utils";
import { gsap } from "gsap";
import { useThemeStore } from "../../../store/useThemeStore";
import { ThemedSurface } from "../ThemedSurface";
import { 
  getVariantColors,
  getButtonSkin,
  getSizeClasses,
  getBorderRadiusClass,
  getAccessibilityProps,
  type ComponentSize,
  type ComponentVariant 
} from "../design-system";

interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ComponentVariant;
  displayVariant?: "button" | "ghost" | "themed-surface";
  size?: ComponentSize;
  icon: React.ReactNode;
  shadowDepth?: "default" | "short" | "none";
  label?: string;
  description?: string;
}

interface RippleType {
  x: number;
  y: number;
  size: number;
  id: number;
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      className,
      variant = "default",
      displayVariant = "button",
      size = "md",
      disabled = false,
      icon,
      shadowDepth = "short",
      onClick,
      label,
      description,
      ...props
    },
    ref,
  ) => {
    const buttonRef = useRef<HTMLButtonElement>(null);
    const [ripples, setRipples] = useState<RippleType[]>([]);
    const rippleCounter = useRef(0);
    const accentColor = useThemeStore((state) => state.accentColor);
    const isBackgroundAnimationEnabled = useThemeStore(
      (state) => state.isBackgroundAnimationEnabled,
    );
    const [isPressed, setIsPressed] = useState(false);
    const [isHovered, setIsHovered] = useState(false);

    const mergedRef = (node: HTMLButtonElement) => {
      if (ref) {
        if (typeof ref === "function") {
          ref(node);
        } else {
          ref.current = node;
        }
      }
      buttonRef.current = node;
    };

    const handleRipple = (e: React.MouseEvent<HTMLButtonElement>) => {
      if (disabled) return;

      if (onClick) onClick(e);
    };

    const handleMouseDown = () => {
      if (disabled) return;
      setIsPressed(true);
    };

    const handleMouseUp = () => {
      if (disabled) return;
      setIsPressed(false);
    };

    const handleMouseEnter = () => {
      if (disabled) return;
      setIsHovered(true);

      if (
        buttonRef.current &&
        isBackgroundAnimationEnabled &&
        shouldShowShadow()
      ) {
        gsap.to(buttonRef.current, {
          boxShadow: `0 6px 22px rgba(0,0,0,0.25), 0 8px 12px rgba(0,0,0,0.4)`,
          duration: 0.2,
          ease: "power2.out",
        });
      }
    };

    const handleMouseLeave = () => {
      if (disabled) return;
      setIsHovered(false);

      if (
        buttonRef.current &&
        isBackgroundAnimationEnabled &&
        shouldShowShadow()
      ) {
        gsap.to(buttonRef.current, {
          boxShadow: `0 4px 16px rgba(0,0,0,0.3), 0 6px 10px rgba(0,0,0,0.35)`,
          duration: 0.2,
          ease: "power2.out",
        });
      }

      if (isPressed) {
        handleMouseUp();
      }
    };

    const shouldShowShadow = () => {
      return (
        displayVariant === "button" &&
        variant === "3d" &&
        shadowDepth !== "none"
      );
    };    const colors = getVariantColors(variant, accentColor);
    const bare = displayVariant === "ghost" || displayVariant === "themed-surface";
    const skin = getButtonSkin(bare ? "ghost" : variant, accentColor, isHovered && !disabled && !bare);
    const radiusClass = getBorderRadiusClass();
    const accessibilityProps = getAccessibilityProps({
      label,
      description,
      disabled
    });    const getIconButtonSizeClasses = () => {
      switch (size) {
        case "xs": return "h-[36px] w-[36px] p-2 text-xs";
        case "sm": return "h-[42px] w-[42px] p-2.5 text-xs";
        case "md": return "h-[50px] w-[50px] p-3 text-sm";
        case "lg": return "h-[58px] w-[58px] p-3.5 text-base";
        case "xl": return "h-[66px] w-[66px] p-4 text-base";
        default: return "h-[50px] w-[50px] p-3 text-sm";
      }
    };

    const sizeClasses = getIconButtonSizeClasses();

    const getIconSize = () => {
      switch (size) {
        case "xs": return "w-4 h-4";
        case "sm": return "w-5 h-5";
        case "md": return "w-6 h-6";
        case "lg": return "w-7 h-7";
        case "xl": return "w-8 h-8";
        default: return "w-6 h-6";
      }
    };

    const getBorderClasses = () => {
      if (displayVariant === "ghost" || displayVariant === "themed-surface") {
        return "";
      }

      if (variant === "ghost") {
        return "";
      }

      if (variant === "3d") {
        return "border";
      }

      return "border";
    };

    const buttonElement = (
      <button
        ref={mergedRef}
        disabled={disabled}
        onClick={handleRipple}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        className={cn(
          "font-smallcaps relative overflow-hidden transition-colors duration-150",
          "rounded-md text-white tracking-wider",
          "flex items-center justify-center",
          getBorderClasses(),
          "focus:outline-none focus:ring-2 focus:ring-white/30 focus:ring-offset-1 focus:ring-offset-black/20",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          displayVariant !== "themed-surface" && sizeClasses,
          className,
        )}
        style={{
          backgroundColor: skin.background,
          borderColor: skin.border,
          boxShadow: bare || shadowDepth === "none" ? "none" : skin.shadow,
          color: bare ? colors.text : skin.color,
          
        }}
        {...props}
      >
        <span
          className={cn(
            "relative z-10 flex items-center justify-center transition-transform duration-200",
            getIconSize(),
          )}
          style={{
            
          }}
          aria-hidden="true"
        >
          {icon}
        </span>
      </button>
    );

    if (displayVariant === "themed-surface") {
      const surfaceBaseColorHex =
        variant === "default" || variant === "ghost" ? undefined : colors.main;      return (
        <ThemedSurface
          baseColorHex={surfaceBaseColorHex}
          className={cn(sizeClasses, "!p-0", className)}
        >
          {buttonElement}
        </ThemedSurface>
      );
    }

    return buttonElement;
  },
);

IconButton.displayName = "IconButton";
