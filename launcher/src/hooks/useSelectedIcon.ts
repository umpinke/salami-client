import type { SelectedIcon } from "../services/cosmetic-icon-service";

const EMPTY: SelectedIcon = { url: null, plus: false };

/** Salami: Profilsymbole kamen von NoRisks Cosmetics-Server – es gibt keins. */
export function useSelectedIcon(_playerIdentifier: string | null | undefined): SelectedIcon {
  return EMPTY;
}
