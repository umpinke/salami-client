import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { FunctionAnimation, SkinViewer } from "skinview3d";
import type { CatalogBuddy, CatalogEmote, CatalogHat } from "../../services/salami-cosmetics-service";
import {
  attachBuddy,
  attachEmoteExtras,
  attachHat,
  attachMiniMe,
  buildHat,
  emoteClock,
  frameFigure,
  poseEmote,
  poseIdle,
  viewOf,
  type BuddyObject,
  type BuddyPlace,
  type EmoteExtrasObject,
  type HatObject,
  type MiniMeObject,
  type MiniMePlace,
} from "./cosmetics";
import { disposeViewer } from "./dispose";
import type { SkinVariant } from "./flat-render";
import { getWebGLSupport } from "./webgl";

export interface CosmeticsViewerProps {
  /** Skin-Textur (Data-URL oder Asset-URL). `null` = Standard-Skin (Steve). */
  textureUrl: string | null;
  variant?: SkinVariant | "auto";
  /** Hut, den die Figur trägt. */
  hat?: CatalogHat | null;
  /** Buddy, der die Figur begleitet, und wo er ist (ohne Angabe: über der Schulter). */
  buddy?: CatalogBuddy | null;
  buddyPlace?: BuddyPlace;
  /** Emote, das die Figur in Dauerschleife zeigt – mit Requisiten und Verwandlung, wenn es welche hat. */
  emote?: CatalogEmote | null;
  miniMe?: MiniMePlace | null;
  className?: string;
  style?: CSSProperties;
  /** Wird statt der 3D-Ansicht gezeigt, wenn WebGL fehlt. */
  unsupportedFallback?: ReactNode;
}

/** Standard-Skin, solange kein eigener da ist (liegt in `public/skins`). */
export const DEFAULT_SKIN_URL = "/skins/steve.png";
/**
 * Armform zum Skin. Beim Standard-Skin entscheidet das Bild: bei erhobenen Armen (Emotes) sieht man
 * ihre Rückseite, und die passt nur zur richtigen Armbreite.
 */
export const cosmeticsModelOf = (textureUrl: string | null, variant: SkinVariant | "auto" | undefined) =>
  !textureUrl || !variant || variant === "auto" ? "auto-detect" : variant === "slim" ? "slim" : "default";

/** Was gerade an der Figur hängt – die Animation liest es in jedem Bild. */
interface Stage {
  hat: HatObject | null;
  buddy: BuddyObject | null;
  mini: MiniMeObject | null;
  emote: CatalogEmote | null;
  extras: EmoteExtrasObject | null;
  emoteStart: number;
  now: number;
}

/** 3D-Figur mit Salami-Cosmetics: Hut, Buddy, Emote und Mini Me wie im Spiel. Mit der Maus drehbar. */
export function CosmeticsViewer({
  textureUrl,
  variant = "auto",
  hat = null,
  buddy = null,
  buddyPlace = "shoulder",
  emote = null,
  miniMe = null,
  className,
  style,
  unsupportedFallback = null,
}: CosmeticsViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const stage = useRef<Stage>({ hat: null, buddy: null, mini: null, emote: null, extras: null, emoteStart: 0, now: 0 });
  const [viewer, setViewer] = useState<SkinViewer | null>(null);
  const [support] = useState(getWebGLSupport);
  const [failed, setFailed] = useState(false);

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
      console.warn("[CosmeticsViewer] WebGL konnte nicht gestartet werden:", error);
      setFailed(true);
      return;
    }
    created.controls.enableZoom = false;
    created.controls.enablePan = false;
    // nur ein Stück nach oben und unten kippen – von unten gibt es nichts zu sehen
    created.controls.minPolarAngle = Math.PI / 2 - 0.7;
    created.controls.maxPolarAngle = Math.PI / 2 + 0.25;
    created.animation = new FunctionAnimation((player, progress) => {
      const current = stage.current;
      current.now = progress;
      poseIdle(player.skin, progress);
      if (current.emote) {
        const { t, weight } = emoteClock(current.emote, progress - current.emoteStart);
        if (weight > 0) poseEmote(player.skin, current.emote, t, weight);
        current.extras?.update(t, weight);
      }
      current.hat?.animate(progress);
      current.buddy?.animate(progress);
      current.mini?.animate(progress);
    });
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
  }, [support]);

  useEffect(() => {
    if (!viewer) return;
    viewer
      .loadSkin(textureUrl || DEFAULT_SKIN_URL, { model: cosmeticsModelOf(textureUrl, variant) })
      // das Mini Me trägt denselben Skin: nach jedem Laden neu übernehmen
      .then(() => stage.current.mini?.syncSkin(viewer.playerObject))
      .catch((error: unknown) => console.warn("[CosmeticsViewer] Skin konnte nicht geladen werden:", error));
  }, [viewer, textureUrl, variant]);

  // Ausschnitt und Drehung passen zum gezeigten Cosmetic (Hut am Kopf, Mini Me neben der Figur …)
  const view = useMemo(
    () => viewOf({ hat, buddy: buddy ? buddyPlace : null, emote, miniMe }),
    [hat, buddy, buddyPlace, emote, miniMe],
  );
  useEffect(() => {
    if (viewer) frameFigure(viewer, view);
  }, [viewer, view.focus, view.rotation]);

  useEffect(() => {
    if (!viewer || !hat) return;
    const object = buildHat(hat);
    attachHat(viewer.playerObject, hat, object);
    stage.current.hat = object;
    return () => {
      stage.current.hat = null;
      object.dispose();
    };
  }, [viewer, hat]);

  useEffect(() => {
    if (!viewer || !buddy) return;
    const object = attachBuddy(viewer.playerObject, buddy, buddyPlace);
    stage.current.buddy = object;
    return () => {
      stage.current.buddy = null;
      object.dispose();
    };
  }, [viewer, buddy, buddyPlace]);

  useEffect(() => {
    if (!viewer || !miniMe) return;
    const object = attachMiniMe(viewer.playerObject, miniMe);
    object.syncSkin(viewer.playerObject);
    stage.current.mini = object;
    return () => {
      stage.current.mini = null;
      object.dispose();
    };
  }, [viewer, miniMe]);

  // ein anderes Emote beginnt von vorn – mit dem, was es mitbringt (Requisiten, Verwandlung)
  useEffect(() => {
    stage.current.emote = emote;
    stage.current.emoteStart = stage.current.now;
    if (!viewer || !emote) return;
    const extras = attachEmoteExtras(viewer.playerObject, emote);
    stage.current.extras = extras;
    return () => {
      stage.current.extras = null;
      extras?.dispose();
    };
  }, [viewer, emote]);

  if (!support.available || failed) return <>{unsupportedFallback}</>;
  return (
    <div ref={containerRef} className={className} style={{ position: "relative", ...style }}>
      <canvas ref={canvasRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
    </div>
  );
}
