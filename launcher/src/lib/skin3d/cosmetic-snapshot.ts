import { useEffect, useState } from "react";
import { SkinViewer } from "skinview3d";
import type { CatalogBuddy, CatalogEmote, CatalogHat } from "../../services/salami-cosmetics-service";
import {
  attachBuddy,
  attachEmoteExtras,
  attachHat,
  attachMiniMe,
  buildHat,
  frameFigure,
  poseEmote,
  viewOf,
  type BuddyObject,
  type BuddyPlace,
  type EmoteExtrasObject,
  type HatObject,
  type MiniMeObject,
  type MiniMePlace,
} from "./cosmetics";
import { DEFAULT_SKIN_URL, cosmeticsModelOf } from "./CosmeticsViewer";
import { disposeViewer } from "./dispose";
import type { SkinVariant } from "./flat-render";
import { isWebGLAvailable } from "./webgl";

/** Was auf ein Vorschaubild soll: die Figur mit einem Hut, einem Buddy, in der Haltung eines Emotes oder mit Mini Me. */
export interface CosmeticSnapshotRequest {
  /** Skin-Textur; `null` = Standard-Skin (Steve). */
  textureUrl: string | null;
  variant?: SkinVariant | "auto";
  hat?: CatalogHat;
  /** Buddy und wo er ist (ohne Angabe: über der Schulter). */
  buddy?: CatalogBuddy;
  buddyPlace?: BuddyPlace;
  emote?: CatalogEmote;
  miniMe?: MiniMePlace;
  /** Kantenlänge des quadratischen Bildes in Pixeln. */
  size: number;
}

/** Nach so langer Ruhe wird der WebGL-Kontext freigegeben. */
const IDLE_DISPOSE_MS = 15_000;

let shared: SkinViewer | null = null;
let sharedSkin: string | null = null;
let disposeTimer: ReturnType<typeof setTimeout> | null = null;
let queue: Promise<unknown> = Promise.resolve();

async function draw(request: CosmeticSnapshotRequest): Promise<string> {
  if (disposeTimer) clearTimeout(disposeTimer);
  if (!shared) {
    shared = new SkinViewer({
      width: request.size,
      height: request.size,
      preserveDrawingBuffer: true,
      renderPaused: true,
      enableControls: false,
      pixelRatio: 1,
    });
    sharedSkin = null;
  }
  const viewer = shared;
  let hat: HatObject | null = null;
  let buddy: BuddyObject | null = null;
  let extras: EmoteExtrasObject | null = null;
  let mini: MiniMeObject | null = null;
  try {
    viewer.setSize(request.size, request.size);
    const model = cosmeticsModelOf(request.textureUrl, request.variant);
    const skin = `${model}|${request.textureUrl ?? DEFAULT_SKIN_URL}`;
    if (skin !== sharedSkin) {
      sharedSkin = null;
      await viewer.loadSkin(request.textureUrl ?? DEFAULT_SKIN_URL, { model });
      sharedSkin = skin;
    }
    const player = viewer.playerObject;
    player.skin.resetJoints();
    if (request.hat) {
      hat = buildHat(request.hat);
      attachHat(player, request.hat, hat);
    }
    if (request.buddy) buddy = attachBuddy(player, request.buddy, request.buddyPlace ?? "shoulder");
    if (request.emote) {
      poseEmote(player.skin, request.emote, request.emote.iconTime, 1);
      extras = attachEmoteExtras(player, request.emote);
      extras?.update(request.emote.iconTime, 1);
    }
    if (request.miniMe) {
      mini = attachMiniMe(player, request.miniMe);
      mini.syncSkin(player);
    }
    frameFigure(viewer, viewOf({ ...request, buddy: request.buddy ? (request.buddyPlace ?? "shoulder") : null }));
    viewer.render();
    return viewer.canvas.toDataURL("image/png");
  } finally {
    hat?.dispose();
    buddy?.dispose();
    extras?.dispose();
    mini?.dispose();
    disposeTimer = setTimeout(() => {
      if (shared) disposeViewer(shared);
      shared = null;
    }, IDLE_DISPOSE_MS);
  }
}

/** Rendert ein Vorschaubild (Data-URL). Aufträge laufen nacheinander über einen gemeinsamen Viewer. */
export function renderCosmeticSnapshot(request: CosmeticSnapshotRequest): Promise<string> {
  const job = queue.then(() => draw(request));
  queue = job.catch(() => undefined);
  return job;
}

// Bilder je Katalog-Eintrag merken: lädt der Launcher den Katalog neu, sind es neue Einträge und neue Bilder.
const MINI_ME_KEYS: Record<MiniMePlace, object> = { hand: {}, shoulder: {}, head: {}, follow: {} };
const cache = new WeakMap<object, Map<string, Promise<string>>>();

function cached(request: CosmeticSnapshotRequest): Promise<string> {
  const item = request.hat ?? request.buddy ?? request.emote ?? (request.miniMe ? MINI_ME_KEYS[request.miniMe] : null);
  if (!item) return renderCosmeticSnapshot(request);
  let images = cache.get(item);
  if (!images) cache.set(item, (images = new Map()));
  const key = `${request.size}|${request.variant ?? "auto"}|${request.buddy ? (request.buddyPlace ?? "shoulder") : ""}|${request.textureUrl ?? ""}`;
  let image = images.get(key);
  if (!image) {
    image = renderCosmeticSnapshot(request);
    images.set(key, image);
    // ein fehlgeschlagener Versuch soll den nächsten nicht blockieren
    image.catch(() => images.delete(key));
  }
  return image;
}

/**
 * Vorschaubild eines Cosmetics als Data-URL. `null`, solange es gerendert wird, ohne WebGL oder
 * wenn `request` fehlt (z. B. der Skin noch lädt).
 */
export function useCosmeticSnapshot(request: CosmeticSnapshotRequest | null): string | null {
  const [image, setImage] = useState<string | null>(null);
  const { textureUrl, variant, hat, buddy, buddyPlace, emote, miniMe, size } = request ?? ({} as Partial<CosmeticSnapshotRequest>);
  const wanted = request !== null;
  useEffect(() => {
    setImage(null);
    if (!wanted || !isWebGLAvailable()) return;
    let cancelled = false;
    cached({ textureUrl: textureUrl ?? null, variant, hat, buddy, buddyPlace, emote, miniMe, size: size ?? 128 })
      .then((url) => {
        if (!cancelled) setImage(url);
      })
      .catch((error: unknown) => console.warn("[cosmetics] Vorschaubild konnte nicht erstellt werden:", error));
    return () => {
      cancelled = true;
    };
  }, [wanted, textureUrl, variant, hat, buddy, buddyPlace, emote, miniMe, size]);
  return image;
}
