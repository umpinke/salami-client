"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import type { UnifiedGalleryImage } from "../../types/unified";

interface ModDetailLightboxProps {
  images: UnifiedGalleryImage[];
  initialIndex: number;
  isOpen: boolean;
  onClose: () => void;
}

export function ModDetailLightbox({
  images,
  initialIndex,
  isOpen,
  onClose,
}: ModDetailLightboxProps) {
  const { t } = useTranslation();
  const [currentIndex, setCurrentIndex] = useState(initialIndex);
  const [isZoomed, setIsZoomed] = useState(false);

  // Reset index when opening
  useEffect(() => {
    if (isOpen) {
      setCurrentIndex(initialIndex);
      setIsZoomed(false);
    }
  }, [isOpen, initialIndex]);

  // Keyboard navigation
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case "Escape":
          onClose();
          break;
        case "ArrowLeft":
          goToPrevious();
          break;
        case "ArrowRight":
          goToNext();
          break;
        case " ": // Space to toggle zoom
          e.preventDefault();
          setIsZoomed((prev) => !prev);
          break;
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const goToPrevious = useCallback(() => {
    setCurrentIndex((prev) => (prev === 0 ? images.length - 1 : prev - 1));
    setIsZoomed(false);
  }, [images.length]);

  const goToNext = useCallback(() => {
    setCurrentIndex((prev) => (prev === images.length - 1 ? 0 : prev + 1));
    setIsZoomed(false);
  }, [images.length]);

  if (!isOpen || images.length === 0) return null;

  const currentImage = images[currentIndex];

  return (
    <div
      className="fixed inset-0 z-50 bg-[rgba(21,16,14,0.94)] flex items-center justify-center"
      onClick={onClose}
    >
      {/* Close Button */}
      <button
        onClick={onClose}
        className="absolute top-4 right-4 z-10 p-2 bg-white/[0.08] hover:bg-white/[0.14] border border-white/10 rounded-[calc(var(--border-radius)*0.8)] text-white shadow-[0_2px_0_rgba(0,0,0,0.32)] transition-colors duration-150"
      >
        <Icon icon="solar:close-circle-bold" className="w-8 h-8" />
      </button>

      {/* Navigation - Previous */}
      {images.length > 1 && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            goToPrevious();
          }}
          className="absolute left-4 top-1/2 -translate-y-1/2 z-10 p-3 bg-white/[0.08] hover:bg-white/[0.14] border border-white/10 rounded-[calc(var(--border-radius)*0.8)] text-white shadow-[0_2px_0_rgba(0,0,0,0.32)] transition-colors duration-150"
        >
          <Icon icon="solar:arrow-left-bold" className="w-6 h-6" />
        </button>
      )}

      {/* Navigation - Next */}
      {images.length > 1 && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            goToNext();
          }}
          className="absolute right-4 top-1/2 -translate-y-1/2 z-10 p-3 bg-white/[0.08] hover:bg-white/[0.14] border border-white/10 rounded-[calc(var(--border-radius)*0.8)] text-white shadow-[0_2px_0_rgba(0,0,0,0.32)] transition-colors duration-150"
        >
          <Icon icon="solar:arrow-right-bold" className="w-6 h-6" />
        </button>
      )}

      {/* Image Container */}
      <div
        className="relative w-[80vw] h-[60vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <img
          src={currentImage.url}
          alt={currentImage.title || `Image ${currentIndex + 1}`}
          className={`
            w-full h-full object-contain
            transition-transform duration-300 cursor-pointer
            ${isZoomed ? "scale-150" : "scale-100"}
          `}
          onClick={() => setIsZoomed((prev) => !prev)}
        />
      </div>

      {/* Bottom Info Bar */}
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-[rgba(21,16,14,0.9)] to-transparent p-6">
        <div className="max-w-4xl mx-auto">
          {/* Counter */}
          <div className="flex items-center justify-between mb-2">
            <span className="text-white/70 font-minecraft text-sm">
              {t('lightbox.counter', { current: currentIndex + 1, total: images.length })}
            </span>
            <div className="flex items-center gap-2 text-white/50 text-xs font-minecraft">
              <span>{t('lightbox.press_space_zoom')}</span>
              <span>|</span>
              <span>{t('lightbox.arrow_keys_navigate')}</span>
              <span>|</span>
              <span>{t('lightbox.esc_close')}</span>
            </div>
          </div>

          {/* Title & Description */}
          {(currentImage.title || currentImage.description) && (
            <div className="mt-2">
              {currentImage.title && (
                <h3 className="text-lg font-minecraft text-white">
                  {currentImage.title}
                </h3>
              )}
              {currentImage.description && (
                <p className="text-sm text-white/70 font-minecraft mt-1">
                  {currentImage.description}
                </p>
              )}
            </div>
          )}

          {/* Thumbnail Navigation */}
          {images.length > 1 && (
            <div className="flex gap-2 mt-4 overflow-x-auto pb-2 justify-center">
              {images.map((image, index) => (
                <button
                  key={image.url}
                  onClick={(e) => {
                    e.stopPropagation();
                    setCurrentIndex(index);
                    setIsZoomed(false);
                  }}
                  className={`
                    flex-shrink-0 w-16 h-12 rounded overflow-hidden border-2 transition-all
                    ${index === currentIndex ? "border-[#f3e3d3]" : "border-transparent opacity-50 hover:opacity-100"}
                  `}
                >
                  <img
                    src={image.thumbnail_url || image.url}
                    alt={`Thumbnail ${index + 1}`}
                    className="w-full h-full object-cover"
                  />
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
