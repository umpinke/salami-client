import { useEffect, useState, type CSSProperties } from "react";
import { renderFlatSkin, type SkinVariant } from "./flat-render";

export interface FlatSkinProps {
  textureUrl: string | null | undefined;
  variant?: SkinVariant | "auto";
  /** Pixel pro Skin-Pixel (Bild ist 16×32 Skin-Pixel groß). */
  scale?: number;
  alt?: string;
  className?: string;
  style?: CSSProperties;
}

/** Vorderansicht eines Skins als Bild – braucht kein WebGL. */
export function FlatSkin({ textureUrl, variant = "auto", scale = 8, alt = "", className, style }: FlatSkinProps) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let current = true;
    if (!textureUrl) {
      setSrc(null);
      return;
    }
    renderFlatSkin(textureUrl, { variant, scale })
      .then((url) => current && setSrc(url))
      .catch(() => current && setSrc(null));
    return () => {
      current = false;
    };
  }, [textureUrl, variant, scale]);

  if (!src) return null;
  return (
    <img
      src={src}
      alt={alt}
      draggable={false}
      className={className}
      style={{ imageRendering: "pixelated", objectFit: "contain", ...style }}
    />
  );
}
