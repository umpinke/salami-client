/** Rechteck im 64x32-Raster einer Cape-Textur: [x, y, breite, höhe]. */
export type CapeRect = readonly [number, number, number, number];

export const TEXTURE_WIDTH = 64;
export const TEXTURE_HEIGHT = 32;

/** Cape-Box 10x16x1 (Minecraft-Layout). `outside` („Vorne“) sieht man im Spiel von hinten. */
export const CAPE = {
  outside: [1, 1, 10, 16],
  inside: [12, 1, 10, 16],
  top: [1, 0, 10, 1],
  bottom: [11, 0, 10, 1],
  left: [0, 1, 1, 16],
  right: [11, 1, 1, 16],
} as const satisfies Record<string, CapeRect>;

/** Elytra-Box 10x20x2 bei (22,0) – nutzt dieselbe Textur. `outside` („Vorne“) sieht man im Spiel. */
export const ELYTRA = {
  outside: [36, 2, 10, 20],
  inside: [24, 2, 10, 20],
  top: [24, 0, 10, 2],
  bottom: [34, 0, 10, 2],
  left: [22, 2, 2, 20],
  right: [34, 2, 2, 20],
} as const satisfies Record<string, CapeRect>;

/**
 * Pixel, die Minecraft von der Elytra zeichnet (Form der Original-Elytra):
 * Zeile -> Spaltenbereiche [von, bis]. Gleiche Daten wie branding/generate_capes.py.
 */
const ELYTRA_ROWS: Record<number, [number, number][]> = {
  0: [[31, 39]],
  1: [[32, 33]],
  2: [[34, 41]],
  3: [[34, 42]],
  4: [[35, 43]],
  5: [[35, 43]],
  6: [[35, 43]],
  7: [[35, 44]],
  8: [[35, 44]],
  9: [[35, 44]],
  10: [[35, 44]],
  11: [[22, 22], [36, 45]],
  12: [[22, 22], [36, 45]],
  13: [[22, 22], [36, 45]],
  14: [[22, 22], [36, 45]],
  15: [[22, 22], [36, 45]],
  16: [[22, 22], [37, 45]],
  17: [[22, 22], [37, 45]],
  18: [[22, 22], [37, 45]],
  19: [[22, 22], [38, 45]],
  20: [[22, 22], [38, 45]],
  21: [[22, 22], [39, 45]],
};

type ElytraPart = "top" | "bottom" | "left" | "right" | "front";

function elytraPart(x: number, y: number): ElytraPart | null {
  if (!(ELYTRA_ROWS[y] ?? []).some(([a, b]) => x >= a && x <= b)) return null;
  if (y === 0) return x <= 33 ? "top" : "bottom";
  if (y === 1) return "top";
  if (x === 22) return "left";
  return x <= 35 ? "right" : "front";
}

const inRect = ([rx, ry, w, h]: CapeRect) => (x: number, y: number) => x >= rx && x < rx + w && y >= ry && y < ry + h;

export interface EditorArea {
  id: string;
  /** Umgebendes Rechteck (Spiegeln, Beschriftung). */
  rect: CapeRect;
  /** Farbe wie in der Vorlage, halbtransparent (Hilfsebene, wird nicht gespeichert). */
  color: string;
  labelKey?: string;
  /** Spiegeln ist nur bei symmetrischen Flächen sinnvoll (Cape), nicht bei der Flügelform. */
  mirror: boolean;
  contains: (x: number, y: number) => boolean;
}

const cape = (id: string, rect: CapeRect, color: string, labelKey?: string): EditorArea => ({
  id,
  rect,
  color,
  labelKey,
  mirror: true,
  contains: inRect(rect),
});

const elytra = (part: ElytraPart, rect: CapeRect, color: string, labelKey?: string): EditorArea => ({
  id: `elytra_${part}`,
  rect,
  color,
  labelKey,
  mirror: false,
  contains: (x, y) => elytraPart(x, y) === part,
});

/** Flächen wie in der Vorlage (salami_cape_template.png): gleiche Farben, gleiche Namen. */
export const EDITOR_AREAS: EditorArea[] = [
  cape("cape_top", CAPE.top, "rgba(255, 216, 0, 0.35)"),
  cape("cape_bottom", CAPE.bottom, "rgba(0, 255, 255, 0.3)"),
  cape("cape_left", CAPE.left, "rgba(255, 216, 0, 0.35)"),
  cape("cape_front", CAPE.outside, "rgba(0, 148, 255, 0.3)", "salami.capes.editor.area.outside"),
  cape("cape_right", CAPE.right, "rgba(255, 216, 0, 0.35)"),
  cape("cape_back", CAPE.inside, "rgba(255, 0, 0, 0.3)", "salami.capes.editor.area.inside"),
  elytra("top", [31, 0, 3, 2], "rgba(255, 120, 0, 0.4)"),
  elytra("bottom", [34, 0, 6, 1], "rgba(0, 255, 255, 0.3)"),
  elytra("right", [34, 2, 2, 9], "rgba(40, 162, 0, 0.4)"),
  elytra("front", [36, 2, 10, 20], "rgba(216, 0, 255, 0.3)", "salami.capes.editor.area.elytra"),
  elytra("left", [22, 11, 1, 11], "rgba(40, 162, 0, 0.4)"),
];

/** Fläche unter einem Texturpixel (oder `null`, wenn Minecraft das Pixel nicht zeichnet). */
export function areaAt(x: number, y: number): EditorArea | null {
  return EDITOR_AREAS.find((area) => area.contains(x, y)) ?? null;
}
