"use client";

import React, { useState, useRef } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import type { UnifiedGalleryImage } from "../../types/unified";
import { ModDetailLightbox } from "./ModDetailLightbox";
import { hexAlpha, KANTE_SOFT, SURFACE } from "../ui/design-system";
import { useThemeStore } from "../../store/useThemeStore";
import { DeliLabel } from "../../themes/salami/SalamiStage";
import { KanteButton, SectionHeading } from "../modrinth/v2/FeinkostParts";

interface ModDetailGalleryProps {
  images: UnifiedGalleryImage[];
}

export function ModDetailGallery({ images }: ModDetailGalleryProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((s) => s.accentColor);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const openLightbox = (index: number) => {
    setLightboxIndex(index);
    setLightboxOpen(true);
  };

  const scrollLeft = () => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollBy({ left: -300, behavior: "smooth" });
    }
  };

  const scrollRight = () => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollBy({ left: 300, behavior: "smooth" });
    }
  };

  // Sort images by ordering, with featured first
  const sortedImages = [...images].sort((a, b) => {
    if (a.featured && !b.featured) return -1;
    if (!a.featured && b.featured) return 1;
    return a.ordering - b.ordering;
  });

  return (
    <>
      <div className="relative">
        {/* Section Title */}
        <SectionHeading
          className="mb-3"
          right={
            <span className="flex-shrink-0 text-xs text-white/50 font-minecraft">
              {t("mod_detail.gallery.count", { count: images.length })}
            </span>
          }
        >
          {t("mod_detail.gallery.title")}
        </SectionHeading>

        {/* Gallery Container with Navigation */}
        <div className="relative">
          {/* Left Navigation Button */}
          {images.length > 3 && (
            <div className="absolute left-2 top-1/2 z-10 -translate-y-1/2">
              <KanteButton
                iconOnly
                onClick={scrollLeft}
                style={{ backgroundColor: SURFACE.solidRaised }}
                icon={<Icon icon="solar:alt-arrow-left-bold" className="w-5 h-5" />}
              />
            </div>
          )}

          {/* Right Navigation Button */}
          {images.length > 3 && (
            <div className="absolute right-2 top-1/2 z-10 -translate-y-1/2">
              <KanteButton
                iconOnly
                onClick={scrollRight}
                style={{ backgroundColor: SURFACE.solidRaised }}
                icon={<Icon icon="solar:alt-arrow-right-bold" className="w-5 h-5" />}
              />
            </div>
          )}

          {/* Image Container */}
          <div
            ref={scrollContainerRef}
            className="flex gap-3 overflow-x-auto [&::-webkit-scrollbar]:hidden"
            style={{
              scrollSnapType: "x mandatory",
              scrollbarWidth: "none",
              msOverflowStyle: "none",
            }}
          >
          {sortedImages.map((image, index) => (
            <button
              key={image.url}
              onClick={() => openLightbox(index)}
              className="group relative my-1 flex-shrink-0 overflow-hidden border transition-[transform,border-color,box-shadow] duration-200 hover:-translate-y-0.5 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
              style={{
                scrollSnapAlign: "start",
                borderRadius: "var(--border-radius)",
                borderColor: SURFACE.line,
                boxShadow: KANTE_SOFT,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = hexAlpha(accentColor.value, 0.55);
                e.currentTarget.style.boxShadow = `0 10px 24px -12px ${hexAlpha(accentColor.value, 0.6)}`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = SURFACE.line;
                e.currentTarget.style.boxShadow = KANTE_SOFT;
              }}
            >
              <img
                src={image.thumbnail_url || image.url}
                alt={image.title || t("mod_detail.gallery.image", { number: index + 1 })}
                className="h-40 w-auto min-w-[200px] max-w-[320px] object-cover"
                loading="lazy"
              />

              {/* Hover Overlay */}
              <div className="absolute inset-0 flex items-center justify-center bg-transparent transition-colors duration-200 group-hover:bg-[rgba(21,16,14,0.45)]">
                <Icon
                  icon="solar:magnifer-zoom-in-bold"
                  className="w-8 h-8 text-[#f3e3d3] opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                />
              </div>

              {/* Featured Badge */}
              {image.featured && (
                <div className="absolute top-2 left-2">
                  <DeliLabel size="sm" tone="red">{t("mod_detail.gallery.featured")}</DeliLabel>
                </div>
              )}

              {/* Title (if available) */}
              {image.title && (
                <div
                  className="absolute bottom-0 left-0 right-0 p-2"
                  style={{ background: "linear-gradient(to top, rgba(21,16,14,0.85), rgba(21,16,14,0))" }}
                >
                  <p className="text-xs text-white font-minecraft truncate">
                    {image.title}
                  </p>
                </div>
              )}
            </button>
          ))}
          </div>
        </div>
      </div>

      {/* Lightbox */}
      <ModDetailLightbox
        images={sortedImages}
        initialIndex={lightboxIndex}
        isOpen={lightboxOpen}
        onClose={() => setLightboxOpen(false)}
      />
    </>
  );
}
