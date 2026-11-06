/** Kann dieses System WebGL (für die 3D-Skin-Ansicht)? */
export interface WebGLSupport {
  available: boolean;
  version: 2 | 1 | null;
  reason: string | null;
}

let cached: WebGLSupport | null = null;

/** Prüft einmal pro Start, ob WebGL 2 oder 1 verfügbar ist. */
export function getWebGLSupport(): WebGLSupport {
  if (cached) return cached;
  try {
    const canvas = document.createElement("canvas");
    for (const [name, version] of [
      ["webgl2", 2],
      ["webgl", 1],
    ] as const) {
      const gl = canvas.getContext(name) as WebGLRenderingContext | null;
      if (gl) {
        gl.getExtension("WEBGL_lose_context")?.loseContext();
        cached = { available: true, version, reason: null };
        return cached;
      }
    }
    cached = { available: false, version: null, reason: "WebGL wird von diesem System nicht unterstützt" };
  } catch (error) {
    cached = { available: false, version: null, reason: error instanceof Error ? error.message : String(error) };
  }
  return cached;
}

export const isWebGLAvailable = (): boolean => getWebGLSupport().available;
