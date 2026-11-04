import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { open, ask } from "@tauri-apps/plugin-dialog";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import {
  BUILTIN_PREFIX,
  adoptGalleryCape,
  builtinCapeImage,
  deleteCape,
  downloadCapeTemplate,
  getCapeGallery,
  getCapeOverview,
  getCapeReviews,
  getGalleryCapeImage,
  importCape,
  renameCape,
  selectCape,
  type SalamiCape,
  type SalamiCapeOverview,
  type SalamiCapeReview,
  type SalamiGalleryCape,
} from "../../services/salami-cape-service";
import { Cape3D } from "./Cape3D";
import { CapeFace } from "./CapeFace";
import { CapeEditor, type CapeEditorTarget } from "./CapeEditor";
import "./i18n";

const errorText = (error: unknown) => (error instanceof Error ? error.message : String(error));

type ReviewState = SalamiCapeReview["status"] | "new";

const REVIEW_STYLE: Record<ReviewState, { icon: string; className: string }> = {
  approved: { icon: "solar:check-circle-bold", className: "bg-emerald-500/20 text-emerald-200" },
  pending: { icon: "solar:hourglass-bold", className: "bg-amber-500/20 text-amber-200" },
  rejected: { icon: "solar:close-circle-bold", className: "bg-red-500/25 text-red-200" },
  new: { icon: "solar:eye-closed-bold", className: "bg-white/10 text-white/70" },
};

/** Vorschau-Auswahl für Galerie-Capes, die noch nicht bei den eigenen liegen. */
const GALLERY_PREFIX = "gallery:";
/** So viele Galerie-Capes auf einmal – die Bilder kommen einzeln vom Cape-Server. */
const GALLERY_PAGE = 24;

interface CardOptions {
  extra?: React.ReactNode;
  /** Statt die Auswahl zu setzen (z. B. Galerie-Cape erst holen). */
  onPick?: () => void;
  /** Icon, solange kein Bild da ist. */
  placeholder?: string;
}

/** Cape-Seite: Salami-Capes, eigene Capes, Capes anderer Spieler, Vorlage, Import und Editor. */
export function SalamiCapesTab() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor);
  const [overview, setOverview] = useState<SalamiCapeOverview | null>(null);
  const [hovered, setHovered] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [editor, setEditor] = useState<CapeEditorTarget | null>(null);
  /** Prüfstatus vom Cape-Server; `null` = unbekannt (Server aus oder nicht erreichbar). */
  const [reviews, setReviews] = useState<Record<string, SalamiCapeReview> | null>(null);
  /** Freigegebene Capes anderer Spieler; `null` = lädt, `"error"` = Cape-Server nicht erreichbar. */
  const [gallery, setGallery] = useState<SalamiGalleryCape[] | "error" | null>(null);
  /** Bilder der Galerie (Data-URL), `false` = konnte nicht geladen werden. */
  const [galleryImages, setGalleryImages] = useState<Record<string, string | false>>({});
  const [galleryShown, setGalleryShown] = useState(GALLERY_PAGE);
  const [adopting, setAdopting] = useState<string | null>(null);
  const requestedImages = useRef(new Set<string>());

  const reload = useCallback(async () => {
    try {
      const next = await getCapeOverview();
      setOverview(next);
      const ids = next.capes.map((c) => c.share_id).filter(Boolean);
      if (ids.length === 0) return;
      try {
        setReviews(await getCapeReviews(ids));
      } catch {
        setReviews(null);
      }
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  }, [t]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const loadGallery = useCallback(async () => {
    setGallery(null);
    try {
      const capes = await getCapeGallery();
      setGallery(Array.isArray(capes) ? capes : []);
    } catch {
      setGallery("error");
    }
  }, []);

  useEffect(() => {
    void loadGallery();
  }, [loadGallery]);

  /** Eigene Capes nach Bild-ID: ein Galerie-Cape mit denselben Pixeln liegt schon bei dir. */
  const ownByShareId = new Map((overview?.capes ?? []).map((c) => [c.share_id, c] as const));
  const visibleGallery = Array.isArray(gallery) ? gallery.slice(0, galleryShown) : [];
  const missingImages = visibleGallery
    .filter((c) => !ownByShareId.has(c.id))
    .map((c) => c.id)
    .join(",");

  // Bilder der sichtbaren Galerie-Capes nacheinander laden (der Launcher speichert sie zwischen)
  useEffect(() => {
    if (!missingImages) return;
    void (async () => {
      for (const id of missingImages.split(",")) {
        if (requestedImages.current.has(id)) continue;
        requestedImages.current.add(id);
        let image: string | false;
        try {
          image = await getGalleryCapeImage(id);
        } catch {
          image = false;
        }
        setGalleryImages((prev) => ({ ...prev, [id]: image }));
      }
    })();
  }, [missingImages]);

  const imageOf = (selection: string | null): string | null => {
    if (!selection || !overview) return null;
    if (selection.startsWith(BUILTIN_PREFIX)) return builtinCapeImage(selection.slice(BUILTIN_PREFIX.length));
    if (selection.startsWith(GALLERY_PREFIX)) return galleryImages[selection.slice(GALLERY_PREFIX.length)] || null;
    return overview.capes.find((c) => c.id === selection)?.image ?? null;
  };

  const nameOf = (selection: string | null): string => {
    if (!selection) return t("salami.capes.none");
    if (selection.startsWith(BUILTIN_PREFIX)) return t(`salami.capes.${selection.slice(BUILTIN_PREFIX.length)}`);
    if (selection.startsWith(GALLERY_PREFIX)) return t("salami.capes.gallery.cape");
    return overview?.capes.find((c) => c.id === selection)?.name ?? "";
  };

  const choose = async (selection: string | null) => {
    try {
      await selectCape(selection);
      setOverview((o) => (o ? { ...o, selected: selection } : o));
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  };

  const handleTemplate = async () => {
    try {
      const path = await downloadCapeTemplate();
      toast.success(t("salami.capes.template_saved", { path }));
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  };

  const handleImport = async () => {
    const picked = await open({ multiple: false, filters: [{ name: "PNG", extensions: ["png"] }] });
    if (typeof picked !== "string") return;
    const fileName = picked.split(/[\\/]/).pop() ?? "Cape";
    const name = fileName.replace(/\.png$/i, "");
    try {
      const cape = await importCape(picked, name);
      toast.success(t("salami.capes.imported", { name: cape.name }));
      // erst auswählen, dann neu laden – das Neuladen reicht das Cape zur Prüfung ein
      await choose(cape.id);
      await reload();
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  };

  /** Galerie-Cape tragen: erst zu den eigenen Capes holen (falls nötig), dann auswählen. */
  const handleAdopt = async (cape: SalamiGalleryCape) => {
    const own = ownByShareId.get(cape.id);
    if (own) return choose(own.id);
    if (adopting) return;
    setAdopting(cape.id);
    try {
      const adopted = await adoptGalleryCape(cape.id, t("salami.capes.gallery.default_name", { id: cape.id.slice(0, 4) }));
      toast.success(t("salami.capes.gallery.adopted", { name: adopted.name }));
      // Die Karte steht jetzt für das eigene Cape – Vorschau links mitziehen
      setHovered((h) => (h === `${GALLERY_PREFIX}${cape.id}` ? adopted.id : h));
      await choose(adopted.id);
      await reload();
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    } finally {
      setAdopting(null);
    }
  };

  const handleDelete = async (cape: SalamiCape) => {
    const confirmed = await ask(t("salami.capes.delete_confirm", { name: cape.name }), { kind: "warning" });
    if (!confirmed) return;
    try {
      await deleteCape(cape.id);
      await reload();
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  };

  const handleRename = async () => {
    if (!renaming) return;
    try {
      await renameCape(renaming.id, renaming.name);
      setRenaming(null);
      await reload();
    } catch (error) {
      toast.error(t("salami.capes.error", { error: errorText(error) }));
    }
  };

  const selected = overview?.selected ?? null;
  const previewSelection = hovered ?? selected;
  const previewImage = imageOf(previewSelection);
  const previewCape = overview?.capes.find((c) => c.id === previewSelection) ?? null;

  const reviewOf = (cape: SalamiCape): { state: ReviewState; reason?: string } | null => {
    if (!reviews) return null;
    const review = reviews[cape.share_id];
    return review ? { state: review.status, reason: review.reason } : { state: "new" };
  };

  const reviewHint = (review: { state: ReviewState; reason?: string }) =>
    review.state === "rejected" && review.reason
      ? t("salami.capes.review.rejected_reason", { reason: review.reason })
      : t(`salami.capes.review.${review.state}_hint`);

  const reviewBadge = (cape: SalamiCape) => {
    const review = reviewOf(cape);
    if (!review) return null;
    const style = REVIEW_STYLE[review.state];
    return (
      <span
        title={reviewHint(review)}
        className={cn("flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold", style.className)}
      >
        <Icon icon={style.icon} className="h-3 w-3" />
        {t(`salami.capes.review.${review.state}`)}
      </span>
    );
  };

  const card = (
    key: string,
    selection: string | null,
    image: string | null,
    label: string,
    { extra, onPick, placeholder = "solar:forbidden-circle-bold" }: CardOptions = {},
  ) => {
    const isSelected = selected === selection;
    return (
      <div
        key={key}
        onMouseEnter={() => setHovered(selection)}
        onMouseLeave={() => setHovered(null)}
        className={cn(
          "group relative flex flex-col items-center gap-2 rounded-2xl border p-3 transition-colors",
          isSelected ? "bg-white/10" : "border-white/10 bg-black/25 hover:bg-white/5",
        )}
        style={isSelected ? { borderColor: accent.value, boxShadow: `0 0 0 1px ${accent.value}` } : undefined}
      >
        <button
          type="button"
          onClick={onPick ?? (() => void choose(selection))}
          className="flex flex-col items-center gap-2 focus:outline-none"
          aria-pressed={isSelected}
        >
          <div className="flex h-[112px] w-[70px] items-center justify-center rounded-lg bg-black/30">
            {image ? (
              <CapeFace src={image} width={70} />
            ) : (
              <Icon icon={placeholder} className="h-8 w-8 text-white/40" />
            )}
          </div>
          <span className="max-w-[120px] truncate text-sm text-white">{label}</span>
        </button>
        {isSelected && (
          <span
            className="absolute right-2 top-2 rounded-full px-2 py-0.5 text-[10px] font-semibold text-white"
            style={{ backgroundColor: accent.value }}
          >
            {t("salami.capes.selected")}
          </span>
        )}
        {extra}
      </div>
    );
  };

  const ownCapeActions = (cape: SalamiCape) =>
    renaming?.id === cape.id ? (
      <form
        className="flex w-full gap-1"
        onSubmit={(e) => {
          e.preventDefault();
          void handleRename();
        }}
      >
        <input
          autoFocus
          value={renaming.name}
          maxLength={40}
          onChange={(e) => setRenaming({ id: cape.id, name: e.target.value })}
          onBlur={() => void handleRename()}
          className="w-full rounded-md bg-black/40 px-2 py-1 text-xs text-white outline-none"
          aria-label={t("salami.capes.name_prompt")}
        />
      </form>
    ) : (
      <div className="flex gap-1 opacity-70 transition-opacity group-hover:opacity-100">
        {[
          { icon: "solar:pen-bold", label: t("salami.capes.edit"), onClick: () => setEditor({ cape }) },
          { icon: "solar:text-field-bold", label: t("salami.capes.rename"), onClick: () => setRenaming({ id: cape.id, name: cape.name }) },
          { icon: "solar:trash-bin-trash-bold", label: t("salami.capes.delete"), onClick: () => void handleDelete(cape) },
        ].map((action) => (
          <button
            key={action.icon}
            type="button"
            title={action.label}
            aria-label={action.label}
            onClick={action.onClick}
            className="rounded-md p-1.5 text-white/80 hover:bg-white/10 hover:text-white"
          >
            <Icon icon={action.icon} className="h-4 w-4" />
          </button>
        ))}
      </div>
    );

  const actionButton = (icon: string, label: string, onClick: () => void, primary = false) => (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold text-white transition-colors",
        primary ? "hover:brightness-110" : "bg-white/10 hover:bg-white/15",
      )}
      style={primary ? { backgroundColor: accent.value } : undefined}
    >
      <Icon icon={icon} className="h-5 w-5" />
      {label}
    </button>
  );

  return (
    <div className="flex h-full gap-6 overflow-hidden p-6">
      <aside className="flex w-[280px] shrink-0 flex-col gap-3">
        <div className="relative h-[340px] overflow-hidden rounded-2xl border border-white/10 bg-black/30">
          {previewImage ? (
            <Cape3D src={previewImage} className="h-full w-full" />
          ) : (
            <div className="flex h-full items-center justify-center text-white/40">
              <Icon icon="solar:forbidden-circle-bold" className="h-12 w-12" />
            </div>
          )}
        </div>
        <div className="px-1">
          <div className="text-lg font-semibold text-white">{nameOf(previewSelection)}</div>
          {previewSelection?.startsWith(GALLERY_PREFIX) && (
            <p className="mt-2 text-xs leading-relaxed text-white/70">{t("salami.capes.gallery.preview_hint")}</p>
          )}
          {previewCape && reviewOf(previewCape) && (
            <div className="mt-2 flex flex-col items-start gap-1">
              {reviewBadge(previewCape)}
              <p className="text-xs leading-relaxed text-white/70">{reviewHint(reviewOf(previewCape)!)}</p>
            </div>
          )}
          <p className="mt-2 text-xs leading-relaxed text-white/55">{t("salami.capes.visibility")}</p>
        </div>
      </aside>

      <section className="flex-1 overflow-y-auto pr-2 custom-scrollbar">
        <p className="text-sm text-white/70">{t("salami.capes.subtitle")}</p>
        <div className="mt-4 flex flex-wrap gap-2">
          {actionButton("solar:pen-new-square-bold", t("salami.capes.create"), () => setEditor({}), true)}
          {actionButton("solar:gallery-add-bold", t("salami.capes.import"), () => void handleImport())}
          {actionButton("solar:download-minimalistic-bold", t("salami.capes.template"), () => void handleTemplate())}
        </div>

        <h3 className="mb-3 mt-7 text-base font-semibold text-white">{t("salami.capes.builtin")}</h3>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
          {(overview?.builtin ?? []).map((name) =>
            card(name, `${BUILTIN_PREFIX}${name}`, builtinCapeImage(name), t(`salami.capes.${name}`)),
          )}
          {card("none", null, null, t("salami.capes.none"))}
        </div>

        <h3 className="mb-3 mt-7 text-base font-semibold text-white">{t("salami.capes.own")}</h3>
        {overview && overview.capes.length > 0 ? (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
            {overview.capes.map((cape) =>
              card(cape.id, cape.id, cape.image, cape.name, {
                extra: (
                  <>
                    {reviewBadge(cape)}
                    {ownCapeActions(cape)}
                  </>
                ),
              }),
            )}
          </div>
        ) : (
          <div className="rounded-2xl border border-dashed border-white/15 bg-black/20 p-5 text-sm text-white/75">
            <div className="mb-2 font-semibold text-white">{t("salami.capes.empty_title")}</div>
            <ol className="list-decimal space-y-1 pl-5">
              <li>{t("salami.capes.empty_step1")}</li>
              <li>{t("salami.capes.empty_step2")}</li>
              <li>{t("salami.capes.empty_step3")}</li>
            </ol>
          </div>
        )}

        <h3 className="mt-7 text-base font-semibold text-white">{t("salami.capes.gallery.title")}</h3>
        <p className="mb-3 mt-1 text-xs leading-relaxed text-white/55">{t("salami.capes.gallery.subtitle")}</p>
        {gallery === null ? (
          <div className="flex items-center gap-2 text-sm text-white/60">
            <Icon icon="svg-spinners:ring-resize" className="h-4 w-4" />
            {t("salami.capes.gallery.loading")}
          </div>
        ) : gallery === "error" ? (
          <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-dashed border-white/15 bg-black/20 p-4 text-sm text-white/75">
            <span>{t("salami.capes.gallery.error")}</span>
            {actionButton("solar:refresh-bold", t("salami.capes.gallery.retry"), () => void loadGallery())}
          </div>
        ) : gallery.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/15 bg-black/20 p-4 text-sm text-white/75">
            {t("salami.capes.gallery.empty")}
          </div>
        ) : (
          <>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
              {visibleGallery.map((entry) => {
                const own = ownByShareId.get(entry.id);
                const image = own?.image ?? galleryImages[entry.id];
                return card(
                  `gallery-${entry.id}`,
                  own ? own.id : `${GALLERY_PREFIX}${entry.id}`,
                  image || null,
                  own?.name ?? t("salami.capes.gallery.cape"),
                  {
                    onPick: () => void handleAdopt(entry),
                    placeholder: image === false ? "solar:forbidden-circle-bold" : "svg-spinners:ring-resize",
                    extra: (
                      <div className="flex flex-wrap items-center justify-center gap-1">
                        {adopting === entry.id && <Icon icon="svg-spinners:ring-resize" className="h-3.5 w-3.5 text-white/70" />}
                        {own && (
                          <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-semibold text-white/80">
                            {t("salami.capes.gallery.owned")}
                          </span>
                        )}
                        {entry.wearers > 0 && (
                          <span
                            title={t("salami.capes.gallery.wearers", { count: entry.wearers })}
                            className="flex items-center gap-1 rounded-full bg-white/5 px-2 py-0.5 text-[10px] text-white/70"
                          >
                            <Icon icon="solar:users-group-rounded-bold" className="h-3 w-3" />
                            {entry.wearers}
                          </span>
                        )}
                      </div>
                    ),
                  },
                );
              })}
            </div>
            {gallery.length > galleryShown && (
              <div className="mt-3">
                {actionButton(
                  "solar:alt-arrow-down-bold",
                  t("salami.capes.gallery.more", { count: gallery.length - galleryShown }),
                  () => setGalleryShown((n) => n + GALLERY_PAGE),
                )}
              </div>
            )}
          </>
        )}
      </section>

      {editor && (
        <CapeEditor
          target={editor}
          onClose={() => setEditor(null)}
          onSaved={async (cape) => {
            setEditor(null);
            toast.success(t("salami.capes.saved", { name: cape.name }));
            await choose(cape.id);
            await reload();
          }}
        />
      )}
    </div>
  );
}
