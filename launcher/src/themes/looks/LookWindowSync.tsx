import { useEffect } from "react";
import { useFontStore } from "../../store/font-store";
import { useThemeStore } from "../../store/useThemeStore";
import { LookSync } from "./LookSync";
import "./registry";

/**
 * Für Nebenfenster (Log-Fenster): denselben Look, dieselbe Farbe und Schrift wie das Hauptfenster.
 * Die Einstellungen liegen im gemeinsamen localStorage; ändert das Hauptfenster sie, zieht das
 * Nebenfenster über das `storage`-Ereignis nach.
 */
export function LookWindowSync() {
  useEffect(() => {
    useFontStore.getState().applyFontToDOM();
    useThemeStore.getState().applyAccentColorToDOM();
    useThemeStore.getState().applyBorderRadiusToDOM();
    const onStorage = (event: StorageEvent) => {
      if (event.key === "norisk-theme-storage") void useThemeStore.persist.rehydrate();
      if (event.key === "norisk-font-storage") void useFontStore.persist.rehydrate();
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);
  return <LookSync />;
}
