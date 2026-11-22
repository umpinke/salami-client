"use client";

import type React from "react";
import { forwardRef, useRef, useState } from "react";
import { cn } from "../../../lib/utils";
import { gsap } from "gsap";
import { useThemeStore } from "../../../store/useThemeStore";
import { 
  getButtonSkin,
  getSizeClasses,
  getBorderRadiusClass,
  getAccessibilityProps,
  createRadiusStyle,
  type ComponentSize,
  type ComponentVariant
} from "../design-system";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ComponentVariant;
  size?: ComponentSize;
  icon?: React.ReactNode;
  iconPosition?: "left" | "right";
  shadowDepth?: "default" | "short" | "none";
  widthClassName?: string;
  heightClassName?: string;
  label?: string;
  description?: string;
}

interface RippleType {
  x: number;
  y: number;
  size: number;
  id: number;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      className,
      variant = "default",
      size = "md",
      disabled = false,
      icon,
      iconPosition = "left",
      shadowDepth = "short",
      onClick,
      widthClassName,
      heightClassName,
      label,
      description,
      ...props
    },
    ref,
  ) => {
    const buttonRef = useRef<HTMLButtonElement>(null);
    const accentColor = useThemeStore((state) => state.accentColor);
    const borderRadius = useThemeStore((state) => state.borderRadius);
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

    // Salami: kein weicher gsap-Schein beim Überfahren – die harte Kante (`kante`) bleibt stehen
    const shouldShowShadow = () => false;    const skin = getButtonSkin(variant, accentColor, isHovered && !disabled);
    
    const getButtonSizeClasses = () => {
      switch (size) {
        case "xs": return "h-[36px] px-5 py-2 text-xs";
        case "sm": return "h-[42px] px-6 py-2 text-sm";
        case "md": return "h-[50px] px-8 py-2.5 text-sm";
        case "lg": return "h-[58px] px-10 py-3 text-base";
        case "xl": return "h-[66px] px-12 py-4 text-lg";
        default: return "h-[50px] px-8 py-2.5 text-sm";
      }
    };
    
    const sizeClasses = getButtonSizeClasses();
    const radiusClass = getBorderRadiusClass(borderRadius);
    const accessibilityProps = getAccessibilityProps({
      label,
      description,
      disabled
    });    const getBorderClasses = () => {
      if (variant === "ghost") {
        return "";
      }

      if (variant === "3d") {
        return "border";
      }

      return "border";
    };

    return (
      <button
        ref={mergedRef}
        type="button"
        disabled={disabled}        className={cn(
          // Ansatzpunkt für die Looks (themes/looks): look-btn--primary | accent | secondary | ghost | destructive …
          "look-btn",
          `look-btn--${variant === "3d" || variant === "elevated" ? "primary" : variant === "default" ? "accent" : variant === "flat-secondary" ? "secondary" : variant}`,
          "relative overflow-hidden font-smallcaps transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2",
          "flex items-center justify-center gap-2 whitespace-nowrap",
          radiusClass,
          sizeClasses,
          getBorderClasses(),
          disabled && "opacity-50 cursor-not-allowed",
          widthClassName,
          heightClassName,
          className,
        )}
        style={{
          backgroundColor: skin.background,
          borderColor: skin.border,
          boxShadow: shadowDepth === "none" ? "none" : skin.shadow,
          color: skin.color,
          transform: isPressed ? "translateY(1px)" : "translateY(0)",
          
          ...createRadiusStyle(borderRadius),
        }}
        onClick={handleRipple}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        {...accessibilityProps}
        {...props}      >        {icon && iconPosition === "left" && (
          <span className="flex items-center transition-transform duration-200" style={{
            
          }}>{icon}</span>
        )}
        <span className="transition-transform duration-200" style={{
          
        }}>
          {children}
        </span>
        {icon && iconPosition === "right" && (
          <span className="flex items-center transition-transform duration-200" style={{
            
          }}>{icon}</span>
        )}
      </button>
    );
  },
);

Button.displayName = "Button";
