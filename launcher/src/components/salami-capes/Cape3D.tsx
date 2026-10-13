import { Suspense, useMemo } from "react";
import { Canvas } from "@react-three/fiber";
import { OrbitControls, useTexture } from "@react-three/drei";
import * as THREE from "three";
import { WebGLBoundary } from "../WebGLBoundary";
import { CapeFace } from "./CapeFace";
import { CAPE, TEXTURE_HEIGHT, TEXTURE_WIDTH, type CapeRect } from "./cape-layout";

/**
 * Cape als 10x16x1-Box mit den UVs aus dem Minecraft-Layout.
 * Flächen-Reihenfolge von BoxGeometry: +x, -x, +y, -y, +z (außen), -z (innen).
 */
function capeGeometry(): THREE.BoxGeometry {
  const geometry = new THREE.BoxGeometry(10, 16, 1);
  const uv = geometry.attributes.uv as THREE.BufferAttribute;
  const faces: CapeRect[] = [CAPE.left, CAPE.right, CAPE.top, CAPE.bottom, CAPE.outside, CAPE.inside];
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

function CapeMesh({ src }: { src: string }) {
  const texture = useTexture(src);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  const geometry = useMemo(capeGeometry, []);
  return (
    <mesh geometry={geometry} rotation={[0.12, 0, 0]}>
      <meshStandardMaterial map={texture} transparent alphaTest={0.05} />
    </mesh>
  );
}

/** Drehende 3D-Vorschau eines Capes (mit der Maus drehbar). Ohne WebGL: flache Ansicht. */
export function Cape3D({ src, className }: { src: string; className?: string }) {
  return (
    <div className={className}>
      <WebGLBoundary label="cape preview" fallback={<CapeFace src={src} width={150} className="mx-auto mt-6" />}>
        <Canvas camera={{ position: [0, 0, 26], fov: 45 }} dpr={[1, 2]}>
          <ambientLight intensity={1.7} />
          <directionalLight position={[6, 10, 12]} intensity={1.2} />
          <Suspense fallback={null}>
            <CapeMesh key={src} src={src} />
          </Suspense>
          <OrbitControls autoRotate autoRotateSpeed={2.5} enablePan={false} enableZoom={false} />
        </Canvas>
      </WebGLBoundary>
    </div>
  );
}
