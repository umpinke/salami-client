# Salami Client

Minecraft-Client für **1.21.9 bis 26.3 (Fabric, Quilt)** mit eigenem Launcher, Modul-System und dem Salami-Look.

| Teil | Ordner | Technik |
|---|---|---|
| Launcher | [`launcher/`](launcher/) | Fork des [NoRisk-Launchers](https://github.com/NoRiskClient/noriskclient-launcher) (Tauri 2, React, Rust) |
| In-Game-Mod `salami-core` | [`mod/`](mod/) | Fabric, Java 21/25, Mojang-Mappings, [Stonecutter](https://stonecutter.kikugie.dev) (eine Jar pro Minecraft-Version) |
| Pack-Definitionen | [`packs/`](packs/) | JSON, im Launcher eingebettet |
| Online-Zähler | [`server/online-counter/`](server/online-counter/) | Node.js, ohne Abhängigkeiten, auf eigenem Server |
| Cape-Server + Prüf-Webseite | [`server/capes/`](server/capes/) | Node.js, ohne Abhängigkeiten, auf eigenem Server |
| Logo & Branding | [`branding/`](branding/) | `generate_logo.py` (Pillow) |
| Plan & Roadmap | [`docs/PLAN.md`](docs/PLAN.md) | |

## Schnellstart

### Mod bauen und testen

```bash
cd mod
./gradlew build                # alle Versionen, Jars nach mod/versions/<version>/build/libs/
./gradlew :1.21.11:runClient   # Minecraft 1.21.11 mit salami-core starten
./gradlew installDev           # alle Jars in den Salami-Launcher kopieren (Pack "salami")
```

Ein Quellcode für alle Versionen: Unterschiede stehen als Stonecutter-Kommentare im Code
(`//? if >=26.1 { … }`) oder als Ersetzungen in `mod/stonecutter.gradle.kts`. Welche Jar welche
Minecraft-Versionen abdeckt, steht in `mod/stonecutter.properties.toml`:

| Jar | Minecraft |
|---|---|
| `salami-core-1.21.10.jar` | 1.21.9, 1.21.10 (ohne Speer-Module – Speere gibt es erst ab 1.21.11) |
| `salami-core-1.21.11.jar` | 1.21.11 |
| `salami-core-26.1.2.jar` | 26.1, 26.1.1, 26.1.2 |
| `salami-core-26.2.jar` | 26.2 |
| `salami-core-26.3.jar` | 26.3 |

Der Code in `src/` steht immer für 1.21.11. Wer in der IDE auf eine andere Version umschaltet
(`./gradlew "Set active project to 26.3"`), stellt vor dem Commit zurück: `./gradlew "Reset active project"`.
Neue Minecraft-Version: Eintrag in `settings.gradle.kts` und `stonecutter.properties.toml`, dann
`node launcher/scripts/update-salami-pack.mjs` (trägt salami-core und die Pack-Mods ins Pack ein).

Im Spiel:

- **Rechte Umschalttaste** öffnet das Salami-Menü (änderbar unter *Optionen → Steuerung → Salami Client*): Mods, Kacheln für HUD, Configs und Panels, darunter **Freunde** (Chat) und **Cosmetics** (Capes anlegen und hochladen).

### Launcher bauen

Voraussetzungen:

- Node 18+
- Rust (stable) + MSVC Build Tools
- Yarn 1 (`npx yarn@1.22.22`)

```bash
cd launcher
npx yarn@1.22.22 install
npx yarn@1.22.22 tauri dev
```

Die Clip-Aufnahme (`salami-capture`, FFmpeg) braucht zusätzlich **LLVM/libclang** (`winget install LLVM.LLVM`). Ohne LLVM startest du den Launcher ohne Clip-Funktion:

```bash
npx yarn@1.22.22 tauri dev --config src-tauri/tauri.dev-noclips.json
```

Beim ersten Start legt der Launcher den Datenordner `%APPDATA%\salamiclient\SalamiClient` an. Das Standardprofil „Salami Client 1.21.11“ lädt `salami-core` – beim Entwickeln aus `dev/salami-core-<version>.jar` (vorher in `mod/` `./gradlew installDev` ausführen), im installierten Launcher die mitgelieferte Kopie – sowie die Performance-Mods von Modrinth. Eigene Profile mit 1.21.9 bis 26.3 (Fabric oder Quilt) bekommen automatisch die passende Jar. Importierte `.noriskpack`-Profile laufen automatisch mit dem Salami-Pack.

### Performance

- **Pack-Mods**: Sodium, Lithium, ImmediatelyFast, EntityCulling, FerriteCore, More Culling, BadOptimizations, ScalableLux und Dynamic FPS (weniger FPS, solange das Fenster im Hintergrund ist). Sie ändern nichts am Aussehen. `update-salami-pack.mjs` trägt nur Release- oder Beta-Versionen ein; für 26.3 gibt es ScalableLux bisher nur als Alpha, dort fehlt es deshalb. Mods mit eigenen Abhängigkeiten (z. B. Particle Core mit Kotlin und Fzzy Config) gehören nur mit diesen Abhängigkeiten ins Pack – `-PpackMods` (siehe [Tests](#tests)) findet so etwas.
- **Java**: Ohne eigene GC-Einstellung startet der Launcher Minecraft mit G1 und zusätzlich `-XX:+DisableExplicitGC`, `-XX:+PerfDisableSharedMem`, `-XX:ReservedCodeCacheSize=400M` und `-XX:-DontCompileHugeMethods`, ab Java 25 (Minecraft 26.x) außerdem mit `-XX:+UseCompactObjectHeaders`.
- **salami-core**: Der Benchmark-Test (`-PsalamiTests=Benchmark`, siehe [Tests](#tests)) misst jedes Modul einzeln. So sieht man, welches Modul pro Frame am meisten kostet.

### Launcher installieren (Windows)

```bash
cd launcher
npx yarn@1.22.22 tauri build --config src-tauri/tauri.release-local.json --config src-tauri/tauri.release-windows.json
```

`tauri.release-local.json` gilt für alle Systeme (salami-core, kein Updater). `tauri.release-windows.json` ergänzt die Windows-Clip-Dateien. Das erzeugt `src-tauri/target/release/bundle/nsis/Salami Client_<version>_x64-setup.exe`. Der Installer installiert ohne Admin-Rechte nach `%LOCALAPPDATA%\Salami Client` und legt Startmenü- und Desktop-Verknüpfung an.

Der Build baut vorher `salami-core` für alle Versionen (`scripts/bundle-salami-core.mjs` → `./gradlew launcherBundle`). Der Installer legt die Jars als `mods\salami-core-<version>.jar` neben die Launcher-Exe, damit braucht der installierte Launcher keinen Dev-Ordner. Liegt zusätzlich eine Jar im Datenordner `dev\` (von `installDev`), nimmt der Launcher die neuere der beiden Dateien.

Der Build braucht **LLVM** (`winget install LLVM.LLVM`), denn er baut auch das Clip-Aufnahmeprogramm (`yarn stage-native` → `salami-capture`). Der Installer legt `salami-capture.exe`, die FFmpeg-DLLs und den Grafik-Hook neben die Launcher-Exe. Clips sind standardmäßig aus; wer sie unter *Einstellungen → Clips* einschaltet, speichert mit **F8** die letzten 30 Sekunden. Der lokale Build lässt nur die Updater-Signatur weg.

### macOS und Linux

Den Launcher gibt es auch für macOS (Apple Silicon und Intel, ab macOS 11) und Linux (AppImage und `.deb`). Gebaut werden diese Versionen auf GitHub Actions ([`.github/workflows/launcher-mac-linux.yml`](.github/workflows/launcher-mac-linux.yml)), weil ein Windows-Rechner keine Mac- und Linux-Pakete bauen kann. Ohne Release lässt sich der Workflow unter *Actions → Launcher macOS + Linux → Run workflow* starten. Die Pakete liegen dann als Artefakte am Lauf.

| System | Datei im Release | Datenordner |
|---|---|---|
| macOS (Apple Silicon) | `Salami-Client_<version>_macOS-Apple-Silicon.dmg` | `~/Library/Application Support/dev.salamiclient.SalamiClient` |
| macOS (Intel) | `Salami-Client_<version>_macOS-Intel.dmg` | wie oben |
| Linux | `Salami-Client_<version>_Linux.AppImage` oder `_Linux.deb` | `~/.local/share/salamiclient` |

Die mitgelieferten `salami-core`-Jars liegen in den App-Ressourcen: auf dem Mac in `Salami Client.app/Contents/Resources/mods/`, unter Linux in `/usr/lib/Salami Client/mods/`. Die Mac-App bringt die Clip-Aufnahme mit (`salami-capture`); unter Linux gibt es keine Clips.

Die Mac-App ist nur ad hoc signiert, nicht von Apple notarisiert (dafür bräuchte es ein Apple-Developer-Konto). Beim ersten Öffnen blockiert macOS sie deshalb. Spieler öffnen sie einmal über *Systemeinstellungen → Datenschutz & Sicherheit → Trotzdem öffnen*. Danach startet sie normal, und Auto-Updates laufen ohne Nachfrage.

### Updates veröffentlichen (Auto-Update)

Der Launcher prüft beim Start `latest.json` im neuesten Release von
[github.com/umpinke/salami-client](https://github.com/umpinke/salami-client/releases)
und installiert neuere Versionen selbst – samt mitgelieferter `salami-core`-Mod.

1. Version in `launcher/src-tauri/tauri.conf.json` und `launcher/src-tauri/Cargo.toml` erhöhen.
2. Release bauen und hochladen:

   ```bash
   cd launcher
   node scripts/release.mjs --notes "Was ist neu"
   ```

   Das Skript startet zuerst den macOS/Linux-Build auf GitHub. Danach baut es den
   Windows-Installer – ohne den Updater-Schlüssel – und wartet auf GitHub. Dann signiert es in
   einem eigenen Schritt (`scripts/release-signing.mjs`) die Windows-, Mac- und Linux-Updatedateien
   lokal mit `tauri signer sign` und prüft alle Signaturen gegen `plugins.updater.pubkey`. Nur
   dieser Schritt bekommt Schlüssel und Passwort. Hat der Schlüssel ein Passwort, fragt das Skript
   hier verdeckt danach (oder nimmt `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` aus der eigenen Shell).
   Zum Schluss schreibt es `latest.json` für alle Systeme und legt mit `gh` das GitHub-Release
   `v<version>` mit allen Installern an. Der Release-Commit muss dafür committet und gepusht sein.
   Mit `--windows-only` geht es wie früher nur um Windows.

   Einmalig vorher: den CurseForge-Key als GitHub-Secret hinterlegen (`node scripts/release.mjs --setup-ci`).

Der Signaturschlüssel liegt in `%USERPROFILE%\.salami-client\updater.key` (nicht im Repo, auch
nicht bei GitHub – die Mac- und Linux-Pakete signiert `release.mjs` lokal).
**Sichern!** Ohne ihn können installierte Launcher keine Updates mehr annehmen. Der passende
öffentliche Schlüssel steht in `tauri.conf.json` unter `plugins.updater.pubkey`. Er sollte ein
Passwort haben; `release.mjs` fragt beim Signieren danach (ohne Passwort fragt es nicht).

### CurseForge-API-Key

CurseForge vergibt API-Keys pro Anwendung. Der Salami Client liefert deshalb keinen fremden Key mit. Den eigenen Key (kostenlos über die [CurseForge Console](https://console.curseforge.com)) in `%USERPROFILE%\.salami-client\curseforge.key` ablegen (nicht im Repo, wie `updater.key`). `build.rs` baut ihn bei jedem Build ein – `tauri dev`, `tauri build` und `release.mjs`. Das Release-Skript bricht ohne Key ab.

Alternativ für einen einzelnen Build als Umgebungsvariable (hat Vorrang vor der Datei):

```bash
SALAMI_CURSEFORGE_API_KEY='$2a$10$...' npx yarn@1.22.22 tauri build
```

Ohne Key funktioniert Modrinth normal. Bei CurseForge-Funktionen erscheint der Hinweis „CurseForge ist nicht eingerichtet“.

## Mod-Downloader

| Verbesserung | Wo |
|---|---|
| **Strikte Versionswahl**: Minecraft-Version und Loader müssen passen, Releases vor Betas. Kein stiller Rückfall auf eine inkompatible Version mehr. | `src/features/mod-downloader/version-selection.ts` |
| **Quilt lädt Fabric-Mods**: beim Installieren, beim Spielstart und bei Abhängigkeiten. Vorher wurden solche Mods beim Start übersprungen. | `ModLoader::runs_on` / `pick_for_profile` (Rust) |
| **Abhängigkeits-Vorschau** vor jeder Mod-Installation (siehe unten) | `install-preflight.tsx`, `DependencyPlanModal.tsx` |
| **Mod-Warenkorb**: Mods sammeln und in einem Rutsch in ein Profil installieren, jeweils in der passenden Version | `cart-store.ts`, `InstallCart.tsx` |
| **Mehrere MC-Versionen und Loader** gleichzeitig in der Modrinth-Suche (ODER-verknüpft) | `modrinth.rs` (Facetten-Gruppen) |
| **Cache** für Suche (2 min) und Versionslisten (10 min) | `ttl-cache.ts`, `unified-service.ts` |
| **Rate-Limit-Schutz**: 429/5xx werden mit Backoff wiederholt (`Retry-After`/`X-Ratelimit-Reset`) | `utils/http_retry.rs` |
| CurseForge: Fingerprint wird bei jeder Installation gespeichert (bessere Update-Erkennung). `relationType 6` („Include“) wird einheitlich als eingebettet behandelt. | `ModrinthSearchV2.tsx`, `unified_mod.rs` |

Die **Abhängigkeits-Vorschau** zeigt:

- was mitinstalliert wird,
- welche optionalen Abhängigkeiten du dazuwählen kannst,
- welche installierten Mods laut Autor inkompatibel sind.

Unit-Tests (ohne zusätzliche Abhängigkeiten, Node 22+):

```bash
cd launcher
node --test "src/features/mod-downloader/*.test.ts"
```

## Architektur in Kürze

**Launcher**

- Alles Markenspezifische steht zentral an zwei Stellen: `src-tauri/src/branding.rs` und `src/config/salami.ts`.
- **Keine NoRisk-Dienste**: NoRisks Cape-Shop, Cosmetics und Freunde/Chat (Salami hat dafür eigene, siehe `server/capes/`), News, Werbung, Analytics, Feature-Flags und der NoRisk-Login sind aus dem Code entfernt. Als Sicherheitsnetz lehnt `utils/http_client.rs` Anfragen an `*.norisk.gg` ab (etwa aus importierten Packs), im Frontend blockiert sie die CSP.
- Die 3D-Skin-Ansicht und die Skin-Vorschaubilder nutzen [skinview3d](https://github.com/bs-community/skinview3d) (MIT, `src/lib/skin3d/`).
- Packs und Standardprofile kommen aus `packs/*.json`. Optional lassen sie sich remote laden (`PACKS_URL`). Die Pack-Logik heißt intern noch `norisk_packs`, und `.noriskpack`-Dateien lassen sich weiter importieren.
- Updates kommen über `latest.json` im neuesten GitHub-Release (`UPDATE_BASE_URL` in `branding.rs`), signiert mit dem eigenen Schlüssel.

**Mod**

- Module (`module/Module`) haben typisierte Settings (`setting/*`) und werden als JSON gespeichert (`config/salami/config.json`, verzögert und atomar).
- Nur **aktive** Module bekommen Tick- und HUD-Aufrufe. Ein fehlerhaftes Modul wird abgeschaltet statt das Spiel abstürzen zu lassen.
- Module mit eigenem Menü (`Module#hasScreen`, z. B. Freunde, Capes, Hüte) bekommen in der Panel-Ansicht einen „Öffnen“-Knopf.
- **Freunde** (`friends/Friends`, `FriendsApi`, `FriendsCommand`): Anmeldung wie bei den Capes über Mojang (`hasJoined`), danach ein eigenes Sitzungs-Token; Live-Verbindung per Server-Sent Events für Nachrichten, Anfragen und Online-Status. Server: `server/capes/friends.mjs`.
- **Cosmetics** (`gui/CosmeticsScreen`, `capes/CapeLibrary`): liest und schreibt die Cape-Auswahl des Launchers (`capes.json` im Ordner aus `-Dsalami.capes.dir`), ohne Launcher `config/salami/cape.png`.
- **Salami-Modus**:
  - Theme-Schalter im Mod (`SalamiThemeModule`).
  - Der Launcher kann ihn per `-Dsalami.theme=salami|default` vorgeben.

## Salami-Modus

Ein Schalter für den ganzen Client: *Launcher → Einstellungen → Darstellung → Salami-Modus*.

| Wo | Was passiert |
|---|---|
| Launcher | Salami-Rot als Akzent, fallende Salamischeiben, Schneidebrett-Hintergrund auf dem Spielen-Tab, Wortspiele („Anschneiden“, „Belag“, „Gewürzmischung“ …) |
| Spiel | Salami-Buttons in allen Menüs, „SALAMI / CLIENT“-Logo im Titelbildschirm, ClickGUI und HUD in Salami-Farben |

Der Launcher speichert den Schalter in seiner Config (`salami_theme`) und gibt ihn beim Start per `-Dsalami.theme` an `salami-core` weiter. Im Spiel kannst du den Modus zusätzlich jederzeit in der ClickGUI umschalten (*Client → Salami-Modus*). Die Texturen werden live getauscht, ein Ressourcen-Neuladen ist nicht nötig.

Alle Grafiken erzeugen die Skripte unter `branding/` (`generate_logo.py`, `generate_mc_textures.py`).

## Tests

```bash
cd mod
./gradlew :1.21.11:runClientGameTest   # eine Version
./gradlew runClientGameTest             # alle Versionen nacheinander
./gradlew :26.3:runClientGameTest -PsalamiTests=Benchmark   # nur einzelne Tests (Teile des Klassennamens, kommagetrennt)
./gradlew :26.3:runClientGameTest -PpackMods                # mit allen Pack-Mods (Sodium, Lithium …) – Kompatibilitätstest
```

Der Benchmark schreibt die Zeit aller Module und die zehn teuersten einzeln ins Log (`[Benchmark]`).

`-PpackMods` lädt die Pack-Mods von Modrinth und legt sie unverändert in den `mods`-Ordner des Testlaufs, wie im Launcher und samt eingebetteter Bibliotheken. Bis 1.21.11 übersetzt Fabric Loader ihre Namen beim Start. Weggelassen werden nur Dynamic FPS (bremst das unfokussierte Testfenster) und Sodium auf 26.1.x: Der Test-Screenshot der Fabric-Gametest-API überspringt dort den Schritt, in dem Sodium das Gelände vorbereitet; im Spiel läuft es normal. Weitere Mods zum Eingrenzen: `-PpackModsSkip=badoptimizations,scalablelux`.

Der Test startet Minecraft mit einer Testwelt und prüft:

- Salami-Menü per Taste, Panel-Ansicht (ohne Kampf-Module, mit Social), Freunde- und Cosmetics-Menü,
- Klicks auf Module und Einstellungen,
- den Salami-Look,
- Live Tracker, Capes (auch Anlegen und Hochladen aus dem Spiel), Configs und Kits in echten Testszenen,
- Screenshots aller Visuals und des HUD-Editors.

Screenshots: `mod/build/run/clientGameTest/screenshots/`.

## Module (Stand)

| Kategorie | Modul | Kurz |
|---|---|---|
| Client | Salami-Modus | kompletter Salami-Look (Launcher-Schalter wird übernommen) |
| Visuals | Watermark · Info HUD · Armor HUD · Item Counter · Combo Counter · PvP-Logo | **Overlay-System**: alle frei verschiebbar im HUD-Editor (ClickGUI → „HUD bearbeiten“) |
| Visuals | Crosshair | eigenes Fadenkreuz: Kreuz, Punkt, Kreis, Salamischeibe; Farbe, Größe, Abstand, Dicke |
| Visuals | Block Outline | farbiger Umriss für den anvisierten Block |
| Visuals | Live Tracker | Rüstung (mit Haltbarkeit) und Hand-Items über dem Namen von Gegnern |
| Sonstiges | Kits | Inventar-Layouts für Crystal, Anchor, Mace, Spear, Sword, Axe, SMP, NethPot, UHC und 3 eigene; Taste K sortiert das Inventar |
| Sonstiges | Panic Disconnect | eigene Taste trennt sofort die Verbindung, ohne Menü und Rückfrage |
| Social | Freunde | Salami-Spieler per Minecraft-Name als Freunde hinzufügen, Anfragen annehmen, chatten (Salami-Menü → Freunde, eigene Taste oder `/salami friends`); Nachrichten und Online-Meldungen auch im Chat, Klick darauf schreibt `/salami msg <name>` vor |

**Cosmetics** (Salami-Menü → Cosmetics, im Launcher die Seite „Cosmetics“): vorerst der Reiter *Capes*. Klick auf ein Cape legt es an – mit dem Launcher dieselbe Auswahl wie dort –, „Hochladen“ übernimmt ein eigenes PNG (Breite = 2 × Höhe, 64–2048 px) und legt es gleich an. Eigene Capes erscheinen erst nach der Freigabe durch einen Admin.

Über *ClickGUI → Configs* lassen sich alle Einstellungen als JSON-Datei exportieren, importieren oder auf Standard zurücksetzen.

## Hinweis

Der Salami Client enthält keine Kampf-Automatisierung mehr (die frühere Kategorie *Utility* mit Crystal- und Anchor-Makros, Auto Totem usw. wurde entfernt) und **keine** Funktionen, um Anti-Cheat oder Server-Moderation zu umgehen oder zu täuschen.

## Lizenz

GPL-3.0, siehe [LICENSE](LICENSE).

Der Launcher basiert auf dem NoRisk Launcher (GPL-3.0). Dieser basiert wiederum teilweise auf [LiquidLauncher](https://github.com/CCBlueX/LiquidLauncher). Die 3D-Skin-Ansicht nutzt [skinview3d](https://github.com/bs-community/skinview3d) (MIT).

Der Salami Client nutzt keine Dienste, Server, Pakete oder Logos von NoRisk. „NoRisk“ und das NoRisk-Logo sind Marken ihrer Inhaber.
