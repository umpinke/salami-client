import { Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, useTexture } from "@react-three/drei";
import * as THREE from "three";
import { cn } from "../../lib/utils";
import { WebGLBoundary } from "../WebGLBoundary";
import { CapeFace } from "./CapeFace";
import { CAPE, TEXTURE_HEIGHT, TEXTURE_WIDTH, type CapeRect } from "./cape-layout";
import "./i18n";

type PreviewMode = "cape" | "elytra";

/**
 * Box mit UVs aus dem Minecraft-Layout. Flächen-Reihenfolge von BoxGeometry:
 * +x, -x, +y, -y, +z (zur Kamera), -z.
 */
function texturedBox(size: [number, number, number], faces: CapeRect[]): THREE.BoxGeometry {
  const geometry = new THREE.BoxGeometry(...size);
  const uv = geometry.attributes.uv as THREE.BufferAttribute;
  faces.forEach(([x, y, w, h], face) => {
    const u0 = x / TEXTURE_WIDTH;
    const u1 = (x + w) / TEXTURE_WIDTH;
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

/**
 * Würfel genau wie Minecraft (`ModelPart.Cube`): Ecken, Flächen und UVs 1:1 aus dem
 * Spielcode, damit die Vorschau das echte Modell zeigt. Koordinaten im Modellraum von
 * Minecraft (Pixel; +x = links vom Spieler, +y = unten, +z = nach hinten).
 */
function minecraftCube(
  [texU, texV]: [number, number],
  [x, y, z]: [number, number, number],
  [w, h, d]: [number, number, number],
  inflate: number,
  mirror: boolean,
): THREE.BufferGeometry {
  let x0 = x - inflate;
  const y0 = y - inflate;
  const z0 = z - inflate;
  let x1 = x + w + inflate;
  const y1 = y + h + inflate;
  const z1 = z + d + inflate;
  if (mirror) [x0, x1] = [x1, x0];
  const c = {
    v1: [x0, y0, z0], v2: [x1, y0, z0], v3: [x1, y1, z0], v4: [x0, y1, z0],
    v5: [x0, y0, z1], v6: [x1, y0, z1], v7: [x1, y1, z1], v8: [x0, y1, z1],
  };
  const u = [texU, texU + d, texU + d + w, texU + d + w + w, texU + d + w + d, texU + d + w + d + w];
  const v = [texV, texV + d, texV + d + h];
  // [Ecken, u1, v1, u2, v2] wie in ModelPart.Cube (DOWN, UP, WEST, NORTH, EAST, SOUTH)
  const faces: [number[][], number, number, number, number][] = [
    [[c.v6, c.v5, c.v1, c.v2], u[1], v[0], u[2], v[1]],
    [[c.v3, c.v4, c.v8, c.v7], u[2], v[1], u[3], v[0]],
    [[c.v1, c.v5, c.v8, c.v4], u[0], v[1], u[1], v[2]],
    [[c.v2, c.v1, c.v4, c.v3], u[1], v[1], u[2], v[2]],
    [[c.v6, c.v2, c.v3, c.v7], u[2], v[1], u[4], v[2]],
    [[c.v5, c.v6, c.v7, c.v8], u[4], v[1], u[5], v[2]],
  ];
  const positions: number[] = [];
  const uvs: number[] = [];
  for (const [corners, u1, v1, u2, v2] of faces) {
    // ModelPart.Polygon: Ecke 0 → (u2,v1), 1 → (u1,v1), 2 → (u1,v2), 3 → (u2,v2)
    const quad = corners.map((pos, i) => ({
      pos,
      uv: [[u2, v1], [u1, v1], [u1, v2], [u2, v2]][i],
    }));
    if (mirror) quad.reverse();
    for (const i of [0, 1, 2, 0, 2, 3]) {
      positions.push(...quad[i].pos);
      uvs.push(quad[i].uv[0] / TEXTURE_WIDTH, 1 - quad[i].uv[1] / TEXTURE_HEIGHT);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
  geometry.computeVertexNormals();
  return geometry;
}

/** Stehende Haltung aus ElytraModel/ElytraAnimationState: 15° nach hinten, 15° zur Seite. */
const ELYTRA_TILT = Math.PI / 12;

/**
 * Elytra exakt wie im Spiel (ElytraModel): zwei Flügel 10x20x2 bei Textur (22,0), um 1 Pixel
 * aufgeblasen, Drehpunkte bei x = ±5, der rechte Flügel gespiegelt. Von hinten gesehen.
 */
function ElytraMesh({ src }: { src: string }) {
  const texture = useCapeTexture(src);
  const [left, right] = useMemo(
    () => [
      minecraftCube([22, 0], [-10, 0, 0], [10, 20, 2], 1, false),
      minecraftCube([22, 0], [0, 0, 0], [10, 20, 2], 1, true),
    ],
    [],
  );
  return (
    // Modellraum von Minecraft (y nach unten, x nach links) → three.js: 180° um z; mittig rücken
    <group position={[0, 11, 0]} rotation={[0, 0, Math.PI]}>
      <mesh geometry={left} position={[5, 0, 2]} rotation={new THREE.Euler(ELYTRA_TILT, 0, -ELYTRA_TILT, "ZYX")}>
        <meshStandardMaterial map={texture} transparent alphaTest={0.05} side={THREE.DoubleSide} />
      </mesh>
      <mesh geometry={right} position={[-5, 0, 2]} rotation={new THREE.Euler(ELYTRA_TILT, 0, ELYTRA_TILT, "ZYX")}>
        <meshStandardMaterial map={texture} transparent alphaTest={0.05} side={THREE.DoubleSide} />
      </mesh>
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
