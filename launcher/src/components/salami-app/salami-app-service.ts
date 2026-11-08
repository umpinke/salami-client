import { invoke } from "@tauri-apps/api/core";

/** Stand der Kopplung mit der Salami-App (siehe `integrations::salami_app::AppStatus`). */
export interface SalamiAppStatus {
  available: boolean;
  enabled: boolean;
  linked: boolean;
  accountName: string | null;
  pendingSessions: number;
  storageFull: boolean;
  downloadUrl: string;
  privacyUrl: string;
}

/** Kopplungscode: 6 Ziffern, 10 min gültig; `qrSvg` enthält genau `url`. */
export interface SalamiPairCode {
  code: string;
  expiresAt: string;
  url: string;
  qrSvg: string;
}

export interface SalamiDevice {
  id: string;
  name: string;
  pairedAt: string | null;
  lastSeen: string | null;
}

export const SalamiAppService = {
  status: () => invoke<SalamiAppStatus>("salami_app_status"),
  pairCode: () => invoke<SalamiPairCode>("salami_app_pair_code"),
  devices: () => invoke<SalamiDevice[]>("salami_app_devices"),
  removeDevice: (id: string) => invoke<void>("salami_app_remove_device", { id }),
  deleteData: () => invoke<number>("salami_app_delete_data"),
};

/** Fehlertext aus einem Tauri-Befehl (CommandError kommt als Objekt mit `message`). */
export function errorText(error: unknown): string {
  let text: string;
  if (typeof error === "string") text = error;
  else if (error && typeof error === "object" && "message" in error) text = String((error as { message: unknown }).message);
  else text = String(error);
  return text.replace(/^Other error: /, "");
}
