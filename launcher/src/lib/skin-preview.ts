/**
 * Vorschaubilder von Skins (Skins-Tab, Startseite ohne 3D): einmal mit skinview3d gerendert,
 * im Launcher-Ordner zwischengespeichert (`get_cached_skin_render` / `store_skin_render`).
 * Ohne WebGL gibt es die flache Vorderansicht.
 */
import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import {
  isWebGLAvailable,
  renderFlatSkin,
  renderSkinSnapshot,
  type SkinSnapshotRequest,
} from "./skin3d";

/** Ändert sich die Darstellung, Version erhöhen – alte Bilder im Cache werden dann nicht mehr benutzt. */
const RENDER_VERSION = "skinview3d-1";
const RENDER_WIDTH = 280;
const RENDER_HEIGHT = 560;

const FORMATS = {
  png: { mimeType: "image/png", extension: "png" },
  webp: { mimeType: "image/webp", extension: "webp" },
} as const;

export type SkinPreviewFormat = keyof typeof FORMATS;
export type { SkinSnapshotRequest };

export interface SkinPreviewOptions {
  width?: number;
  height?: number;
  format?: SkinPreviewFormat;
  quality?: number;
  cacheKey?: string;
}

const resolved = new Map<string, string>();
const pending = new Map<string, Promise<string>>();

async function sha1(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export async function skinPreviewKey(request: SkinSnapshotRequest, options: SkinPreviewOptions = {}): Promise<string> {
  if (options.cacheKey) return options.cacheKey;
  const cape = typeof request.cape === "string" ? request.cape : (request.cape?.texture ?? null);
  return sha1(
    JSON.stringify([
      RENDER_VERSION,
      options.width ?? RENDER_WIDTH,
      options.height ?? RENDER_HEIGHT,
      options.format ?? "png",
      await sha1(request.textureUrl),
      request.variant ?? "auto",
      cape ? await sha1(cape) : null,
      typeof request.cape === "object" && request.cape?.elytra === true,
      request.nametag ?? null,
      request.rotation ?? null,
    ]),
  );
}

async function readFromDisk(key: string, extension: string): Promise<string | null> {
  const path = await invoke<string | null>("get_cached_skin_render", { cacheKey: key, extension });
  return path ? convertFileSrc(path) : null;
}

async function writeToDisk(key: string, extension: string, blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  const path = await invoke<string>("store_skin_render", { cacheKey: key, extension, base64Data: btoa(binary) });
  return convertFileSrc(path);
}

export async function getSkinPreview(request: SkinSnapshotRequest, options: SkinPreviewOptions = {}): Promise<string> {
  if (!isWebGLAvailable()) {
    return renderFlatSkin(request.textureUrl, { variant: request.variant });
  }

  const format = FORMATS[options.format ?? "png"];
  const key = await skinPreviewKey(request, options);
  const known = resolved.get(key);
  if (known) return known;
  const running = pending.get(key);
  if (running) return running;

  const task = (async () => {
    const cached = await readFromDisk(key, format.extension).catch(() => null);
    if (cached) {
      resolved.set(key, cached);
      return cached;
    }
    const blob = await renderSkinSnapshot(request, {
      width: options.width ?? RENDER_WIDTH,
      height: options.height ?? RENDER_HEIGHT,
      mimeType: format.mimeType,
      quality: options.quality,
    });
    const url = await writeToDisk(key, format.extension, blob).catch((error: unknown) => {
      console.warn("[SkinPreview] Bild konnte nicht zwischengespeichert werden:", error);
      return URL.createObjectURL(blob);
    });
    resolved.set(key, url);
    return url;
  })().finally(() => pending.delete(key));

  pending.set(key, task);
  return task;
}
