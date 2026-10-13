"use client";

import { useEffect, useRef, useState } from "react";
import { gsap } from "gsap";
import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { Logo } from "../ui/Logo";
import { NavTooltip } from "../ui/nav/NavTooltip";
import { CreditsModal } from "../modals/CreditsModal";
import * as ConfigService from "../../services/launcher-config-service";
import { useThemeStore } from "../../store/useThemeStore";
import { createPortal } from "react-dom";
import { BRAND } from "../../config/salami";

interface NavItem {
  id: string;
  icon: string;
  label: string;
  action?: () => void;
  isAction?: boolean;
}

interface VerticalNavbarProps {
  className?: string;
  items: NavItem[];
  activeItem?: string;
  onItemClick?: (id: string) => void;
  version?: string;
}

export function VerticalNavbar({
  className,
  items,
  activeItem,
  onItemClick,
  version = "v0.5.22",
}: VerticalNavbarProps) {  const [active, setActive] = useState(activeItem || items[0]?.id);
  const navRef = useRef<HTMLElement>(null);
  const [showTooltip, setShowTooltip] = useState<string | null>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const [appVersion, setAppVersion] = useState<string | null>(null);
  const accentColor = useThemeStore((state) => state.accentColor);
  const showNavLabels = useThemeStore((state) => state.showNavLabels);
  const [tooltipPosition, setTooltipPosition] = useState({ top: 0, left: 0 });
  const [isMounted, setIsMounted] = useState(false);
  const [showCreditsModal, setShowCreditsModal] = useState(false);

  useEffect(() => {
    setIsMounted(true);
    return () => setIsMounted(false);
  }, []);

  useEffect(() => {
    if (activeItem) {
      setActive(activeItem);
    }
  }, [activeItem]);

  useEffect(() => {
    const fetchVersion = async () => {
      try {
        const fetchedVersion = await ConfigService.getAppVersion();
        setAppVersion(`v${fetchedVersion}`);
      } catch (error) {
        console.error("Failed to fetch app version:", error);
        setAppVersion("v?.?.?");
      }
    };
    fetchVersion();
  }, []);

  useEffect(() => {
    const ctx = gsap.context(() => {
      gsap.set(".nav-item", { opacity: 0, x: -20 });

      gsap.to(".nav-item", {
        opacity: 1,
        x: 0,
        stagger: 0.05,
        duration: 0.4,
        ease: "power2.out",
        onComplete: () => {
          gsap.set(".nav-item", { clearProps: "all" });
        },
      });
    }, navRef);

    return () => ctx.revert();
  }, []);

  const handleItemClick = (id: string, isAction?: boolean) => {
    if (!isAction) setActive(id);
    if (onItemClick) {
      onItemClick(id);
    }
  };

  const handleMouseEnter = (id: string) => {
    const buttonElement = buttonRefs.current[id];
    if (buttonElement) {
      const rect = buttonElement.getBoundingClientRect();
      setTooltipPosition({
        top: rect.top + rect.height / 2,
        left: rect.right + 12,
      });
    }

    setShowTooltip(id);
    if (tooltipRef.current) {
      gsap.fromTo(
        tooltipRef.current,
        { opacity: 0, x: -10 },
        { opacity: 1, x: 0, duration: 0.3, ease: "power2.out" },
      );
    }
  };

  const handleMouseLeave = () => {
    setShowTooltip(null);
  };

  const expanded = showNavLabels;

  const renderItem = (item: NavItem) => {
    const isActive = !item.isAction && active === item.id;
    return (
      <div
        key={item.id}
        className="nav-item w-full"
        ref={(el) => (buttonRefs.current[item.id] = el)}
      >
        <button
          type="button"
          onClick={() => handleItemClick(item.id, item.isAction)}
          onMouseEnter={() => !expanded && handleMouseEnter(item.id)}
          onMouseLeave={handleMouseLeave}
          aria-label={item.label}
          aria-current={isActive ? "page" : undefined}
          title={expanded ? item.label : undefined}
          className={cn(
            "relative flex w-full items-center rounded-xl transition-colors duration-150",
            "focus:outline-none focus-visible:ring-2 focus-visible:ring-white/40",
            expanded ? "h-11 gap-3 px-3 text-left" : "h-12 justify-center",
            isActive ? "text-white" : "text-white/70 hover:bg-white/5 hover:text-white",
          )}
          style={
            isActive
              ? {
                  backgroundColor: `${accentColor.value}66`,
                  boxShadow: `inset 3px 0 0 ${accentColor.hoverValue ?? accentColor.value}`,
                }
              : undefined
          }
        >
          <Icon icon={item.icon} className="h-6 w-6 shrink-0" />
          {expanded && (
            <span className="truncate font-minecraft text-[15px] leading-none">{item.label}</span>
          )}
        </button>
      </div>
    );
  };

  return (
    <>
      <nav
        ref={navRef}
        aria-label={BRAND.name}
        className={cn(
          "flex flex-col py-5 backdrop-blur-lg",
          expanded ? "w-56 px-3" : "w-20 items-center px-2",
          className,
        )}
        style={{
          backgroundColor: `rgba(${parseInt(accentColor.value.slice(1, 3), 16)}, ${parseInt(accentColor.value.slice(3, 5), 16)}, ${parseInt(accentColor.value.slice(5, 7), 16)}, 0.35)`,
          borderRight: `2px solid ${accentColor.value}50`,
        }}
      >
        <button
          type="button"
          onClick={() => setShowCreditsModal(true)}
          aria-label={BRAND.name}
          className={cn(
            "mb-7 flex items-center rounded-xl transition-colors hover:bg-white/5",
            expanded ? "w-full gap-3 px-2 py-1.5 text-left" : "justify-center p-1",
          )}
        >
          <Logo size="sm" className="shrink-0" />
          {expanded && (
            <span className="flex flex-col leading-none">
              <span className="font-minecraft text-xl text-white">{BRAND.shortName}</span>
              <span className="mt-1 text-[11px] uppercase tracking-[0.25em] text-white/55">Client</span>
            </span>
          )}
        </button>

        <div className="flex w-full flex-1 flex-col gap-1">
          {items.filter((item) => !item.isAction).map(renderItem)}
        </div>

        <div className="mt-4 flex w-full flex-col gap-1">
          {items.filter((item) => item.isAction).map(renderItem)}
          {expanded && (
            <span className="px-3 pt-2 text-[11px] text-white/40">{appVersion ?? version}</span>
          )}
        </div>
      </nav>
      {isMounted &&
        showTooltip &&
        document.body &&
        createPortal(
          <div
            className="fixed pointer-events-none"
            style={{
              top: `${tooltipPosition.top}px`,
              left: `${tooltipPosition.left}px`,
              zIndex: 9999,
              transform: "translateY(-50%)",
            }}
          >
            <NavTooltip ref={tooltipRef}>
              {items.find((item) => item.id === showTooltip)?.label}
            </NavTooltip>
          </div>,
          document.body,
        )}

      <CreditsModal
        isOpen={showCreditsModal}
        onClose={() => setShowCreditsModal(false)}
      />
    </>
  );
}
