import type { ComponentType, ReactNode } from "react";
import type { TopNavItem } from "../../components/navigation/TopNav";
import type { NavKey } from "../../components/profiles/v3/ProfileLeftRailV3";
import type { ContentTileProps } from "../../components/profiles/v3/tabs/local-content/ContentTile";
import type { ContentInstallStatus, Profile } from "../../types/profile";
import type { UnifiedModSearchResult } from "../../types/unified";

/**
 * Ein „Look“ ist ein komplettes Erscheinungsbild des Launchers. Der Spieler wählt ihn beim ersten
 * Start und kann ihn in den Einstellungen jederzeit wechseln. Regeln und Stellschrauben stehen in
 * `themes/looks/CONTRACT.md`.
 */
export type LookId = "salumeria" | "werkbank" | "theke";

export const LOOK_IDS: readonly LookId[] = ["salumeria", "werkbank", "theke"];

/** Gilt, solange noch keiner gewählt ist (hinter dem Auswahl-Bildschirm). */
export const DEFAULT_LOOK: LookId = "theke";

export interface LookNavProps {
  /** Alle Einträge; `isAction` (Einstellungen) gehört nicht in die Navigation, außer der Look will es. */
  items: TopNavItem[];
  /** id des aktiven Eintrags (Detailseiten sind schon auf ihren Reiter abgebildet). */
  activeItem: string;
  onItemClick: (id: string) => void;
}

export interface LaunchVersion {
  id: string;
  label: string;
  icon?: string;
  isCustom?: boolean;
  profileId?: string;
}

export interface LookPlayViewProps {
  profiles: Profile[];
  selectedProfile: Profile | null;
  versions: LaunchVersion[];
  onVersionChange: (profileId: string) => void;
  playerName: string | null | undefined;
}

export interface LookBackdropProps {
  /** Route ohne führenden Schrägstrich, z. B. "play", "profiles", "mods/modrinth/abc". */
  activeTab: string;
}

export interface LookProfileNavItem {
  key: NavKey;
  /** Fertig übersetzt. */
  label: string;
  count?: number;
  /** "content" = Mods, Ressourcen, Shader, Datenpakete, Salami; "world" = Welten, Screenshots, Logs. */
  group: "content" | "world";
}

/**
 * Profil-Detailseite. Alle Logik (Starten, Bearbeiten, Menüs, Dialoge) liegt in
 * `ProfileDetailViewV3`; der Look ordnet nur die fertigen Teile an.
 */
export interface LookProfileDetailProps {
  profile: Profile;
  /** Profilname als HTML (Minecraft-Farbcodes schon umgesetzt) – mit `dangerouslySetInnerHTML`. */
  nameHtml: string;
  backLabel: string;
  onBack: () => void;
  /** Profilbild samt Loader-Abzeichen (das Abzeichen öffnet die Loader-Wahl). */
  icon: ReactNode;
  /** Version, Loader, Gruppe, Modpack und Konto als fertige, anklickbare Etiketten. */
  chips: ReactNode;
  launch: { isLaunching: boolean; statusMessage: string | null; onLaunch: () => void };
  onEdit: () => void;
  stats: { lastPlayed: string; playtime: string; hasPlaytime: boolean; disk: string | null };
  actions: { onSyncPacks: () => void; onOpenFolder: () => void; onDuplicate: () => void; onExport: () => void };
  /** „Mehr“-Knopf samt Kontextmenü (bringt seinen eigenen `relative`-Rahmen mit). */
  moreMenu: ReactNode;
  nav: { items: LookProfileNavItem[]; active: NavKey; onChange: (key: NavKey) => void };
  /** Inhalt des aktiven Reiters (Liste, Welten, Screenshots oder Logs) – füllt die Höhe, scrollt selbst. */
  content: ReactNode;
}

/** Ein Ergebnis im Mod-Browser (Suche über Modrinth/CurseForge). */
export interface LookSearchCardProps {
  hit: UnifiedModSearchResult;
  installStatus: ContentInstallStatus | null;
  /** Download-Zahl schon kurz formatiert („82 Mio.“). */
  downloadsLabel: string;
  /** Kategorie-Etiketten ohne Loader, fertig übersetzt. */
  categories: string[];
  installBusy: boolean;
  alreadyInstalled: boolean;
  installDisabled: boolean;
  onInstall: () => void;
  /** Öffnet die Detailseite (Versionen, Beschreibung). */
  onOpen: () => void;
  onOpenAuthor: () => void;
  /** Fertiger Warenkorb-Knopf. */
  cartButton: ReactNode;
}

export interface LookDefinition {
  id: LookId;
  /**
   * Wo die Navigation sitzt: "top" in der Mitte der oberen Leiste, "below-top" als eigene Zeile
   * darunter, "bottom" als Leiste am unteren Fensterrand.
   */
  navPlacement: "top" | "below-top" | "bottom";
  Nav: ComponentType<LookNavProps>;
  /** Die Startseite (Reiter „Spielen“). */
  PlayView: ComponentType<LookPlayViewProps>;
  /** Liegt hinter Leiste und Inhalt (z. B. Markise, Rahmen, Muster). `pointer-events: none`! */
  Backdrop?: ComponentType<LookBackdropProps>;
  /** true: der Look malt den Fensterhintergrund selbst; die Hintergrund-Effekte entfallen. */
  ownsBackground?: boolean;
  /** Eigene Profil-Detailseite (sonst die Standard-Kopfkarte mit Leiste rechts). */
  ProfileDetail?: ComponentType<LookProfileDetailProps>;
  /** Eine Zeile/Kachel der Inhaltsliste eines Profils (Mods, Ressourcen, Shader …). */
  ContentItem?: ComponentType<ContentTileProps>;
  /** Raster der Inhaltsliste (Tailwind-Klassen für `VirtuosoGrid` `listClassName`). */
  contentListClassName?: string;
  /** Eigene Ergebnis-Karte im Mod-Browser; die Ergebnisse stehen dann im Raster statt als Liste. */
  SearchCard?: ComponentType<LookSearchCardProps>;
  /** Raster der Suchergebnisse (Tailwind-Klassen), nur mit `SearchCard`. */
  searchGridClassName?: string;
}
