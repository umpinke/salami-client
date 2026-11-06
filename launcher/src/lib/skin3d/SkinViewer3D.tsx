import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { IdleAnimation, SkinViewer, WalkingAnimation } from "skinview3d";
import { disposeViewer } from "./dispose";
import type { SkinVariant } from "./flat-render";
import { getWebGLSupport, type WebGLSupport } from "./webgl";

/** Cape-Bild für die 3D-Ansicht; `elytra` zeigt es als Elytra statt als Cape. */
export interface SkinViewerCape {
  texture: string;
  elytra?: boolean;
}

export interface SkinViewer3DProps {
  /** Skin-Textur (Data-URL, Asset-URL oder https mit CORS). `null` = Standard-Skin (Steve). */
  textureUrl: string | null;
  /** `"auto"` erkennt schmale Arme am Bild. */
  variant?: SkinVariant | "auto";
  cape?: string | SkinViewerCape | null;
  /** Namensschild über dem Kopf. */
  nametag?: string | null;
  animation?: "idle" | "walk" | "none";
  /** Automatisch drehen (Geschwindigkeit wie `autoRotateSpeed` von skinview3d, 0 = aus). */
  spinSpeed?: number;
  /** Drehung um die Hochachse in Radiant. */
  rotation?: number;
  /** 1 = ganze Figur, größer = näher dran. */
  zoom?: number;
  /** Mit der Maus drehen. */
  draggable?: boolean;
  /** `"yaw"` dreht nur waagerecht, `"both"` auch nach oben/unten. */
  dragAxis?: "yaw" | "both";
  /** Nicht zeichnen (z. B. wenn die Seite verdeckt ist). */
  paused?: boolean;
  className?: string;
  style?: CSSProperties;
  /** Wird statt der 3D-Ansicht gezeigt, wenn WebGL fehlt. */
  unsupportedFallback?: ReactNode;
  onUnsupported?: (support: WebGLSupport) => void;
}

/** Standard-Skin, solange kein eigener da ist (liegt in `public/skins`). */
const DEFAULT_SKIN_URL = "/skins/steve.png";

const modelOf = (variant: SkinVariant | "auto") =>
  variant === "auto" ? "auto-detect" : variant === "slim" ? "slim" : "default";

/** 3D-Ansicht eines Minecraft-Spielers (skinview3d, MIT-Lizenz). */
export function SkinViewer3D({
  textureUrl,
  variant = "auto",
  cape = null,
  nametag = null,
  animation = "idle",
  spinSpeed = 0,
  rotation = 0,
  zoom = 0.9,
  draggable = true,
  dragAxis = "yaw",
  paused = false,
  className,
  style,
  unsupportedFallback = null,
  onUnsupported,
}: SkinViewer3DProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [viewer, setViewer] = useState<SkinViewer | null>(null);
  const [support] = useState(getWebGLSupport);

  useEffect(() => {
    if (!support.available) onUnsupported?.(support);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [support]);

  // Viewer einmal anlegen, Größe folgt dem Container
  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!support.available || !container || !canvas) return;
    let created: SkinViewer;
    try {
      created = new SkinViewer({
        canvas,
        width: Math.max(1, container.clientWidth),
        height: Math.max(1, container.clientHeight),
        pixelRatio: "match-device",
      });
    } catch (error) {
      console.warn("[SkinViewer3D] WebGL konnte nicht gestartet werden:", error);
      onUnsupported?.({ available: false, version: null, reason: String(error) });
      return;
    }
    created.controls.enableZoom = false;
    created.controls.enablePan = false;
    const resize = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      if (width > 0 && height > 0) created.setSize(width, height);
    });
    resize.observe(container);
    setViewer(created);
    return () => {
      resize.disconnect();
      disposeViewer(created);
      setViewer(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [support]);

  useEffect(() => {
    if (!viewer) return;
    const model = textureUrl ? modelOf(variant) : "default";
    viewer.loadSkin(textureUrl || DEFAULT_SKIN_URL, { model }).catch((error: unknown) => {
      console.warn("[SkinViewer3D] Skin konnte nicht geladen werden:", error);
    });
  }, [viewer, textureUrl, variant]);

  const capeTexture = typeof cape === "string" ? cape : (cape?.texture ?? null);
  const elytra = typeof cape === "object" && cape?.elytra === true;
  useEffect(() => {
    if (!viewer) return;
    if (!capeTexture) {
      viewer.loadCape(null);
      return;
    }
    viewer.loadCape(capeTexture, { backEquipment: elytra ? "elytra" : "cape" }).catch((error: unknown) => {
      console.warn("[SkinViewer3D] Cape konnte nicht geladen werden:", error);
    });
  }, [viewer, capeTexture, elytra]);

  useEffect(() => {
    if (viewer) viewer.nameTag = nametag || null;
  }, [viewer, nametag]);

  useEffect(() => {
    if (!viewer) return;
    viewer.animation = animation === "walk" ? new WalkingAnimation() : animation === "idle" ? new IdleAnimation() : null;
    if (animation === "none") viewer.playerObject.resetJoints();
  }, [viewer, animation]);

  useEffect(() => {
    if (viewer) viewer.playerWrapper.rotation.y = rotation;
  }, [viewer, rotation]);

  useEffect(() => {
    if (!viewer) return;
    viewer.autoRotate = spinSpeed !== 0;
    viewer.autoRotateSpeed = spinSpeed;
  }, [viewer, spinSpeed]);

  useEffect(() => {
    if (viewer) viewer.zoom = zoom;
  }, [viewer, zoom]);

  useEffect(() => {
    if (!viewer) return;
    viewer.controls.enableRotate = draggable;
    const level = Math.PI / 2;
    viewer.controls.minPolarAngle = dragAxis === "yaw" ? level : 0;
    viewer.controls.maxPolarAngle = dragAxis === "yaw" ? level : Math.PI;
  }, [viewer, draggable, dragAxis]);

  useEffect(() => {
    if (viewer) viewer.renderPaused = paused;
  }, [viewer, paused]);

  if (!support.available) return <>{unsupportedFallback}</>;
  return (
    <div ref={containerRef} className={className} style={{ position: "relative", ...style }}>
      <canvas ref={canvasRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
    </div>
  );
}
