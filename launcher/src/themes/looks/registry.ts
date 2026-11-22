import { useThemeStore } from "../../store/useThemeStore";
import { look as salumeria } from "./salumeria";
import { look as theke } from "./theke";
import { DEFAULT_LOOK, type LookDefinition, type LookId } from "./types";
import { look as werkbank } from "./werkbank";

export const LOOKS: Record<LookId, LookDefinition> = { salumeria, werkbank, theke };

/** Der gewählte Look – oder der Standard, solange der Spieler noch nicht gewählt hat. */
export function useLook(): LookDefinition {
  const chosen = useThemeStore((state) => state.look);
  return LOOKS[chosen ?? DEFAULT_LOOK] ?? LOOKS[DEFAULT_LOOK];
}
