import { useEffect, useRef } from "react";
import { capeAnimation, currentFrame, frameOrigin } from "./animated-capes";
import { CAPE, TEXTURE_WIDTH, type CapeRect } from "./cape-layout";

interface CapeFaceProps {
  src: string;
  /** Welche Seite: außen (im Spiel sichtbar) oder innen. */
  side?: "outside" | "inside";
  /** Breite in px; die Höhe folgt aus 10:16. */
  width?: number;
  className?: string;
}

/**
 * Zeigt eine Seite eines Capes – pixelscharf, funktioniert mit 64x32 und HD-Capes. Die
 * animierten Salami-Capes laufen hier mit (Außenseite).
 */
export function CapeFace({ src, side = "outside", width = 80, className }: CapeFaceProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const height = Math.round((width * 16) / 10);
  const animation = side === "outside" ? capeAnimation(src) : null;
  // Animierte Capes sind fein gemalt: in Bildschirmauflösung zeichnen und weich verkleinern
  const density = animation ? Math.min(2, window.devicePixelRatio || 1) : 1;

  useEffect(() => {
    const image = new Image();
    let cancelled = false;
    let timer: number | undefined;
    image.onload = () => {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext("2d");
      if (cancelled || !canvas || !ctx) return;
      if (!animation) {
        const scale = image.naturalWidth / TEXTURE_WIDTH;
        const [x, y, w, h]: CapeRect = CAPE[side];
        ctx.imageSmoothingEnabled = false;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(image, x * scale, y * scale, w * scale, h * scale, 0, 0, canvas.width, canvas.height);
        return;
      }
      const { face } = animation;
      let shown = -1;
      const draw = () => {
        const frame = currentFrame(animation);
        if (frame === shown) return;
        shown = frame;
        const [x, y] = frameOrigin(face, frame, 10, 16);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(image, x, y, 10 * face.scale, 16 * face.scale, 0, 0, canvas.width, canvas.height);
      };
      draw();
      timer = window.setInterval(draw, animation.frameMs / 2);
    };
    image.src = animation ? animation.face.url : src;
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearInterval(timer);
    };
    // animation hängt nur von src und side ab
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, side]);

  return (
    <canvas
      ref={canvasRef}
      width={Math.round(width * density)}
      height={Math.round(height * density)}
      className={className}
      style={{ width, height, imageRendering: animation ? "auto" : "pixelated" }}
    />
  );
}
