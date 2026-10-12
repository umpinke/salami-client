import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "react-hot-toast";
import { Icon } from "@iconify/react";
import { Modal } from "../../components/ui/Modal";
import { Button } from "../../components/ui/buttons/Button";
import { useProfileStore } from "../../store/profile-store";
import type { Profile } from "../../types/profile";
import { installCart } from "./cart-install";
import { useInstallCartStore } from "./cart-store";
import "./i18n";

/**
 * Schwebender Warenkorb-Knopf (unten rechts im Mod-Browser) + Dialog zum
 * gesammelten Installieren in ein Profil.
 */
export function InstallCart({ profiles }: { profiles: Profile[] }) {
  const { t } = useTranslation();
  const items = useInstallCartStore((s) => s.items);
  const [open, setOpen] = useState(false);

  if (items.length === 0 && !open) return null;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="absolute bottom-6 right-6 z-20 flex items-center gap-2 rounded-lg px-4 py-3 font-minecraft text-xl lowercase text-white shadow-lg transition-transform hover:scale-105"
        style={{ background: "var(--accent)", boxShadow: "0 6px 20px var(--accent-shadow)" }}
      >
        <Icon icon="solar:cart-large-2-bold" className="w-6 h-6" />
        {t("salami.downloader.cart.open", { count: items.length })}
      </button>
      {open && <InstallCartModal profiles={profiles} onClose={() => setOpen(false)} />}
    </>
  );
}

function InstallCartModal({ profiles, onClose }: { profiles: Profile[]; onClose: () => void }) {
  const { t } = useTranslation();
  const { items, remove, clear } = useInstallCartStore();
  const fetchProfiles = useProfileStore((s) => s.fetchProfiles);
  const selectable = useMemo(() => [...profiles].sort((a, b) => a.name.localeCompare(b.name)), [profiles]);
  const [profileId, setProfileId] = useState<string>("");
  const [progress, setProgress] = useState<string | null>(null);

  useEffect(() => {
    if (!profileId && selectable.length > 0) setProfileId(selectable[0].id);
  }, [profileId, selectable]);

  const profile = selectable.find((p) => p.id === profileId) ?? null;
  const busy = progress !== null;

  const installAll = async () => {
    if (!profile) {
      toast.error(t("salami.downloader.cart.no_profile"));
      return;
    }
    const result = await installCart(items, profile, (current, total, item) =>
      setProgress(t("salami.downloader.cart.installing", { current, total, name: item.title })),
    );
    setProgress(null);
    for (const item of [...result.ok, ...result.already]) remove(item.key);
    for (const { item, error } of result.failed) console.warn(`[Salami] ${item.title}: ${error}`);
    const summary = t("salami.downloader.cart.result", {
      ok: result.ok.length,
      already: result.already.length,
      skipped: result.skipped.length,
      failed: result.failed.length,
    });
    (result.failed.length || result.skipped.length ? toast.error : toast.success)(summary);
    void fetchProfiles();
    if (result.failed.length === 0 && result.skipped.length === 0) onClose();
  };

  return (
    <Modal
      title={t("salami.downloader.cart.title")}
      titleIcon={<Icon icon="solar:cart-large-2-bold" className="w-6 h-6" />}
      onClose={() => !busy && onClose()}
      width="md"
      footer={
        <div className="flex items-center justify-between gap-3 w-full">
          <Button variant="ghost" disabled={busy || items.length === 0} onClick={clear}>
            {t("salami.downloader.cart.clear")}
          </Button>
          <Button variant="success" disabled={busy || items.length === 0 || !profile} onClick={installAll}>
            {busy ? progress : t("salami.downloader.cart.install_all", { count: items.length })}
          </Button>
        </div>
      }
    >
      <div className="space-y-4 p-1">
        <label className="block">
          <span className="font-minecraft text-xl lowercase text-white/80">{t("salami.downloader.cart.profile")}</span>
          <select
            className="mt-1 w-full rounded-md bg-black/40 border border-white/15 px-3 py-2 text-white"
            value={profileId}
            disabled={busy}
            onChange={(e) => setProfileId(e.target.value)}
          >
            {selectable.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} – {p.game_version} {p.loader}
              </option>
            ))}
          </select>
        </label>

        {items.length === 0 ? (
          <p className="text-sm text-white/60">{t("salami.downloader.cart.empty")}</p>
        ) : (
          <ul className="space-y-1 max-h-80 overflow-y-auto custom-scrollbar">
            {items.map((item) => (
              <li key={item.key} className="flex items-center gap-3 rounded-md bg-black/25 px-2 py-1.5">
                {item.iconUrl ? (
                  <img src={item.iconUrl} alt="" className="w-8 h-8 rounded" />
                ) : (
                  <Icon icon="solar:box-bold" className="w-8 h-8 text-white/40" />
                )}
                <div className="flex-1 min-w-0">
                  <div className="truncate text-white">{item.title}</div>
                  <div className="text-xs text-white/50">
                    {item.source} · {item.projectType}
                  </div>
                </div>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => remove(item.key)}
                  className="text-white/50 hover:text-red-400 disabled:opacity-40"
                  aria-label="remove"
                >
                  <Icon icon="solar:trash-bin-trash-bold" className="w-5 h-5" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  );
}
