import { useEffect, useRef } from "react";
import { CAPE, TEXTURE_WIDTH, type CapeRect } from "./cape-layout";

interface CapeFaceProps {
  src: string;
  /** Welche Seite: außen (im Spiel sichtbar) oder innen. */
  side?: "outside" | "inside";
  /** Breite in px; die Höhe folgt aus 10:16. */
  width?: number;
  className?: string;
}

/** Zeigt eine Seite eines Capes pixelscharf – funktioniert mit 64x32 und HD-Capes. */
export function CapeFace({ src, side = "outside", width = 80, className }: CapeFaceProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const height = Math.round((width * 16) / 10);

  useEffect(() => {
    const image = new Image();
    let cancelled = false;
    image.onload = () => {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext("2d");
      if (cancelled || !canvas || !ctx) return;
      const scale = image.naturalWidth / TEXTURE_WIDTH;
      const [x, y, w, h]: CapeRect = CAPE[side];
      ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(image, x * scale, y * scale, w * scale, h * scale, 0, 0, canvas.width, canvas.height);
    };
    image.src = src;
    return () => {
      cancelled = true;
    };
  }, [src, side]);

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      className={className}
      style={{ width, height, imageRendering: "pixelated" }}
    />
  );
}
