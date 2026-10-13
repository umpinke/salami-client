/** Rechteck im 64x32-Raster einer Cape-Textur: [x, y, breite, höhe]. */
export type CapeRect = readonly [number, number, number, number];

export const TEXTURE_WIDTH = 64;
export const TEXTURE_HEIGHT = 32;

/** Cape-Box 10x16x1 (Minecraft-Layout). `outside` sieht man im Spiel von hinten. */
export const CAPE = {
  outside: [1, 1, 10, 16],
  inside: [12, 1, 10, 16],
  top: [1, 0, 10, 1],
  bottom: [11, 0, 10, 1],
  left: [0, 1, 1, 16],
  right: [11, 1, 1, 16],
} as const satisfies Record<string, CapeRect>;

/** Elytra-Box 10x20x2 – nutzt dieselbe Textur. */
export const ELYTRA = {
  outside: [24, 2, 10, 20],
  inside: [36, 2, 10, 20],
  top: [24, 0, 10, 2],
  bottom: [34, 0, 10, 2],
  left: [22, 2, 2, 20],
  right: [34, 2, 2, 20],
} as const satisfies Record<string, CapeRect>;

/** Färbung der Flächen im Editor (Hilfsebene, wird nicht gespeichert). */
export const EDITOR_AREAS: { rect: CapeRect; color: string; labelKey?: string }[] = [
  { rect: CAPE.outside, color: "rgba(178, 58, 58, 0.35)", labelKey: "salami.capes.editor.area.outside" },
  { rect: CAPE.inside, color: "rgba(122, 36, 37, 0.35)", labelKey: "salami.capes.editor.area.inside" },
  { rect: CAPE.top, color: "rgba(243, 227, 211, 0.3)" },
  { rect: CAPE.bottom, color: "rgba(243, 227, 211, 0.3)" },
  { rect: CAPE.left, color: "rgba(122, 74, 53, 0.4)" },
  { rect: CAPE.right, color: "rgba(122, 74, 53, 0.4)" },
  { rect: ELYTRA.outside, color: "rgba(196, 150, 98, 0.3)", labelKey: "salami.capes.editor.area.elytra" },
  { rect: ELYTRA.inside, color: "rgba(164, 118, 72, 0.3)" },
  { rect: ELYTRA.top, color: "rgba(243, 227, 211, 0.25)" },
  { rect: ELYTRA.bottom, color: "rgba(243, 227, 211, 0.25)" },
  { rect: ELYTRA.left, color: "rgba(74, 40, 30, 0.35)" },
  { rect: ELYTRA.right, color: "rgba(74, 40, 30, 0.35)" },
];

/** Liegt ein Texturpixel in einer der benutzten Flächen? */
export function isUsedPixel(x: number, y: number): boolean {
  return EDITOR_AREAS.some(({ rect: [rx, ry, w, h] }) => x >= rx && x < rx + w && y >= ry && y < ry + h);
}
