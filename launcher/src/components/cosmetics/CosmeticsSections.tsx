import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { cn } from "../../lib/utils";
import { useActiveSkinTexture } from "../../hooks/useActiveSkinTexture";
import { useThemeStore } from "../../store/useThemeStore";
import { CosmeticsViewer, type CosmeticsViewerProps } from "../../lib/skin3d/CosmeticsViewer";
import { useCosmeticSnapshot, type CosmeticSnapshotRequest } from "../../lib/skin3d/cosmetic-snapshot";
import { MINI_ME_PLACES, type MiniMePlace } from "../../lib/skin3d/cosmetics";
import {
  cosmeticName,
  getEmoteCatalog,
  getHatCatalog,
  type CatalogEmote,
  type CatalogHat,
  type CosmeticsCatalog,
} from "../../services/salami-cosmetics-service";
import "../salami-capes/i18n";

/** Vorschaubilder werden doppelt so groß gerendert, wie sie auf der Karte stehen. */
const THUMB_PIXELS = 192;

/** Heller Fleck hinter der Figur: so heben sich auch schwarze Hüte vom dunklen Launcher ab. */
const SPOTLIGHT = {
  background: "radial-gradient(ellipse at 50% 35%, rgba(255,255,255,0.22), rgba(255,255,255,0.05) 90%), rgba(0,0,0,0.3)",
} as const;

/** Katalog laden; `null`, solange er unterwegs ist. Ohne Server kommt die Liste aus dem Launcher. */
function useCatalog<T>(load: () => Promise<CosmeticsCatalog<T>>): CosmeticsCatalog<T> | null {
  const [catalog, setCatalog] = useState<CosmeticsCatalog<T> | null>(null);
  useEffect(() => {
    let cancelled = false;
    load()
      // kann nur scheitern, wenn auch die Liste aus dem Launcher unlesbar ist
      .catch((error: unknown): CosmeticsCatalog<T> => {
        console.warn("[cosmetics] Katalog nicht lesbar:", error);
        return { items: [], live: false };
      })
      .then((loaded) => {
        if (!cancelled) setCatalog(loaded);
      });
    return () => {
      cancelled = true;
    };
  }, [load]);
  return catalog;
}

/** Eintrag, den die große Vorschau zeigt: der unter der Maus, sonst der zuletzt angeklickte. */
function useShown<T>(items: T[] | undefined) {
  const [hovered, setHovered] = useState<T | null>(null);
  const [picked, setPicked] = useState<T | null>(null);
  const shown = hovered ?? picked ?? items?.[0] ?? null;
  return { shown, picked: picked ?? items?.[0] ?? null, setHovered, setPicked };
}

interface CardProps {
  label: string;
  /** Kleine Zeile unter dem Namen. */
  note?: string;
  /** Diese Karte ist für die große Vorschau angeklickt. */
  active: boolean;
  icon: string;
  snapshot: CosmeticSnapshotRequest | null;
  onHover: (inside: boolean) => void;
  onPick: () => void;
}

function Card({ label, note, active, icon, snapshot, onHover, onPick }: CardProps) {
  const accent = useThemeStore((s) => s.accentColor);
  const image = useCosmeticSnapshot(snapshot);
  return (
    <button
      type="button"
      onClick={onPick}
      onMouseEnter={() => onHover(true)}
      onMouseLeave={() => onHover(false)}
      onFocus={() => onHover(true)}
      onBlur={() => onHover(false)}
      aria-pressed={active}
      className={cn(
        "flex flex-col items-center gap-2 rounded-2xl border p-3 text-center transition-colors focus:outline-none",
        active ? "bg-white/10" : "border-white/10 bg-black/25 hover:bg-white/5",
      )}
      style={active ? { borderColor: accent.value, boxShadow: `0 0 0 1px ${accent.value}` } : undefined}
    >
      <div className="flex h-24 w-24 items-center justify-center rounded-lg" style={SPOTLIGHT}>
        {image ? (
          <img src={image} alt="" className="h-24 w-24" draggable={false} />
        ) : (
          <Icon icon={icon} className="h-8 w-8 text-white/40" />
        )}
      </div>
      <span className="max-w-[124px] truncate text-sm text-white">{label}</span>
      {note && <span className="-mt-1 max-w-[124px] truncate text-xs text-white/50">{note}</span>}
    </button>
  );
}

interface LayoutProps {
  viewer: Pick<CosmeticsViewerProps, "hat" | "emote" | "miniMe">;
  /** Name und Hinweise zum gezeigten Eintrag. */
  title: string;
  details?: ReactNode;
  subtitle: string;
  /** Liste aus dem Launcher statt vom Server. */
  offline?: boolean;
  loading?: boolean;
  icon: string;
  children: ReactNode;
}

/** Links die Figur mit dem gezeigten Cosmetic, rechts alle zur Auswahl – wie auf der Cape-Seite. */
function Layout({ viewer, title, details, subtitle, offline = false, loading = false, icon, children }: LayoutProps) {
  const { t } = useTranslation();
  const skin = useActiveSkinTexture();
  return (
    <div className="flex h-full gap-6 overflow-hidden p-6">
      <aside className="flex w-[280px] shrink-0 flex-col gap-3">
        <div className="relative h-[340px] overflow-hidden rounded-2xl border border-white/10" style={SPOTLIGHT}>
          <CosmeticsViewer
            textureUrl={skin.textureUrl}
            variant={skin.variant}
            className="h-full w-full"
            unsupportedFallback={
              <div className="flex h-full items-center justify-center text-white/40">
                <Icon icon={icon} className="h-12 w-12" />
              </div>
            }
            {...viewer}
          />
        </div>
        <div className="px-1">
          <div className="text-lg font-semibold text-white">{title}</div>
          {details}
          <p className="mt-2 text-xs leading-relaxed text-white/55">{t("salami.cosmetics.preview_hint")}</p>
        </div>
      </aside>

      <section className="flex-1 overflow-y-auto pr-2 custom-scrollbar">
        <p className="text-sm text-white/70">{subtitle}</p>
        {offline && (
          <p className="mt-3 flex items-start gap-2 rounded-xl border border-white/10 bg-black/20 px-3 py-2 text-xs leading-relaxed text-white/65">
            <Icon icon="solar:cloud-cross-bold" className="mt-0.5 h-4 w-4 shrink-0" />
            {t("salami.cosmetics.offline")}
          </p>
        )}
        {loading ? (
          <p className="mt-6 text-sm text-white/55">{t("salami.cosmetics.loading")}</p>
        ) : (
          <div className="mt-5 grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">{children}</div>
        )}
      </section>
    </div>
  );
}

const line = (text: string) => <p className="mt-2 text-xs leading-relaxed text-white/70">{text}</p>;

/** Skin für die Vorschaubilder; `null`, solange er lädt (sonst würde erst Steve gerendert, dann alles noch einmal). */
function useSnapshotSkin(): Pick<CosmeticSnapshotRequest, "textureUrl" | "variant" | "size"> | null {
  const skin = useActiveSkinTexture();
  if (skin.loading && !skin.textureUrl) return null;
  return { textureUrl: skin.textureUrl, variant: skin.variant, size: THUMB_PIXELS };
}

/** Shop → Hüte: alle Hüte aus dem Hut-Katalog, an der eigenen Figur. */
export function HatsSection() {
  const { t, i18n } = useTranslation();
  const catalog = useCatalog<CatalogHat>(getHatCatalog);
  const { shown, picked, setHovered, setPicked } = useShown(catalog?.items);
  const skin = useSnapshotSkin();
  const name = (hat: CatalogHat) => cosmeticName(hat, i18n.language);
  return (
    <Layout
      viewer={{ hat: shown }}
      title={shown ? name(shown) : ""}
      details={
        shown && (
          <>
            {shown.anchor === "shoulder" && line(t("salami.cosmetics.hats.on_shoulder"))}
            {shown.glow && line(t("salami.cosmetics.hats.glows"))}
          </>
        )
      }
      subtitle={t("salami.cosmetics.hats.subtitle")}
      offline={catalog?.live === false}
      loading={!catalog}
      icon="solar:crown-bold"
    >
      {catalog?.items.map((hat) => (
        <Card
          key={hat.id}
          label={name(hat)}
          active={picked === hat}
          icon="solar:crown-bold"
          snapshot={skin && { ...skin, hat }}
          onHover={(inside) => setHovered(inside ? hat : null)}
          onPick={() => setPicked(hat)}
        />
      ))}
    </Layout>
  );
}

/** Shop → Emotes: alle Emotes aus dem Emote-Katalog, die Figur spielt das gezeigte ab. */
export function EmotesSection() {
  const { t, i18n } = useTranslation();
  const catalog = useCatalog<CatalogEmote>(getEmoteCatalog);
  const { shown, picked, setHovered, setPicked } = useShown(catalog?.items);
  const skin = useSnapshotSkin();
  const name = (emote: CatalogEmote) => cosmeticName(emote, i18n.language);
  const length = (emote: CatalogEmote) =>
    emote.loop
      ? t("salami.cosmetics.emotes.endless")
      : t("salami.cosmetics.emotes.seconds", {
          seconds: new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(emote.duration),
        });
  return (
    <Layout
      viewer={{ emote: shown }}
      title={shown ? name(shown) : ""}
      details={
        shown && (
          <>
            {line(shown.loop ? t("salami.cosmetics.emotes.endless_hint") : length(shown))}
            <p className="mt-2 text-xs leading-relaxed text-white/70">
              {t("salami.cosmetics.emotes.command")}{" "}
              <code className="rounded bg-black/40 px-1.5 py-0.5 text-white/85">/salami emote {shown.id}</code>
            </p>
          </>
        )
      }
      subtitle={t("salami.cosmetics.emotes.subtitle")}
      offline={catalog?.live === false}
      loading={!catalog}
      icon="solar:hand-shake-bold"
    >
      {catalog?.items.map((emote) => (
        <Card
          key={emote.id}
          label={name(emote)}
          note={length(emote)}
          active={picked === emote}
          icon="solar:hand-shake-bold"
          snapshot={skin && { ...skin, emote }}
          onHover={(inside) => setHovered(inside ? emote : null)}
          onPick={() => setPicked(emote)}
        />
      ))}
    </Layout>
  );
}

/** Shop → Mini Me: die vier Plätze, an denen das kleine Abbild sein kann. */
export function MiniMeSection() {
  const { t } = useTranslation();
  const { shown, picked, setHovered, setPicked } = useShown<MiniMePlace>([...MINI_ME_PLACES]);
  const skin = useSnapshotSkin();
  const place = shown ?? "shoulder";
  return (
    <Layout
      viewer={{ miniMe: place }}
      title={t(`salami.cosmetics.minime.place.${place}`)}
      details={line(t(`salami.cosmetics.minime.place.${place}_hint`))}
      subtitle={t("salami.cosmetics.minime.subtitle")}
      icon="solar:users-group-rounded-bold"
    >
      {MINI_ME_PLACES.map((item) => (
        <Card
          key={item}
          label={t(`salami.cosmetics.minime.place.${item}`)}
          active={picked === item}
          icon="solar:users-group-rounded-bold"
          snapshot={skin && { ...skin, miniMe: item }}
          onHover={(inside) => setHovered(inside ? item : null)}
          onPick={() => setPicked(item)}
        />
      ))}
    </Layout>
  );
}
