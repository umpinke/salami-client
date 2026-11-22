import { useTranslation } from "react-i18next";
import { toast } from "react-hot-toast";
import { Icon } from "@iconify/react";
import { KanteButton } from "../../components/modrinth/v2/FeinkostParts";
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

  const label = t(inCart ? "salami.downloader.cart.in_cart" : "salami.downloader.cart.add");
  return (
    <KanteButton
      iconOnly
      active={inCart}
      title={label}
      aria-label={label}
      icon={<Icon icon={inCart ? "solar:cart-check-bold" : "solar:cart-plus-bold"} className="h-4 w-4" />}
      onClick={(e) => {
        e.stopPropagation();
        if (toggle(cartItemFrom(project))) {
          toast.success(t("salami.downloader.cart.added", { name: project.title }));
        }
      }}
    />
  );
}
