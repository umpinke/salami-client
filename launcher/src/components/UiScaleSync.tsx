import { useEffect } from "react";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { useUiScaleStore } from "../store/ui-scale-store";

/**
 * Wendet die Oberflächengröße auf das Launcher-Fenster an (wie Browser-Zoom) und
 * hört auf Strg + / Strg − / Strg 0.
 */
export function UiScaleSync() {
  const scale = useUiScaleStore((s) => s.scale);

  useEffect(() => {
    getCurrentWebview()
      .setZoom(scale)
      .catch((err) => console.error("Failed to set UI scale:", err));
  }, [scale]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.altKey) return;
      const { zoomIn, zoomOut, reset } = useUiScaleStore.getState();
      switch (event.code) {
        case "Equal":
        case "NumpadAdd":
        case "BracketRight": // „+“ auf deutschen Tastaturen
          zoomIn();
          break;
        case "Minus":
        case "NumpadSubtract":
        case "Slash": // „-“ auf deutschen Tastaturen
          zoomOut();
          break;
        case "Digit0":
        case "Numpad0":
          reset();
          break;
        default:
          return;
      }
      event.preventDefault();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return null;
}
