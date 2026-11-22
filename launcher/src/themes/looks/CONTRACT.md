# Looks – Regeln und Stellschrauben

Ein Look ist ein komplettes Erscheinungsbild des Launchers. Es gibt drei (`types.ts`): `salumeria`,
`werkbank`, `theke`. Der Spieler wählt beim ersten Start (`components/looks/LookChooser.tsx`) und
kann in Einstellungen → Darstellung → Look jederzeit wechseln. `LookSync` setzt
`<html data-look="<id>">`.

## Was ein Look liefert (`themes/looks/<id>/`)

- `index.tsx` exportiert `look: LookDefinition`:
  - `navPlacement`: `"top"` (Mitte der oberen Leiste), `"below-top"` (eigene Zeile darunter) oder
    `"bottom"` (Leiste am unteren Rand). `AppLayout` setzt die Navigation dorthin.
  - `Nav` (`LookNavProps`): die Hauptnavigation. Einträge mit `isAction` (Einstellungen) gehören nicht
    hinein, außer der Look will sie zeigen; `onItemClick(id)` navigiert bzw. öffnet die Einstellungen.
    Beschriftungen kommen fertig übersetzt in `item.label`. Pixel-Symbole: `themes/salami/PixelIcon`.
  - `PlayView` (`LookPlayViewProps`): die Startseite. Muss können, was
    `components/launcher/SalamiPlayView.tsx` kann: gewähltes Profil, Start-Knopf
    (`MainLaunchButton` mit Profilwahl – wiederverwenden, nicht nachbauen), zuletzt gespielt,
    „kein Profil“ → Profil erstellen, Spielfigur (`PlayerRig`).
  - `Backdrop` (optional): liegt hinter Leiste und Inhalt (Markise, Rahmen, Muster);
    `pointer-events: none`. `ownsBackground: true` schaltet die Hintergrund-Effekte ab.
  - Innenseiten (optional, aber jeder Look soll sie haben – sonst sieht die Seite aus wie der
    alte NoRisk-Aufbau):
    - `ProfileDetail` (`LookProfileDetailProps`): die ganze Profilseite. Die Logik bleibt in
      `ProfileDetailViewV3`; der Look ordnet nur fertige Teile an (Bild, Etiketten, Start,
      Kennzahlen, Aktionen, „Mehr“-Menü, Reiter mit Zahlen, Inhalt des aktiven Reiters).
    - `ContentItem` (`ContentTileProps`) + `contentListClassName`: eine Zeile der Inhaltsliste
      (Mods, Ressourcen, Shader …). Alle Funktionen von `ContentTile` behalten: an/aus, auswählen,
      Version wechseln (`VersionSelectDropdown`), Schnell-Update, Menü.
    - `SearchCard` (`LookSearchCardProps`) + `searchGridClassName`: ein Ergebnis im Mod-Browser;
      die Ergebnisse stehen dann im Raster (`VirtuosoGrid`).
    - Vorbild: `theke/ThekeProfileDetail.tsx`, `ThekeContentItem.tsx`, `ThekeSearchCard.tsx`,
      `theke/inner.css`.
- `look.css`: alle Regeln **nur** unter `:root[data-look="<id>"]`. Daneben `inner.css`
  (Innenseiten), `logs.css` (Log-Fenster) und `dialogs.css` (Dialoge, Meldungen, Willkommen,
  Update-Fenster) – alle in `index.tsx` importiert.
- Vorschau: `design-preview.html` (Looks per `localStorage`, siehe `src/dev/design-preview.ts`);
  `?window=updater` (`&status=checking | downloading | error`) zeigt das Update-Fenster,
  `?signedout` den Willkommens-Bildschirm.

## Variablen (in `look.css` unter `:root[data-look="<id>"]` setzen)

| Variable | Wirkung |
|---|---|
| `--font-minecraft` | Überschriften, Titel, Knopftexte (Klasse `font-minecraft`, alle `h1`–`h6`) |
| `--font-smallcaps` | Fließtext / Standardschrift |
| `--border-radius` | Eckenradius überall (`!important` nötig, der Wert wird inline am `<html>` gesetzt) |
| `--surface-base`, `--surface-hover`, `--surface-raised` | Flächen von Karten, Feldern, Zeilen |
| `--surface-line`, `--surface-line-strong` | Rahmen/Haarlinien |
| `--surface-solid`, `--surface-solid-raised`, `--surface-veil` | deckende Flächen (Dialoge, Menüs), Schleier |
| `--look-kante` | `box-shadow` voller Knöpfe (Standard: harte Unterkante) |
| `--look-kante-soft` | `box-shadow` flacher Knöpfe und Karten |
| `--look-band-bg`, `--look-band-border` | Kopfband (Elemente mit Klasse `salami-fat-spots`; deren `background-image` darf der Look ersetzen) |

Vom Akzent des Spielers stehen bereit: `--accent`, `--accent-hover`, `--accent-light`,
`--accent-dark`, `--accent-rgb` (für `rgba(var(--accent-rgb), .5)`). Abstufungen per
`color-mix(in srgb, var(--accent) 60%, black)`. Vorhandene Schriften: `"Fredoka"`, `"MinecraftTen"`
(Pixel), `"Cormorant Garamond"` (Serife, normal + kursiv, 300–700), `"SmallCaps"`.

## Ansatzpunkte (Klassen an den gemeinsamen Bausteinen)

`look-shell` (Fenster), `look-topbar`, `look-nav-row` (Zeile für "below-top"/"bottom"),
`look-content`, `look-nav` (Standard-Navigation), `look-btn` + `look-btn--primary | accent |
secondary | ghost | danger | destructive | active`, `look-play` (Start-Knopf-Gruppe), `look-card`,
`look-tabs` (Reiter-Gruppe), `look-label` + `look-label--fat | red | ghost`, `look-tag`,
`look-input`, `look-modal`, `look-menu` (Dropdowns, Kontextmenüs), `look-stage`
(Salamischeiben-Bühne), `salami-fat-spots` (Kopfband).

Innenseiten: `look-profile-icon` (Profilbild), `look-content-toolbar` / `look-content-scroll`
(Inhaltsliste), `look-search-types` / `look-search-bar` / `filters-sidebar` (Mod-Browser),
`look-clip-grid` / `look-clip-card` / `look-clip-caption` / `look-clip-title` / `look-clip-meta`
(Clips), `look-settings` / `-side` / `-search` / `-list` / `-entry` / `-tab` (`data-active`) /
`-sub` / `-divider` / `-paper` (Einstellungen – dieselben Klassen trägt auch der Dialog
„Profileinstellungen“, beide sehen in jedem Look gleich aus), `look-picker` (Look-Auswahl),
`look-profile-card` + `data-layout="grid | list | compact"` (Profilübersicht; das Kontextmenü liegt
in der Karte), `look-mod-detail` / `look-mod-header` / `look-mod-section` (+ `look-mod-description |
look-mod-side | look-mod-versions`) / `look-mod-gallery` / `look-mod-shot` (Mod-Detailseite),
`look-version-row` (eine Version im Versions-Verzeichnis), `look-friends` / `look-friends-side` /
`look-friend-row` (`data-active`) / `look-chat` / `look-chat-day` / `look-chat-msg` (`data-own`)
(Freunde und Chat).

Dialoge und kleine Teile: `look-option` – wählbare Kachel oder Zeile in Dialogen und Einstellungen
(Profil-Assistent, Versionen, Loader, Java, Auflösung, Konten); gewählt mit `data-selected` (wo der
Baustein keine Attribute durchreicht: Klasse `look-option--selected`), gesperrt mit
`aria-disabled` / `look-option--disabled`; `look-option--main` ist der Hauptweg (Eigenes Profil),
`look-option--image` eine Kachel mit Bild im Hintergrund (Loader). `look-status` +
`look-status--success | error | warning | info` (Hinweiskästen, `StatusMessage`), `look-toast`
(+ `look-toast--success | error | loading`), `look-tooltip`, `look-empty` / `look-empty-icon`
(leere Zustände). Diese Regeln stehen je Look in `dialogs.css`.

Willkommen und Nebenfenster: `look-welcome` / `-controls` (Fensterknöpfe) / `-hero` / `-title` /
`-panel` / `-panel-title` / `-skip`; Update-Fenster (325×400, eigenes Fenster mit `LookWindowSync`):
`look-updater` / `-card` / `-brand` / `-logo` / `-title` / `-status` (`data-status`) / `-progress`
/ `-bar` / `-actions`. Malt der Look den Hintergrund selbst (`ownsBackground`), entfallen dort wie
im Hauptfenster die Hintergrund-Effekte.

Helle Flächen (Papier) unter gemeinsamen Bausteinen: die Bausteine sind für dunkle Flächen gebaut
(`text-white`, `bg-white/x`, inline `rgba(255, 255, 255, …)`, `--surface-*`). Auf einer hellen
Fläche diese in einer eigenen Klasse umbiegen – Beispiel: Block „Papier“ in `theke/inner.css`.

Viele Bausteine setzen Farben inline; in `look.css` deshalb bei Bedarf `!important`. Fehlt ein
Ansatzpunkt, im Bericht nennen – nicht die gemeinsamen Bausteine selbst ändern.

## Regeln

- Alle Looks bleiben dunkel mit heller Schrift (die Seiten nutzen `text-white`); helle Flächen
  (Papier, Etiketten) nur auf Elementen, deren Textfarbe der Look selbst setzt.
- Nichts am Verhalten ändern. Texte über i18n (`themes/salami/salami-texts.ts` für Neues, de + en).
- Kein CSS-`lowercase`/`uppercase` auf Texten; für Kapitälchen `font-variant-caps`.
- Jede Seite muss in jedem Look benutzbar sein: Spielen, Profile (+ Detail), Mods (+ Detail),
  Skins, Shop, Freunde, Clips, Einstellungen, Dialoge. Bei 1280×760 und 1000×700 prüfen.
- Bewegung kurz (150–200 ms), `prefers-reduced-motion` respektieren.
