import { getSkinPreview } from "../lib/skin-preview";
import { useSkinStore } from "../store/useSkinStore";
import type { SkinVariant } from "../types/localSkin";
import { useAsyncResource } from "./useAsyncResource";

const DEFAULT_FALLBACK_SKIN_URL = "/skins/default_steve_full.png";

export interface SkinPreviewInput {
  textureUrl: string | null | undefined;
  variant?: SkinVariant;
  /** Namensschild über dem Kopf. */
  nametag?: string | null;
  width?: number;
  height?: number;
  dpr?: number;
  fallbackUrl?: string | null;
}

export interface SkinPreview {
  url: string;
  loading: boolean;
}

/** Vorschaubild eines Skins (siehe `lib/skin-preview.ts`), solange `enabled`. */
export function useSkinPreview(enabled: boolean, input: SkinPreviewInput): SkinPreview {
  const skinRevision = useSkinStore((state) => state.skinRevision);
  const {
    textureUrl,
    variant = "classic",
    nametag = null,
    width,
    height,
    dpr = 2,
    fallbackUrl = DEFAULT_FALLBACK_SKIN_URL,
  } = input;
  const active = enabled && !!textureUrl;

  const { data, loading } = useAsyncResource<string>(
    active
      ? () =>
          getSkinPreview(
            { textureUrl: textureUrl!, variant, nametag },
            width && height ? { width: Math.round(width * dpr), height: Math.round(height * dpr) } : undefined,
          )
      : null,
    [active, textureUrl, variant, nametag, width, height, dpr, skinRevision],
    fallbackUrl ?? "",
  );

  return { url: data, loading: active && loading };
}
