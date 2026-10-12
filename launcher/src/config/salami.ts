/**
 * Zentrale Branding- und Feature-Schalter des Salami Client.
 *
 * Gegenstück zu `src-tauri/src/branding.rs` – beide Dateien müssen bei
 * `NORISK_SERVICES_ENABLED` übereinstimmen.
 */

export const BRAND = {
  name: "Salami Client",
  shortName: "Salami",
  /** 512px-Logo (generiert von branding/generate_logo.py). */
  logo: "/logo.png",
  logoSmall: "/salami-logo-64.png",
} as const;

/**
 * NoRisk-Online-Dienste (Account-Token, Capes, Cosmetics, Friends, AGB-Dialog,
 * Analytics, Feature-Flags). Im Salami Client aus – das Backend blockiert
 * Requests an *.norisk.gg ohnehin, hier verschwinden die passenden UI-Teile.
 */
export const NORISK_SERVICES_ENABLED = false;

export const FEATURES = {
  /** Cape-Shop (NoRisk-Account nötig). */
  capes: NORISK_SERVICES_ENABLED,
  /** Freunde/Chat-Sidebar (NoRisk-WebSocket). */
  friends: NORISK_SERVICES_ENABLED,
  /** NoRisk-AGB-Zustimmungsdialog beim ersten Start. */
  termsOfService: NORISK_SERVICES_ENABLED,
  /** NoRisk-Analytics-Banner. */
  analyticsBanner: NORISK_SERVICES_ENABLED,
  /** Remote-Feature-Flags über NoRisks Flagsmith. */
  flagsmith: NORISK_SERVICES_ENABLED,
} as const;
