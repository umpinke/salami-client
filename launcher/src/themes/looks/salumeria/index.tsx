import type { LookDefinition } from "../types";
import { SalumeriaBackdrop } from "./SalumeriaBackdrop";
import { SalumeriaContentItem } from "./SalumeriaContentItem";
import { SalumeriaNav } from "./SalumeriaNav";
import { SalumeriaPlayView } from "./SalumeriaPlayView";
import { SalumeriaProfileDetail } from "./SalumeriaProfileDetail";
import { SalumeriaSearchCard } from "./SalumeriaSearchCard";
import "./texts";
import "./look.css";
import "./inner.css";
import "./logs.css";
import "./dialogs.css";

/**
 * Look „Salumeria": elegantes Feinkost-Etikett wie das Logo – fast schwarzer Grund, Creme,
 * feine Serife, dünne Linien; Rot nur für die Hauptaktion. Profile als Etikett mit cremefarbener
 * „Carta“, der Mod-Browser als gedruckter Katalog mit Register.
 */
export const look: LookDefinition = {
  id: "salumeria",
  navPlacement: "top",
  Nav: SalumeriaNav,
  PlayView: SalumeriaPlayView,
  Backdrop: SalumeriaBackdrop,
  ownsBackground: true,
  ProfileDetail: SalumeriaProfileDetail,
  ContentItem: SalumeriaContentItem,
  contentListClassName: "sal-carta-list grid grid-cols-1 lg:grid-cols-2 gap-x-12",
  SearchCard: SalumeriaSearchCard,
  searchGridClassName:
    "sal-catalog-grid grid grid-cols-[repeat(auto-fill,minmax(236px,1fr))] gap-x-8 gap-y-7 pb-10 pl-1 pr-3 pt-1",
};
