/**
 * 2D-Vorderansicht eines Minecraft-Skins (ohne WebGL): Kopf, Körper, Arme und Beine samt
 * zweiter Ebene, pixelgenau auf ein Canvas gezeichnet.
 */

/** Schmale Arme (3 px, „Alex“) oder breite (4 px, „Steve“). */
export type SkinVariant = "classic" | "slim";

export function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.decoding = "async";
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Skin konnte nicht geladen werden"));
    image.src = src;
  });
}

/**
 * Schmale Arme erkennen: bei Alex-Skins ist die vierte Spalte der rechten Arm-Vorderseite
 * (x = 54, y = 20…31 im 64er-Raster) durchsichtig.
 */
export function detectVariant(image: HTMLImageElement): SkinVariant {
  if (image.height < image.width) return "classic"; // altes 64×32-Format kennt nur breite Arme
  const scale = image.width / 64;
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(scale));
  canvas.height = Math.max(1, Math.round(12 * scale));
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) return "classic";
  ctx.drawImage(image, 54 * scale, 20 * scale, scale, 12 * scale, 0, 0, canvas.width, canvas.height);
  const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height);
  for (let i = 3; i < data.length; i += 4) if (data[i] !== 0) return "classic";
  return "slim";
}

interface Part {
  /** Quelle im 64er-Raster: x, y, Breite, Höhe */
  src: [number, number, number, number];
  /** Ziel im 16×32-Raster: x, y */
  dest: [number, number];
  mirror?: boolean;
}

function frontParts(armWidth: number, legacy: boolean): Part[] {
  const parts: Part[] = [
    { src: [8, 8, 8, 8], dest: [4, 0] }, // Kopf
    { src: [20, 20, 8, 12], dest: [4, 8] }, // Körper
    { src: [44, 20, armWidth, 12], dest: [4 - armWidth, 8] }, // rechter Arm
    { src: [4, 20, 4, 12], dest: [4, 20] }, // rechtes Bein
  ];
  if (legacy) {
    // 64×32: linker Arm und linkes Bein sind gespiegelte rechte
    parts.push({ src: [44, 20, armWidth, 12], dest: [12, 8], mirror: true });
    parts.push({ src: [4, 20, 4, 12], dest: [8, 20], mirror: true });
    parts.push({ src: [40, 8, 8, 8], dest: [4, 0] }); // Hut
    return parts;
  }
  parts.push(
    { src: [36, 52, armWidth, 12], dest: [12, 8] }, // linker Arm
    { src: [20, 52, 4, 12], dest: [8, 20] }, // linkes Bein
    // zweite Ebene
    { src: [40, 8, 8, 8], dest: [4, 0] },
    { src: [20, 36, 8, 12], dest: [4, 8] },
    { src: [44, 36, armWidth, 12], dest: [4 - armWidth, 8] },
    { src: [52, 52, armWidth, 12], dest: [12, 8] },
    { src: [4, 36, 4, 12], dest: [4, 20] },
    { src: [4, 52, 4, 12], dest: [8, 20] },
  );
  return parts;
}

export interface FlatSkinOptions {
  variant?: SkinVariant | "auto";
  /** Pixel pro Skin-Pixel im Ergebnis (16×32 Skin-Pixel). */
  scale?: number;
}

/** Vorderansicht als PNG-Data-URL. */
export async function renderFlatSkin(textureUrl: string, options: FlatSkinOptions = {}): Promise<string> {
  const image = await loadImage(textureUrl);
  const unit = image.width / 64;
  const legacy = image.height * 2 === image.width;
  const variant = !options.variant || options.variant === "auto" ? detectVariant(image) : options.variant;
  const scale = options.scale ?? 8;

  const canvas = document.createElement("canvas");
  canvas.width = 16 * scale;
  canvas.height = 32 * scale;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas nicht verfügbar");
  ctx.imageSmoothingEnabled = false;

  for (const { src, dest, mirror } of frontParts(variant === "slim" ? 3 : 4, legacy)) {
    const [sx, sy, sw, sh] = src;
    const [dx, dy] = dest;
    ctx.save();
    if (mirror) {
      ctx.translate((dx + sw) * scale, 0);
      ctx.scale(-1, 1);
      ctx.drawImage(image, sx * unit, sy * unit, sw * unit, sh * unit, 0, dy * scale, sw * scale, sh * scale);
    } else {
      ctx.drawImage(image, sx * unit, sy * unit, sw * unit, sh * unit, dx * scale, dy * scale, sw * scale, sh * scale);
    }
    ctx.restore();
  }
  return canvas.toDataURL("image/png");
}
