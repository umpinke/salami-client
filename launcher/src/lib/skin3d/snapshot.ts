import { IdleAnimation, SkinViewer } from "skinview3d";
import { disposeViewer } from "./dispose";
import type { SkinVariant } from "./flat-render";
import type { SkinViewerCape } from "./SkinViewer3D";

/** Was auf ein Vorschaubild soll. */
export interface SkinSnapshotRequest {
  textureUrl: string;
  variant?: SkinVariant | "auto";
  cape?: string | SkinViewerCape | null;
  /** Namensschild über dem Kopf. */
  nametag?: string | null;
  /** Drehung um die Hochachse in Radiant (leicht schräg zeigt die Figur räumlich). */
  rotation?: number;
}

export interface SkinSnapshotOptions {
  width: number;
  height: number;
  mimeType?: string;
  quality?: number;
}

const DEFAULT_ROTATION = -0.5;
/** Zeitpunkt der Leerlauf-Animation für die Pose (Arme leicht vom Körper). */
const POSE_TIME = 1.2;
/** Nach so langer Ruhe wird der WebGL-Kontext freigegeben. */
const IDLE_DISPOSE_MS = 15_000;

let shared: SkinViewer | null = null;
let disposeTimer: ReturnType<typeof setTimeout> | null = null;
let queue: Promise<unknown> = Promise.resolve();

const modelOf = (variant: SkinVariant | "auto" | undefined) =>
  !variant || variant === "auto" ? "auto-detect" : variant === "slim" ? "slim" : "default";

async function draw(request: SkinSnapshotRequest, options: SkinSnapshotOptions): Promise<Blob> {
  if (disposeTimer) clearTimeout(disposeTimer);
  if (!shared) {
    shared = new SkinViewer({
      width: options.width,
      height: options.height,
      preserveDrawingBuffer: true,
      renderPaused: true,
      enableControls: false,
      pixelRatio: 1,
    });
  }
  const viewer = shared;
  try {
    viewer.setSize(options.width, options.height);
    await viewer.loadSkin(request.textureUrl, { model: modelOf(request.variant) });
    const cape = typeof request.cape === "string" ? { texture: request.cape } : request.cape;
    if (cape?.texture) await viewer.loadCape(cape.texture, { backEquipment: cape.elytra ? "elytra" : "cape" });
    else viewer.loadCape(null);
    viewer.nameTag = request.nametag || null;

    viewer.playerObject.resetJoints();
    new IdleAnimation().update(viewer.playerObject, POSE_TIME);
    viewer.playerWrapper.rotation.y = request.rotation ?? DEFAULT_ROTATION;
    // etwas Abstand, damit Arme und Namensschild ganz aufs Bild passen
    viewer.zoom = request.nametag ? 0.68 : 0.78;
    viewer.render();

    return await new Promise<Blob>((resolve, reject) =>
      viewer.canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error("Vorschaubild konnte nicht erstellt werden"))),
        options.mimeType ?? "image/png",
        options.quality,
      ),
    );
  } finally {
    disposeTimer = setTimeout(() => {
      if (shared) disposeViewer(shared);
      shared = null;
    }, IDLE_DISPOSE_MS);
  }
}

/** Rendert ein Vorschaubild der Figur. Aufträge laufen nacheinander über einen gemeinsamen Viewer. */
export function renderSkinSnapshot(request: SkinSnapshotRequest, options: SkinSnapshotOptions): Promise<Blob> {
  const job = queue.then(() => draw(request, options));
  queue = job.catch(() => undefined);
  return job;
}
