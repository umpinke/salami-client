# Salami Client – Analyse & Projektplan

Stand: 12.10.2026 · Basis: `NoRiskClient/noriskclient-launcher` v0.6.28 (Commit `2a283c5`)

---

## 1. Analyse der NoRisk-Codebase

### 1.1 Was ist überhaupt Open Source?

| Repo | Inhalt | Lizenz |
|---|---|---|
| `noriskclient-launcher` | Launcher: Tauri 2 + React 18/TS + Rust | GPL-3.0 |
| `nrc-configs` | Pack-Definitionen (welche Mods ein Client-Pack lädt) | – |
| `translations`, `nrc-server-api`, `nrc-server-mappings` | Hilfsprojekte | GPL-3.0 |
| `nrc-core`, `nrc-client`, `nrc-cosmetics`, `nrc-friends` … (11 Mods) | **die eigentliche In-Game-Mod** | **privat / closed source**, nur über NoRisks privates Maven |

**Konsequenz:** Nur der **Launcher** lässt sich forken. Für die In-Game-Features (Module, ClickGUI, HUD, das Salami-Theme im Spiel) gibt es keinen NoRisk-Code, auf dem man aufbauen kann. Salami braucht dafür eine **eigene Fabric-Mod** (`salami-core`).
Die NoRisk-In-Game-Features (Cosmetics, Friends, Minigames, NoRisk-HUD) können nicht übernommen werden. Sie sind proprietär und hängen an NoRisk-Accounts und -Servern. Die Launcher-Features (Profile, Mod-Browser, Skins, Clips, Import aus anderen Launchern …) bleiben erhalten.

### 1.2 Launcher-Architektur

- **Frontend** `src/` (~86k Zeilen TS)
  - Bausteine: React 18, Zustand-Stores (in localStorage persistiert), Tailwind, framer-motion/ogl für Hintergründe.
  - Übersetzungen über i18next: 10 Sprachen inkl. **Deutsch**, flaches JSON mit ~2.800 Keys.
  - Icons über iconify, offline gebündelt durch `scripts/generate-icon-bundle.mjs`.
- **Backend** `src-tauri/src/` (~86k Zeilen Rust)
  - `commands/`: Tauri-Commands
  - `minecraft/`: Auth, Launch, Downloads, Modloader
  - `integrations/`: `modrinth.rs`, `curseforge.rs`, `norisk_packs.rs`, `mod_dependencies.rs`
  - `state/`: Profile (SQLite), Config
  - Workspace-Crates: `norisk-ipc`, `norisk-logging`, `norisk-capture` (Clips)
- `noriskclient-backend/` ist ein toter Gitlink auf ein privates Repo. Der Build braucht ihn nicht.

### 1.3 Pack-System – der Andockpunkt für `salami-core`

Welche Mods der Launcher in ein Spiel lädt, ist **komplett datengetrieben** (`integrations/norisk_packs.rs`):

- Ein Pack ist eine JSON-Datei mit `mods`, `inheritsFrom`, `loaderPolicy` …
- Jede Mod hat eine Quelle `modrinth` | `maven` | `url` und eine Kompatibilitäts-Map pro MC-Version und Loader.
- Bei Fabric wird per `fabric.addMods` injiziert (`installer.rs`, `launch/launcher.rs`).
- Das NoRisk-Prod-Pack enthält 11 private Maven-Mods + 16 Modrinth-Mods (Sodium, Lithium, Fabric API, ImmediatelyFast, EntityCulling, FerriteCore, ModMenu …).

**Problem:** Die Pack-Liste kommt von `api.norisk.gg/api/v1/launcher/modpacks-v3`, mit NoRisk-Token. Jeder erfolgreiche Abruf überschreibt die lokale Kopie (`state/norisk_packs_state.rs:79-115`).
→ **Im Fork:** Die Pack-Quelle wird auf eine eigene, statische JSON umgestellt (z. B. GitHub Releases/raw). Das Salami-Pack enthält dann `salami-core` und dieselben Open-Source-Performance-Mods.

### 1.4 Theme-System

| Baustein | Datei | Befund |
|---|---|---|
| Akzentfarben | `store/useThemeStore.ts` | 16 Presets, setzt CSS-Vars `--accent*`. **Aber:** 678 Stellen in 142 Dateien lesen `accentColor.value` direkt als Inline-Style. Ein Theme muss deshalb über den Store laufen, CSS allein reicht nicht. |
| Launcher-Themes | `store/launcher-theme-store.ts` | `LAUNCHER_THEMES` hat genau 1 Eintrag (`christmas_theme`: Akzent + Hintergrundbild). Das ist der **ideale Andockpunkt fürs Salami-Theme**. |
| Hintergründe | `store/background-effect-store.ts`, `layout/AppLayout.tsx:228-332` | enum + switch. Eine Kopie des switch steckt in `EffectPreviewCard.tsx`. |
| Overlay-Effekt | `features/snow-effect/Snowfall.tsx` | Vorlage für fallende Salamischeiben |
| Tailwind | `tailwind.config.js` | `norisk`-Farbe `#125a9d` und Glow-Schatten sind hart codiert |
| Fonts | `store/font-store.ts`, `config/fonts.ts` | Presets + Custom |

### 1.5 Branding

- `/norisk/i` kommt 1.883× in `src/` und 1.657× in `src-tauri/src/` vor. Der Großteil ist funktional (`norisk_token`, `NoRiskApi`, `noriskpack`).
  Sichtbar sind ~170 Strings in TSX-Dateien und ~80 Werte in jeder Sprachdatei.
- Zu ändern:
  - `tauri.conf.json`: productName, identifier `gg.norisk.NoRiskClientLauncherV3`, Deep-Link `norisk://`, CSP, Dateityp `.noriskpack`
  - `config.rs`: Datenordner `ProjectDirs("gg","norisk","NoRiskClientV3")`
  - Cargo-/npm-Paketnamen
  - Logos (`public/logo.png`, `norisk_logo_color.png`, App-Icons)
  - Fenstertitel
- **Logos und Name „NoRisk“ sind Marken.** Die GPL deckt nur den Code ab, die Marken müssen komplett raus. Die GPL-Lizenz und die Hinweise auf NoRisk/LiquidLauncher als Herkunft müssen dagegen bleiben.

### 1.6 Kopplung an NoRisk-Server (kritisch für einen Fork)

- **Hosts:**
  - `api.norisk.gg` (+ `-staging`), `cdn.`, `assets.`, `cms.`, `blog.`, `discord-api.`, `analytics-api-staging.`, `flagsmith-staging.norisk.gg`
  - Applixir-Werbung
- **Features mit NoRisk-Token:** Capes/Shop, Cosmetics, Friends/Chat (WebSocket), Werbung/AFK-Punkte, Adventskalender, Benachrichtigungen, NRC-Assets, Pack-Refresh.
  Es gibt **keinen globalen Schalter** dafür.
- **Updater:** Endpoint `api.norisk.gg/.../releases-v2` + NoRisks Minisign-Pubkey. **Ein unveränderter Fork könnte sich selbst durch den NoRisk-Launcher ersetzen.** Endpoint und Pubkey müssen also ausgetauscht werden.
- **CurseForge-API-Key:** NoRisks eigener Key steht hart im Code (`integrations/curseforge.rs:33`). Ein Fork braucht einen **eigenen Key** (CurseForge Developer Console) und darf NoRisks Key nicht mitliefern.
- Der gleiche Datenordner wie NoRisk würde Profile und Accounts mit einem installierten NoRisk-Launcher vermischen.

### 1.7 Mod-Downloader – Befund

**Vorhanden:**

- Einheitliche Modrinth- und CurseForge-Suche inkl. Installation (`ModrinthSearchV2.tsx`, `unified_mod.rs`).
- Transitive Pflicht-Abhängigkeiten (BFS, Tiefe 20, Version passend zu Loader + MC).
- Batch-**Updates** über SHA1/Fingerprint.

**Schwächen:**

1. **Quilt verliert Mods:** `associated_loader` = erster Loader der Version. Dadurch wird eine Mod in Quilt-Profilen beim Start übersprungen. Ein Fallback Quilt→Fabric fehlt überall.
2. **Quick-Install kann eine inkompatible Version wählen:** Fallback auf `versions[0]` ohne Warnung (`utils/content-status.ts:29-34`).
3. **Keine Konfliktprüfung:** `Incompatible` wird gemappt, aber nie geprüft. Es gibt keine Vorschau „diese Abhängigkeiten werden mitinstalliert“, und Fehler werden nur geloggt.
4. **Abhängigkeiten nur pro Plattform-ID:** Fabric API von Modrinth und von CurseForge landen beide im Profil (Duplikate). Optionale Abhängigkeiten werden nie angeboten.
5. **Keine Batch-Installation** (ein Item pro Command).
6. **Filter dünn:** nur 1 MC-Version und 1 Loader erreichen das Backend. Kategorien sind nur UND-verknüpft. Umgebungs-Filter gibt es nur als „required“-Checkbox.
7. **CurseForge-Suche schwach:** Kategorien werden verworfen, kein Client/Server-Filter, Name-Sortierung falsch herum.
8. **Kein Caching** von Suche und Versionslisten, **kein 429/Retry-Handling**.
9. CurseForge-Fingerprint geht in 4 Install-Pfaden verloren, und `relationType 6` wird inkonsistent gemappt.
10. `ModrinthSearchV2.tsx` ist ein Monolith mit 3.283 Zeilen und doppelten Install-Handlern.

### 1.8 Entwicklungsumgebung

| Tool | Status |
|---|---|
| Node 24 / npm 11 | ✅ |
| Java (OpenJDK 25) | ✅ |
| Yarn | ❌ → `corepack enable` |
| Rust / Cargo | ❌ → rustup + MSVC Build Tools (für Tauri nötig) |
| Gradle | ❌ → nicht nötig, Gradle-Wrapper im Projekt |
| git | ✅ |

⚠️ **Der Projektordner liegt in OneDrive.** Rusts `target/` (mehrere GB), `node_modules` und Gradle-Caches würden dauernd synchronisiert und Dateisperren verursachen.
**Empfehlung:** Das Repo außerhalb von OneDrive anlegen (z. B. `C:\dev\salami-client`) und per Git/GitHub sichern.

Aktuelle Fabric-Versionen (meta.fabricmc.net): stable 26.3, 26.2, 26.1.x, 1.21.11 …; Loader 0.19.5.

---

## 2. Scope – was gebaut wird und was nicht

### ✅ Wird gebaut

| Bereich | Umsetzung |
|---|---|
| Grundstruktur, Rebrand | Launcher-Fork „Salami Client“ + Fabric-Mod `salami-core` + eigenes Pack |
| Salami-Theme | Launcher **und** In-Game. Ein Schalter für alles (siehe §4) |
| Mod-Downloader | siehe §5 |
| Visuals | Overlay-/HUD-System, Custom Crosshair, Block-Outline |
| Screen | PvP-Logo-Overlay |
| Kits & Combos | Hotbar-/Inventar-Layouts pro Gamemode, Combo-Counter-HUD |
| Client | Easy Leave: Taste, die alle Kampfmodule sofort abschaltet |
| Client | **Safe Client Mode** (in meiner Auslegung): ein Schalter, der alle Utility-Module hart deaktiviert, global oder automatisch auf Servern einer editierbaren Liste. Er schützt vor versehentlichen Bans. |
| Infrastruktur | Config-System, ClickGUI, DE/EN-Übersetzungen, GPL-3.0 |

### ⚠️ Wird gebaut, aber offen als „Utility“ gekennzeichnet

Auto Crystal, Auto Plug, Anchor-Macros, Safe Anchor, Auto Totem, Hover Totem, Double Hand Totem, Breach Swap, Auto Mace, Auto Elytra, Attribute Swap, Speed Lunge, Block-ESP.

Diese Module automatisieren Kampfaktionen. Auf den meisten Servern (Hypixel, NoRisk-eigene Server, gängige CPvP-Server) sind sie **verboten und führen zum Bann**.
Sinnvoll einsetzbar sind sie im Singleplayer, auf eigenen Servern und auf Servern, die so etwas erlauben (Anarchy).
Sie erscheinen in der ClickGUI in einer eigenen Kategorie mit Warnhinweis. Safe Client Mode schaltet sie ab.

### ❌ Wird nicht gebaut

| Feature | Grund |
|---|---|
| Screen-Share Detection / Protection | Einziger Zweck ist, Server-Moderatoren bei einer Prüfung zu täuschen. |
| Undetected Mode | Anti-Cheat-Umgehung, also gezielte Täuschung der Server. |
| Spuren verwischen / Self-Destruct, „Humanizer“ gegen Anti-Cheat | gleicher Grund |
| NoRisks closed-source `nrc-*`-Mods ins Salami-Pack packen | proprietär, an NoRisk-Accounts gebunden, verstößt gegen deren Nutzungsbedingungen |

### ❓ Muss geklärt werden

- **Live Tracker:** Was genau ist gemeint? Eigene Stats/Session-Tracking geht. Das Orten oder Verfolgen *anderer* Spieler über Server hinweg baue ich nicht (Privatsphäre).
- **Ghost Spear:** Was genau soll das Modul tun?

---

## 3. Projektstruktur (Monorepo, GPL-3.0)

```
salami-client/
├── LICENSE                         GPL-3.0 (+ Herkunftshinweis NoRisk/LiquidLauncher im README)
├── launcher/                       Fork von noriskclient-launcher v0.6.28 (mit Git-Historie → Attribution)
│   ├── src/
│   │   ├── branding/brand.ts       NEU: einzige Quelle für Name, Logos, Links, URLs
│   │   ├── config/features.ts      NEU: Feature-Flags (norisk-Account-Features, Werbung, Analytics → aus)
│   │   ├── themes/salami/          NEU: Palette, Hintergrund „Salamifall“, Logos, Witz-Texte (i18n-Overlay)
│   │   ├── store/launcher-theme-store.ts   + Eintrag salami_theme
│   │   └── components/modrinth/v2/ → zerlegt in search/, filters/, install/, hooks/
│   └── src-tauri/
│       ├── src/branding.rs         NEU: Identifier, Datenordner, API-Basis-URLs
│       ├── src/integrations/salami_packs.rs   Pack-Quelle ohne Token (statische JSON)
│       └── src/integrations/mod_dependencies.rs   + Konflikte, Vorschau, plattformübergreifende Deduplizierung
├── mod/                            salami-core (Fabric + Loom, Gradle-Wrapper)
│   ├── build.gradle, gradle.properties
│   └── src/main/
│       ├── java/<package>/
│       │   ├── SalamiClient.java           ClientModInitializer, Bootstrap
│       │   ├── api/module/                 Module, Category, ModuleInfo
│       │   ├── api/setting/                Setting<T>: Bool, Number(Slider), Mode(Enum), Color, Keybind
│       │   ├── core/ModuleManager.java     Registrierung, Keybinds; Events nur an aktive Module
│       │   ├── core/event/                 schlanker Event-Bus über Fabric-API-Callbacks (Tick, HUD, World-Render)
│       │   ├── config/                     JSON (Gson), Config-Profile, Auto-Save, Versionierung
│       │   ├── theme/                      Theme-Interface, DefaultTheme, SalamiTheme
│       │   ├── gui/clickgui/               ClickGUI (Rechts-Shift), komplett theme-fähig
│       │   ├── gui/hud/                    Overlay-System: verschiebbare, skalierbare HUD-Elemente
│       │   ├── modules/visual/             Crosshair, BlockOutline, PvpLogo, Hud-Elemente
│       │   ├── modules/misc/               QuickLeave, Kits, ComboCounter, SafeMode
│       │   ├── modules/utility/            die ⚠️-Module (crystal/, totem/, anchor/, mace/, spear/)
│       │   └── mixin/
│       └── resources/
│           ├── fabric.mod.json, salami.mixins.json
│           └── assets/salami/lang/{en_us,de_de}.json, textures/gui/…
├── packs/salami-stable.json        salami-core + Sodium, Lithium, Fabric API, ImmediatelyFast, …
├── branding/                       Logo-Quellen (SVG), App-Icons
└── docs/
```

**Performance-Prinzipien für die Mod:**

- Deaktivierte Module kosten nichts: Ein Modul meldet sich beim Aktivieren am Event-Bus an und beim Deaktivieren wieder ab.
- Keine Allokationen pro Frame in Render-Pfaden.
- Mixins minimal und gezielt halten.

---

## 4. Salami-Theme-Konzept

**Ein Schalter für alles:** In den Launcher-Einstellungen wird „Salami-Modus“ aktiviert.

1. **Launcher:**
   - Das Theme wird über `launcher-theme-store` aktiviert (Akzentfarbe läuft über den Store, damit die 678 Inline-Stellen mitziehen).
   - Zusätzlich wird `data-theme="salami"` auf `<html>` gesetzt. Darüber laufen die CSS-Overrides für Hintergründe, Buttons und Rahmen.
   - Logo und Name kommen aus `brand.ts`, abhängig vom Theme.
   - Ein i18n-Overlay (`addResourceBundle(..., overwrite)`) ersetzt ausgewählte Texte, solange der Modus aktiv ist.
2. **Brücke ins Spiel:** Beim Start übergibt der Launcher `-Dsalami.theme=salami`.
3. **In-Game:** `salami-core` liest das Flag und schaltet Titelbildschirm, ClickGUI, HUD und Ladebildschirm um. Ein In-Game-Schalter kann das überschreiben.

**Look & Witz:**

- **Palette:** Salami-Rot `#B23A3A`, Fett-Weiß `#F3E3D3`, Pfeffer-Schwarz `#2B1D1A`, Pelle-Braun `#7A4A35`.
- **Buttons:** Scheibenoptik mit „Fettaugen“ (radial-gradients).
- **Spinner:** rotierende Salamischeibe.
- **Hintergrund:** „Salamifall“ (fallende Scheiben, Vorlage Snowfall) auf einem Aufschnittbrett.
- **Texte:**

  | Normal | Salami-Modus |
  |---|---|
  | Spielen | Anschneiden |
  | Wird geladen … | Reift noch … |
  | Mods | Belag |
  | Einstellungen | Gewürzmischung |
  | Fehler | Da ist die Wurst geplatzt |

- **In-Game:**
  - Salami-Logo im Titelbildschirm
  - Ladebalken als Salamistange, die Scheibe für Scheibe abgeschnitten wird
  - Crosshair-Preset „Scheibe“
  - optionaler „Schnipp“-Sound beim Klicken (Standard: aus)

---

## 5. Mod-Downloader – Verbesserungsplan

1. **Korrektheit:**
   - Quilt→Fabric-Fallback (Loader-Liste statt erstem Eintrag).
   - Quick-Install filtert streng nach Loader + MC-Version und warnt, wenn keine passende Version existiert.
   - CurseForge-Fingerprint überall durchreichen.
   - `relationType`-Mapping vereinheitlichen.
2. **Abhängigkeiten:**
   - Vorschau-Dialog vor der Installation: Pflicht-Abhängigkeiten, schon installierte, inkompatible.
   - `Incompatible`-Prüfung.
   - Plattformübergreifende Deduplizierung (über Mod-ID/Slug).
   - Optionale Abhängigkeiten anbieten.
   - Fehler sichtbar im UI statt nur im Log.
3. **Suche & Filter:**
   - Mehrere MC-Versionen und Loader.
   - Kategorien wahlweise UND/ODER.
   - Umgebungs-Filter Client/Server je required/optional/unsupported.
   - CurseForge-Kategorien sauber mappen.
4. **One-Click:** Sammel-„Warenkorb“ mit Batch-Installation (1 Command, parallele Downloads) und „In neues Profil installieren“.
5. **Robustheit:**
   - Cache für Suche und Versionslisten.
   - 429/Retry mit Backoff.
   - Manueller Download-Fallback für CurseForge-Mods mit gesperrter Distribution.
6. **Refactor:** `ModrinthSearchV2.tsx` in Hooks + Komponenten zerlegen.
7. **Eigener CurseForge-Key** per Build-Secret statt hart im Code.

---

## 6. Roadmap

| Phase | Inhalt | Ergebnis |
|---|---|---|
| 0 | Setup: Repo außerhalb OneDrive, Fork mit Historie, Rust/Yarn installieren, Launcher **unverändert** bauen (Baseline) | baut & startet |
| 1 | **Grundstruktur + Rebrand** (siehe unten) | „Salami Client“ startet ein Fabric-Spiel mit `salami-core` |
| 2 | **Salami-Theme** Launcher + In-Game-Brücke | Salami-Modus komplett |
| 3 | Utility-Module: **Auto Crystal, Auto Totem, Anchors** + Safe Client Mode | erste Module in der ClickGUI |
| 4 | **Mod-Downloader** (§5) | |
| 5 | Visuals (HUD, Crosshair, Outline, PvP-Logo), Kits, Combo-Counter, Easy Leave | |
| 6 | Restliche Utility-Module (Mace, Spear, Attribute Swap, ESP) | |
| 7 | Release-Infrastruktur: eigener Updater-Endpoint + Signaturschlüssel, Installer | |

**Phase 1 im Detail:**

- **Launcher:**
  - `brand.ts`/`branding.rs` anlegen.
  - Identifier, Datenordner, Deep-Link und Fenstertitel umstellen.
  - Logos ersetzen.
  - NoRisk-Account-Features, Werbung, Analytics und Flagsmith hinter Feature-Flags legen (Standard: aus).
  - Updater deaktivieren, bis ein eigener Endpoint existiert.
  - Pack-Quelle umstellen.
- **Mod:** `salami-core`-Gerüst mit Modul-System, Config, einfacher ClickGUI und DE/EN.

---

## 7. Entscheidungen (12.10.2026)

| Frage | Entscheidung |
|---|---|
| Minecraft-Version | **1.21.11** (Fabric) |
| Sprache der Mod | **Java 21**, Mojang-Mappings |
| Speicherort | `C:\dev\salami-client` (außerhalb OneDrive), Git-Monorepo, Launcher als `git subtree` |
| Easy Leave | Taste (Standard: Ende), die **alle Kampfmodule sofort abschaltet** und per Safe Mode gesperrt hält |
| Hosting Packs/Updates | später GitHub Releases; bis dahin eingebettete Packs, Updater aus |
| Live Tracker | Visual-Modul: Rüstung des Gegners über seinem Namen |
| Ghost Spear | Utility-Makro: Droppen → Offhand → Slotwechsel automatisch (Desync-Trick) |
| Auto Plug | gestrichen (Funktion unklar) |

---

## 8. Ausbau 2 (13.10.2026)

Wünsche: Capes und weitere NoRisk-Features behalten oder nachbauen, Launcher soll nicht mehr nach NoRisk aussehen, abgeschnittener Reiter „Varieties“, Partikel pro Quelle abschaltbar, Trigger Bot, Aim Assist, Player ESP, Screenshare-Block.

### Befund

- NoRisks Capes, Cosmetics, Friends und Emotes stecken in den closed-source Mods `nrc-cosmetics`, `nrc-friends`, `nrc-animations`, `nrc-client`. Sie brauchen NoRisk-Login und -Server und sehen im Spiel nach NoRisk aus. Übernehmen geht nicht (§2 ❌), also selbst bauen.
- Das NoRisk-Pack enthält außerdem freie Modrinth-Mods (WaveyCapes, 3D Skin Layers, AppleSkin, Shulker Box Tooltip, Mod Menu, MoreCulling, Tiers …). Die kann das Salami-Pack direkt mitliefern.
- „Varieties“ ist abgeschnitten, weil jeder Navigationsknopf nur 64 px breit ist und die Beschriftung nicht umbrechen darf.

### Phasen

| Phase | Inhalt | Ergebnis |
|---|---|---|
| A | **Partikel-Modul** (Visual): Explosionen, Krit-/Verzauberungs-Treffer, Totem, Blockabbau, Trank-/Effektpartikel, Feuer/Rauch, Wetter, Sonstige – je einzeln abschaltbar | weniger Partikel-Spam im Kampf |
| B | **Player ESP** (Utility): Spieler durch Wände als Box oder Leucht-Umriss, Farbe einstellbar | |
| C | **Trigger Bot** und **Aim Assist** (Utility) | |
| D | **Salami Capes**: eigene Capes (mitgelieferte Salami-Designs + eigenes PNG), im Spiel und auf den Elytren. Mojang-Capes im Launcher bleiben. | Cape ohne NoRisk |
| E | **Salami-Pack erweitern** um die freien Mods aus dem NoRisk-Pack | gewohnte Features zurück |
| F | **Launcher-Design-Update**: Navigation neu (Beschriftung passt), eigener Look statt NoRisk-Layout, NoRisk-Reste suchen und ersetzen | sieht nicht mehr nach NoRisk aus |
| G | Gametests, Build, Installer, im echten Spiel prüfen | |

### Entscheidungen

| Frage | Entscheidung |
|---|---|
| Capes | Eigenes System in `salami-core`. **Sichtbar nur für dich**: damit andere Spieler sie sehen, bräuchte es einen eigenen Cape-Server (Hosting) – später möglich. |
| Trigger Bot, Aim Assist, Player ESP | Kategorie „Utility“ wie die anderen Kampfmodule: offen gekennzeichnet, durch Safe Mode und Easy Leave abschaltbar, keine Anti-Cheat-Tarnung. |
| Screenshare-Block | **Wird nicht gebaut** (wie §2 ❌ „Screen-Share Detection / Protection“): Bildschirmaufnahme erkennen und dann alles ausschalten dient nur dazu, Moderatoren bei einer Screenshare-Prüfung zu täuschen. Easy Leave bleibt als manueller Schalter. |
