import type { SkinViewer } from "skinview3d";

/**
 * Gibt einen skinview3d-Viewer frei. skinview3d 3.4 ruft dabei `fxaaPass.fsQuad.dispose()`
 * auf; im three.js des Launchers (eine gemeinsame Version, siehe `dedupe` in vite.config.ts)
 * heißt das Feld `_fsQuad`. Ohne diese Brücke wirft `dispose()` einen Fehler.
 */
export function disposeViewer(viewer: SkinViewer): void {
  const pass = viewer.fxaaPass as unknown as { fsQuad?: unknown; _fsQuad?: unknown };
  if (pass.fsQuad === undefined && pass._fsQuad !== undefined) pass.fsQuad = pass._fsQuad;
  try {
    viewer.dispose();
  } catch (error) {
    console.warn("[skin3d] Viewer konnte nicht vollständig freigegeben werden:", error);
  }
}
