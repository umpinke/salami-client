"use client";

interface HeaderInfoCarouselProps {
  version: string | null;
}

const TEXT_CLASSES = "text-white/70 font-minecraft text-[8px] font-normal leading-none";

/** Versionsanzeige unter dem Seitentitel. */
export function HeaderInfoCarousel({ version }: HeaderInfoCarouselProps) {
  return (
    <span className={`${TEXT_CLASSES} inline-flex items-center h-3 whitespace-nowrap`}>
      v{version || "?.?.?"}
    </span>
  );
}
