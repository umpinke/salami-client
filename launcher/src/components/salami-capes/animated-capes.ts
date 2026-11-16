import info from "./animated-capes.json";

/**
 * Animierte Salami-Capes in der Vorschau. Im Spiel werden sie berechnet (salami-core,
 * `capes/art`); der Launcher zeigt vorab gerenderte Frames aus `public/capes`:
 * `<name>.frames.webp` (ganzer Ausschnitt, für die 3D-Vorschau) und `<name>.face.webp`
 * (nur die Außenseite, für die Karten). Beides und `animated-capes.json` erzeugt
 * `python branding/generate_capes.py`.
 */
export interface CapeAnimation {
  frames: number;
  frameMs: number;
  /** Alle Frames als Raster; jeder zeigt die linken oberen 46x22 Einheiten des Cape-Layouts. */
  sheet: FrameSheet;
  /** Alle Frames als Raster; jeder zeigt nur die Außenseite (10x16 Einheiten). */
  face: FrameSheet;
}

export interface FrameSheet {
  url: string;
  /** Pixel je Einheit des 64x32-Layouts. */
  scale: number;
  columns: number;
}

/** Ausschnitt des Layouts, den ein Frame von `sheet` zeigt (in Einheiten). */
export const SHEET_UNITS = { width: 46, height: 22 } as const;

const capes: Record<string, { frames: number; frameMs: number }> = info.capes;

/** Animation zu einem Bild der mitgelieferten Capes (`/capes/<name>.png`), sonst `null`. */
export function capeAnimation(src: string): CapeAnimation | null {
  const name = /^\/capes\/([a-z]+)\.png$/.exec(src)?.[1];
  const cape = name ? capes[name] : undefined;
  if (!name || !cape) return null;
  return {
    ...cape,
    sheet: { url: `/capes/${name}.frames.webp`, ...info.sheet },
    face: { url: `/capes/${name}.face.webp`, ...info.face },
  };
}

/** Frame, der gerade dran ist – an der Uhr ausgerichtet, damit alle Vorschauen im Takt laufen. */
export function currentFrame(animation: CapeAnimation): number {
  return Math.floor(Date.now() / animation.frameMs) % animation.frames;
}

/** Linke obere Ecke eines Frames im Raster, in Pixeln. */
export function frameOrigin(sheet: FrameSheet, frame: number, unitsWide: number, unitsHigh: number): [number, number] {
  return [(frame % sheet.columns) * unitsWide * sheet.scale, Math.floor(frame / sheet.columns) * unitsHigh * sheet.scale];
}
