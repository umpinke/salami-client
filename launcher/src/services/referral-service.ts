import type { ReferralInfo } from "../types/launcherConfig";

/**
 * Salami: Das NoRisk-Empfehlungsprogramm gibt es nicht – keine Abfrage an fremde Server.
 */
export async function getReferralInfo(_code: string): Promise<ReferralInfo> {
  throw new Error("Not available in Salami Client");
}
