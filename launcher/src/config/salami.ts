/**
 * Zentrales Branding des Salami Client.
 *
 * Gegenstück zu `src-tauri/src/branding.rs`.
 */

export const BRAND = {
  name: "Salami Client",
  shortName: "Salami",
  /** 512px-Logo (generiert von branding/generate_logo.py). */
  logo: "/logo.png",
  logoSmall: "/salami-logo-64.png",
  /** Quellcode (GPL-3.0), verlinkt im Info-Bereich der Einstellungen. */
  sourceUrl: "https://github.com/umpinke/salami-client",
} as const;
