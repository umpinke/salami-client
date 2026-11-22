# Salami-Design „Feinkost“

Der Launcher hat einen eigenen Look statt des NoRisk-Aufbaus: warm, wie eine Feinkosttheke, mit
Salami als wiedererkennbarem Motiv. Der Salami-Modus (fallende Scheiben, Texte wie „Anschneiden“)
bleibt ein Extra obendrauf; dieser Look gilt immer.

## Farben

| Name | Wert | Wofür |
|---|---|---|
| Salami-Rot | `#B23A3A` (Standard-Akzent, `ACCENT_COLORS.salami`) | Hauptaktionen, aktive Zustände |
| Fett-Creme | `#F3E3D3` (`SALAMI_COLORS.fat`) | Etiketten, Text auf Rot, Rahmen von Profilbildern |
| Pfeffer | `#2B1D1A` (`SALAMI_COLORS.pepper`) | Text auf Creme, dunkle Flächen |
| Pelle | `#7A4A35` (`SALAMI_COLORS.casing`) | Lochung der Etiketten, Kanten |
| Grund | `#15100E` (warm, in `AppLayout`) | Fensterhintergrund |

- Aktionsfarben immer über `accentColor` aus `useThemeStore` – Spieler können den Akzent ändern.
  Creme und Pfeffer sind feste Hausfarben.
- Flächen über `SURFACE` (`components/ui/design-system.ts`), nicht eigene Grau-/Blautöne.
  Keine kalten Farben (`slate`, `gray-900`, Blau) für Flächen.

## Bausteine

- **Knöpfe:** `Button` mit `variant="3d"` ist die Hauptaktion: volle Akzentfarbe mit harter
  Unterkante `kante(accent)` (`0 3px 0` dunkler). Andere Knöpfe: `secondary`/Standard mit
  `KANTE_SOFT`. Eigene Knöpfe genauso: Füllung + `boxShadow: kante(...)`, beim Drücken 1px nach unten.
- **Etiketten:** `DeliLabel` (`themes/salami/SalamiStage.tsx`) für Version, Loader, Status,
  Kategorien – Creme mit Lochung, oder `tone="red"`. Größe `sm` in Listen.
- **Kopfband:** Streifen im Salami-Anschnitt: Klasse `salami-fat-spots` + `style={kopfband(accent)}`.
  Für Kartenköpfe, Seitenköpfe, Dialoge (sitzt schon in `Modal`).
- **Karten:** `SURFACE.base` mit `SURFACE.line`-Rahmen und `var(--border-radius)`; beim Hover
  2px anheben, Rahmen `hexAlpha(accent, 0.55)` und ein weicher Schein in Akzentfarbe.
  Vorbild: Profil-Kachel in `components/profiles/ProfileCardV2.tsx` (Zweig `layoutMode === "grid"`).
- **Scheibe:** `SalamiSlice` als Bild für Leeres, Standard-Profilbild (`ProfileIconV2`),
  Ladeanzeige (`LoadingSpinner`, Klasse `salami-spin`), `SalamiStage` als Bühne für Figuren.
- **Symbole:** `PixelIcon` (`themes/salami/PixelIcon.tsx`, 12×12, `currentColor`) für die
  Hauptnavigation und große Seiten-Symbole; neue Pixel-Symbole dort ergänzen. Kleine Symbole in
  Knöpfen dürfen Solar-Icons bleiben.
- **Leere Zustände:** `EmptyState` (Symbol auf einer Scheibe) statt eigener „Keine …“-Kästen.

## Schrift und Text

- Überschriften, Kartentitel, Knopftexte: `font-minecraft`. Fließtext: Standardschrift.
- Kein `lowercase`/`uppercase` per CSS auf deutschen Texten – Nomen werden großgeschrieben.
  Texte immer über i18n (`de.json`/`en.json`, Salami-eigenes in `themes/salami/salami-texts.ts`).

## Bewegung

Kurz und ruhig: 150–200 ms, `prefers-reduced-motion` respektieren. Kein Glas/Blur als Stilmittel,
keine Neon-Glows.
