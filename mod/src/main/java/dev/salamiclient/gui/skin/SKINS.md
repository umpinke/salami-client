# Looks der Salami-Menüs im Spiel

Die Menüs im Spiel folgen dem Look, den der Spieler im Launcher gewählt hat. Der Launcher startet das
Spiel mit `-Dsalami.look=theke|werkbank|salumeria`; `Skins.current()` lädt per Klassenname
`gui.skin.<id>.<Id>Skin` (öffentlicher Konstruktor ohne Parameter). Ohne Angabe oder bei Fehlern gilt
`theke`.

| Look | Paket | Stand |
|---|---|---|
| Feinkost-Theke | `gui/skin/theke` | fertig – Vorbild |
| Pixel-Werkbank | `gui/skin/werkbank` | offen |
| Salumeria | `gui/skin/salumeria` | offen |

Entwürfe (854 × 480, GUI-Größe 2): `design-concepts-ingame/` im Repo (a = Theke, b = Werkbank, c = Salumeria).

## Der Vertrag

`Skin` hat nur Bausteine; Bildschirme halten Logik und Eingabe und zeichnen **nur** über diese
Bausteine und `skin.ink(fläche)`. Keine festen Farben in den Bildschirmen (Ausnahme: Inhalte wie
Cape-Vorschauen, Farbwähler-Felder, Spielgrafik der Minispiele, Online-Grün in Freunde).

- **Hintergrund:** `backdrop(g, w, h)` über Minecrafts eigenem Hintergrund (Unschärfe in der Welt,
  Panorama ohne Welt). Deckt Chat/Hotbar ab. `backdropTop()` = Höhe der Deko oben (Theke: Markise 17);
  Fenster und Leisten beginnen darunter. **Ohne** Hintergrund: HUD-Editor und Emote-Rad.
- **Flächen:** `surface(kind, …)` + `ink(kind)` für `WINDOW` (Hauptfenster), `SIDE` (Seitenleiste,
  Panel-Kopf), `CARD` (Kasten im Fenster, Notizzettel), `POPUP` (Farbwähler, Leisten, Overlays),
  `INPUT`. `Ink` = text, dim, accent, onAccent, line, warning.
- **Bedienelemente:** `button` (PRIMARY/SECONDARY/DANGER/GHOST – GHOST nur auf hellen Flächen),
  `iconButton` + `buttonInk` (Symbol zeichnet der Bildschirm), `toggle` (+ `toggleSize`), `checkbox`,
  `tab` (waagrecht auf WINDOW, senkrecht auf SIDE; `count` darf null sein), `row` (≤ 24 px Zeile,
  > 24 px Karte), `input`, `slider`, `scrollbar` (thumbY relativ zur Spur), `badge` (x = rechte Kante),
  `tag` (+ `tagWidth`/`tagHeight`: Schild für Tasten-Hinweise, lesbar auf jeder Fläche), `divider`.
- **Schrift:** `heading(…, scale)` / `headingWidth`. Sonst Minecraft-Pixelschrift.
- **Eigene Aufbauten:** `menuView()` (Startmenü) und `modsView()` (Mod-Liste). Model-Records rein,
  `render` und `hit` rechnen mit **derselben** Layout-Methode. `ModsView.Tab.count()` (Extras ohne „an“),
  `ModsView.Tool.kind()` (Symbol), `tooltip`, `scrollArea`, `contentHeight`.

Gemeinsame Helfer (für alle Looks):

- `Icons` – Pixel-Symbole (12er für Menü-Einträge, 7er: Zahnrad, Stift, ⏎, Haken, Kreuz, Lupe …).
- `TextFit` – nie mitten im Wort abschneiden: `draw` (verkleinern bis 0,75, dann „…“), `wrap`
  (Umbruch auch nach Bindestrich: „Panel-“/„Ansicht“), `wrapBlock` (Beschreibung ganz zeigen, notfalls
  kleiner, sonst nach dem letzten ganzen Satz + `cut()` → Tooltip).
- `gui/SalamiWindow` – Hintergrund in UI-Koordinaten, Fenster mittig unter `backdropTop()`, Kopfzeile
  (Zurück GHOST, Titel `heading`, Linie in `ink.text`), `hint` (Tasten-Hinweis unten als `tag`,
  bricht an „·“ in mehrere Schilder um).

Kleine Flächen (Menügröße 150 % im Modul „Oberfläche“ ⇒ nur ~285 × 160 UI-Pixel): die Views müssen
selbst schrumpfen. Theke: Startmenü erst knappe Fassung, dann alles verkleinert; Mod-Liste erst
engere Kategorien, dann ohne „Sorte:“, dann ohne Werkzeuge, flacherer Notizzettel; Panel-Ansicht
setzt die rechte Knopfgruppe in eine zweite Reihe.

## Welcher Bildschirm nutzt was

| Bildschirm | Bausteine |
|---|---|
| `SalamiMenuScreen` | backdrop, `menuView()` |
| `ModsScreen` | backdrop, `modsView()` |
| `ModSettingsScreen` | SalamiWindow, toggle (Modul + Bool), row, slider, button (Modus, Aktion), input (Taste, Text), heading + divider („Darstellung“), scrollbar, ColorPicker |
| `ConfigsScreen` | SalamiWindow, row (Karten), Text in `ink(CARD)` |
| `CosmeticsScreen` | SalamiWindow, tab (Reiter, Buddy-Plätze), row (Karten, angelegt = selected), toggle (Stempel „angelegt“), input + button PRIMARY (Mini-Me-Name), scrollbar |
| `FriendsScreen` | SalamiWindow, input, iconButton (+, ✔, ✖), row (Freunde, Anfragen), badge, heading, button (Senden, Entfernen DANGER, Einschalten), surface CARD (fremde Nachrichten), divider |
| `ClickGuiScreen` (Panel-Ansicht) | backdrop, button/iconButton (Leiste oben, unter `backdropTop()`; Tests lesen `ClickGuiScreen.barTop()`/`panelTop()`), surface SIDE (Panel-Kopf) + WINDOW (Panel), row, toggle, checkbox, slider, input (Taste), scrollbar, `SalamiWindow.hint`, ColorPicker |
| `HudEditorScreen` | kein backdrop; surface POPUP (Leiste, Liste, Hinweis), button, row, toggle, tag (Name + Größe) |
| `MinigamesScreen` | backdrop, eigenes Fenster (WINDOW, Kopfzeile wie SalamiWindow), row (Spielkarten), surface SIDE + tab senkrecht (Bestenliste), CARD (Tabelle, Infospalte), POPUP + button (Pause/Ergebnis); Spielgrafik bleibt |
| `EmoteWheelScreen` | kein backdrop; button (Felder), surface POPUP (Mitte), `SalamiWindow.hint` |
| `ColorPicker` | surface POPUP, input (Hex), checkbox (Regenbogen), button SECONDARY |

## Texturen

GUI-Sprites unter `src/main/resources/assets/salami/textures/gui/sprites/<look>/` – sie landen
automatisch im GUI-Atlas, Aufruf über `g.blitSprite(RenderPipelines.GUI_TEXTURED,
Identifier.fromNamespaceAndPath("salami", "<look>/<name>"), x, y, w, h, farbe)`. Doppelte Auflösung
(1 UI-Pixel = 2 Bildpixel). Kacheln/Neun-Teilung per `.png.mcmeta`
(`{"gui":{"scaling":{"type":"tile","width":20,"height":16}}}`). Die Theke-Bilder (Markise, Holz,
Kreide, Papier, Stempel, Regler-Griff) erzeugt `mod/tools/skin-textures/theke.py`
(`python -I theke.py <ausgabeordner>`, braucht Pillow).

## Screenshots pro Look

Aus `mod/`:

```
.\gradlew.bat ":26.3:runClientGameTest" "-PsalamiTests=Client,Mods,Customize,Cosmetics" "-PsalamiLook=theke"
```

Bilder in `mod/versions/26.3/build/run/clientGameTest/screenshots` (werden bei jedem Lauf gelöscht –
vorher wegkopieren). Die wichtigsten: `salami-02-menu`, `salami-07d-menu-german`, `salami-20-mods-menu`,
`salami-07-mods-german`, `salami-22-mod-settings`, `salami-02-clickgui-salami`, `salami-04b-friends`,
`salami-04c-cosmetics`, `salami-05b-configs`, `salami-61-color-picker`, `salami-69-hud-editor-elements`,
`salami-84-emote-wheel`, `salami-92-cosmetics-buddies`; große Menüs (150 %): `salami-07e-menu-large`,
`salami-07f-mods-large`, `salami-07g-panels-large`. Minispiele: `-PsalamiTests=Minigames`
(`salami-minigames-01-menu`, `-02-leaderboard`, `-21-pause`). Vorher alle Versionen bauen:

```
.\gradlew.bat ":1.21.10:compileGametestJava" ":1.21.11:compileGametestJava" ":26.1.2:compileGametestJava" ":26.2:compileGametestJava" ":26.3:compileGametestJava"
```

Neue Zeichenaufrufe nur über `GuiGraphics`-Methoden, die es in allen Versionen gibt (`fill`,
`fillGradient`, `blitSprite`, `blit`, `enableScissor`, `pose()`); Text immer über `render/GuiDraw`.
Muss etwas versionsabhängig sein: Stonecutter-Kommentare wie in `GuiDraw`.

## Die Theke als Vorbild

- `Theke.java`: Farben (Papier #f4ead9/#fffaf1, Pfeffer #2b1d1a, Tinte #7a5a4a, Salami-Rot #b23a3a,
  Kreide #23302b, Holz #5a3a28) und Zeichenhelfer (Papier, Tafel im Holzrahmen, Schnur, Schiene,
  Markise, gepunktete Linien, Preisschild, fette Überschrift).
- `ThekeSkin.java`: alle Bausteine. WINDOW = Metzgerpapier, SIDE = Kreidetafel im Holzrahmen,
  Schalter = runder Stempel (an: rot mit Haken, aus: gestrichelter Kreis – nichts durchgestrichen).
- `ThekeMenuView.java`: Markise → Kreideschild „Salami“ → Papierkarte Mods mit Stempel und
  „Öffnen ⏎“ → zwei Schienen mit je drei Tafeln → Holztheke mit Preisschild. Knappe Fassung, wenn
  die Höhe nicht reicht.
- `ThekeModsView.java`: Tafel „Sorte:“ links (Kategorien, Werkzeuge), Papier „Zutatenliste · …“
  rechts mit Suchzettel, „Esc“, Einträgen in 1–3 Spalten (Stempel, Name, Punktlinie, Zahnrad) und
  Notizzettel mit der vollen Beschreibung des Eintrags unter der Maus.
- Look-eigene Texte: `salami.theke.*` in beiden Sprachdateien (ein anderer Look bekommt
  `salami.werkbank.*` bzw. `salami.salumeria.*`).
