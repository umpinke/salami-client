import { invoke } from "@tauri-apps/api/core";

/** Eigenes Cape, Bild als Data-URL (siehe `integrations/salami_capes.rs`). */
export interface SalamiCape {
  id: string;
  name: string;
  created: string;
  image: string;
  /** ID des Bildes auf dem Cape-Server (Hash über die Pixel), für den Prüfstatus. */
  share_id: string;
}

/** Prüfstatus auf dem Cape-Server. Fehlt ein Cape, wurde es noch nicht eingereicht. */
export interface SalamiCapeReview {
  status: "pending" | "approved" | "rejected";
  reason?: string;
}

export interface SalamiCapeOverview {
  /** `builtin:<name>`, ID eines eigenen Capes oder `null` = kein Cape. */
  selected: string | null;
  builtin: string[];
  capes: SalamiCape[];
}

export const BUILTIN_PREFIX = "builtin:";

/** Vorschaubild eines mitgelieferten Capes (`public/capes`). */
export const builtinCapeImage = (name: string): string => `/capes/${name}.png`;

export const getCapeOverview = () => invoke<SalamiCapeOverview>("salami_capes_overview");

export const importCape = (path: string, name: string) =>
  invoke<SalamiCape>("salami_capes_import", { path, name });

export const saveCape = (id: string | null, name: string, pngBase64: string) =>
  invoke<SalamiCape>("salami_capes_save", { id, name, pngBase64 });

export const renameCape = (id: string, name: string) => invoke<void>("salami_capes_rename", { id, name });

export const deleteCape = (id: string) => invoke<void>("salami_capes_delete", { id });

export const selectCape = (selection: string | null) => invoke<void>("salami_capes_select", { selection });

/** Prüfstatus eigener Capes, Schlüssel ist `share_id`. */
export const getCapeReviews = (ids: string[]) =>
  invoke<Record<string, SalamiCapeReview>>("salami_capes_review_status", { ids });

/** Speichert die Vorlage im Download-Ordner und liefert den Pfad. */
export const downloadCapeTemplate = () => invoke<string>("salami_capes_download_template");
