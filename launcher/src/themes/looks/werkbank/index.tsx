import type { LookDefinition } from "../types";
import { WerkbankBackdrop } from "./Backdrop";
import { Hotbar } from "./Hotbar";
import { WerkbankPlayView } from "./PlayView";
import { WerkbankContentItem } from "./WerkbankContentItem";
import { WerkbankProfileDetail } from "./WerkbankProfileDetail";
import { WerkbankSearchCard } from "./WerkbankSearchCard";
import "./texts";
import "./look.css";
import "./inner.css";
import "./logs.css";
import "./dialogs.css";

/**
 * Look „Pixel-Werkbank“: Minecraft pur – gefaste Pixel-Rahmen wie im Inventar, die Pixel-Schrift
 * überall, Slots und unten eine Hotbar als Navigation. Die Profilseite ist ein Inventar-Fenster
 * mit Kreativ-Reitern, der Mod-Browser ein Kreativ-Inventar.
 */
export const look: LookDefinition = {
  id: "werkbank",
  navPlacement: "bottom",
  Nav: Hotbar,
  PlayView: WerkbankPlayView,
  Backdrop: WerkbankBackdrop,
  ownsBackground: true,
  ProfileDetail: WerkbankProfileDetail,
  ContentItem: WerkbankContentItem,
  contentListClassName: "grid grid-cols-1 lg:grid-cols-2 gap-x-3 gap-y-1.5",
  SearchCard: WerkbankSearchCard,
  searchGridClassName: "wb-creative grid grid-cols-[repeat(auto-fill,minmax(232px,1fr))] gap-2",
};
