import type { LookDefinition } from "../types";
import { ThekeBackdrop } from "./ThekeBackdrop";
import { ThekeNav } from "./ThekeNav";
import { ThekeContentItem } from "./ThekeContentItem";
import { ThekePlayView } from "./ThekePlayView";
import { ThekeProfileDetail } from "./ThekeProfileDetail";
import { ThekeSearchCard } from "./ThekeSearchCard";
import "./texts";
import "./look.css";
import "./inner.css";

/**
 * Look „Feinkost-Theke“: verspielter Feinkostladen – Holzwand, rot-creme gestreifte Markise,
 * Navigation als Kreidetafeln an einer Stange, Butterbrotpapier, Preisschilder und ein runder
 * Stempel als Start-Knopf.
 */
export const look: LookDefinition = {
  id: "theke",
  navPlacement: "below-top",
  Nav: ThekeNav,
  PlayView: ThekePlayView,
  Backdrop: ThekeBackdrop,
  ownsBackground: true,
  ProfileDetail: ThekeProfileDetail,
  ContentItem: ThekeContentItem,
  contentListClassName: "grid grid-cols-1 lg:grid-cols-2 gap-x-8",
  SearchCard: ThekeSearchCard,
  searchGridClassName: "theke-shelves grid grid-cols-[repeat(auto-fill,minmax(212px,1fr))] gap-x-4 gap-y-10 px-5 pb-10 pt-4",
};
