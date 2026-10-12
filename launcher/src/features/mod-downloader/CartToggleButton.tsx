import { useTranslation } from "react-i18next";
import { toast } from "react-hot-toast";
import { ActionButton } from "../../components/ui/ActionButton";
import type { UnifiedModSearchResult } from "../../types/unified";
import { cartItemFrom, cartKeyOf, useInstallCartStore } from "./cart-store";
import "./i18n";

/** Kleiner Knopf auf Projektkarten: Projekt in den Mod-Warenkorb legen / herausnehmen. */
export function CartToggleButton({ project }: { project: UnifiedModSearchResult }) {
  const { t } = useTranslation();
  const key = cartKeyOf(project.source, project.project_id);
  const inCart = useInstallCartStore((s) => s.items.some((i) => i.key === key));
  const toggle = useInstallCartStore((s) => s.toggle);

  if (project.project_type === "modpack") return null;

  return (
    <ActionButton
      icon={inCart ? "solar:cart-check-bold" : "solar:cart-plus-bold"}
      variant="icon-only"
      tooltip={t(inCart ? "salami.downloader.cart.in_cart" : "salami.downloader.cart.add")}
      onClick={(e) => {
        e.stopPropagation();
        if (toggle(cartItemFrom(project))) {
          toast.success(t("salami.downloader.cart.added", { name: project.title }));
        }
      }}
    />
  );
}
