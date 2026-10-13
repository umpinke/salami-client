import { Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, useTexture } from "@react-three/drei";
import * as THREE from "three";
import { cn } from "../../lib/utils";
import { WebGLBoundary } from "../WebGLBoundary";
import { CapeFace } from "./CapeFace";
import { CAPE, ELYTRA, TEXTURE_HEIGHT, TEXTURE_WIDTH, type CapeRect } from "./cape-layout";
import "./i18n";

type PreviewMode = "cape" | "elytra";

/**
 * Box mit UVs aus dem Minecraft-Layout. Flächen-Reihenfolge von BoxGeometry:
 * +x, -x, +y, -y, +z (zur Kamera), -z. `mirror` spiegelt die Textur (rechter Elytra-Flügel).
 */
function texturedBox(size: [number, number, number], faces: CapeRect[], mirror = false): THREE.BoxGeometry {
  const geometry = new THREE.BoxGeometry(...size);
  const uv = geometry.attributes.uv as THREE.BufferAttribute;
  faces.forEach(([x, y, w, h], face) => {
    let u0 = x / TEXTURE_WIDTH;
    let u1 = (x + w) / TEXTURE_WIDTH;
    if (mirror) [u0, u1] = [u1, u0];
    const top = 1 - y / TEXTURE_HEIGHT;
    const bottom = 1 - (y + h) / TEXTURE_HEIGHT;
    const i = face * 4;
    uv.setXY(i, u0, top);
    uv.setXY(i + 1, u1, top);
    uv.setXY(i + 2, u0, bottom);
    uv.setXY(i + 3, u1, bottom);
  });
  uv.needsUpdate = true;
  return geometry;
}

function useCapeTexture(src: string): THREE.Texture {
  const texture = useTexture(src);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

/** Cape 10x16x1, „Vorne“ zeigt zur Kamera (so sieht man es im Spiel von hinten). */
function CapeMesh({ src }: { src: string }) {
  const texture = useCapeTexture(src);
  const geometry = useMemo(
    () => texturedBox([10, 16, 1], [CAPE.left, CAPE.right, CAPE.top, CAPE.bottom, CAPE.outside, CAPE.inside]),
    [],
  );
  return (
    <mesh geometry={geometry} rotation={[0.12, 0, 0]}>
      <meshStandardMaterial map={texture} transparent alphaTest={0.05} />
    </mesh>
  );
}

/** Elytra: zwei Flügel 10x20x2 als umgedrehtes V, von hinten gesehen; der rechte ist gespiegelt. */
function ElytraMesh({ src }: { src: string }) {
  const texture = useCapeTexture(src);
  const [left, right] = useMemo(
    () => [
      texturedBox([10, 20, 2], [ELYTRA.right, ELYTRA.left, ELYTRA.top, ELYTRA.bottom, ELYTRA.outside, ELYTRA.inside]),
      texturedBox([10, 20, 2], [ELYTRA.left, ELYTRA.right, ELYTRA.top, ELYTRA.bottom, ELYTRA.outside, ELYTRA.inside], true),
    ],
    [],
  );
  const material = <meshStandardMaterial map={texture} transparent alphaTest={0.05} side={THREE.DoubleSide} />;
  return (
    <group rotation={[0.12, 0, 0]} position={[0, 0, 0]}>
      <group position={[0, 10, 0]} rotation={[0, 0, -0.26]}>
        <mesh geometry={left} position={[-5, -10, 0]}>
          {material}
        </mesh>
      </group>
      <group position={[0, 10, 0]} rotation={[0, 0, 0.26]}>
        <mesh geometry={right} position={[5, -10, 0.01]}>
          {material}
        </mesh>
      </group>
    </group>
  );
}

/** Drehende 3D-Vorschau eines Capes oder seiner Elytra (mit der Maus drehbar). Ohne WebGL: flache Ansicht. */
export function Cape3D({ src, className }: { src: string; className?: string }) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<PreviewMode>("cape");
  return (
    <div className={cn("relative", className)}>
      <WebGLBoundary label="cape preview" fallback={<CapeFace src={src} width={150} className="mx-auto mt-6" />}>
        <Canvas camera={{ position: [0, 0, mode === "cape" ? 26 : 40], fov: 45 }} dpr={[1, 2]} key={mode}>
          <ambientLight intensity={1.7} />
          <directionalLight position={[6, 10, 12]} intensity={1.2} />
          <Suspense fallback={null}>
            {mode === "cape" ? <CapeMesh key={src} src={src} /> : <ElytraMesh key={src} src={src} />}
          </Suspense>
          <OrbitControls autoRotate autoRotateSpeed={2.5} enablePan={false} enableZoom={false} />
        </Canvas>
      </WebGLBoundary>
      <div className="absolute left-2 top-2 flex gap-1 rounded-lg bg-black/40 p-1">
        {(["cape", "elytra"] as const).map((item) => (
          <button
            key={item}
            type="button"
            aria-pressed={mode === item}
            onClick={() => setMode(item)}
            className={cn(
              "rounded-md px-2 py-0.5 text-xs text-white transition-colors",
              mode === item ? "bg-white/25" : "hover:bg-white/10",
            )}
          >
            {t(`salami.capes.preview.${item}`)}
          </button>
        ))}
      </div>
    </div>
  );
}
