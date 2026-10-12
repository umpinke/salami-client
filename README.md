# Salami Client

Minecraft-Client für **1.21.11 (Fabric)** mit eigenem Launcher, Modul-System und dem Salami-Look.

| Teil | Ordner | Technik |
|---|---|---|
| Launcher | [`launcher/`](launcher/) | Fork des [NoRisk-Launchers](https://github.com/NoRiskClient/noriskclient-launcher) (Tauri 2, React, Rust) |
| In-Game-Mod `salami-core` | [`mod/`](mod/) | Fabric, Java 21, Mojang-Mappings |
| Pack-Definitionen | [`packs/`](packs/) | JSON, im Launcher eingebettet |
| Logo & Branding | [`branding/`](branding/) | `generate_logo.py` (Pillow) |
| Plan & Roadmap | [`docs/PLAN.md`](docs/PLAN.md) | |

## Schnellstart

### Mod bauen und testen

```bash
cd mod
./gradlew build        # Jar nach mod/build/libs/
./gradlew runClient    # Minecraft 1.21.11 mit salami-core starten
./gradlew installDev   # Jar in den Salami-Launcher kopieren (Pack "salami")
```

Im Spiel:

- **Rechte Umschalttaste** öffnet die ClickGUI.
- **Ende** ist **Easy Leave**: Alle Kampfmodule gehen sofort aus und bleiben per Safe Mode gesperrt.

Beide Tasten lassen sich unter *Optionen → Steuerung → Salami Client* ändern.

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

Die Clip-Aufnahme (`norisk-capture`, FFmpeg) braucht zusätzlich **LLVM/libclang** (`winget install LLVM.LLVM`). Ohne LLVM startest du den Launcher ohne Clip-Funktion:

```bash
npx yarn@1.22.22 tauri dev --config src-tauri/tauri.dev-noclips.json
```

Beim ersten Start legt der Launcher den Datenordner `%APPDATA%\salamiclient\SalamiClient` an. Das Standardprofil „Salami Client 1.21.11“ lädt `salami-core` aus `dev/salami-core.jar` (vorher in `mod/` `./gradlew installDev` ausführen) sowie die Performance-Mods von Modrinth. Importierte `.noriskpack`-Profile laufen automatisch mit dem Salami-Pack.

### CurseForge-API-Key

CurseForge vergibt API-Keys pro Anwendung. Der Salami Client liefert deshalb keinen fremden Key mit. Den eigenen Key (kostenlos über die [CurseForge Console](https://console.curseforge.com)) beim Bauen als Umgebungsvariable setzen:

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
- **NoRisk-Online-Dienste sind aus** (`NORISK_SERVICES_ENABLED = false`):
  - Requests an `*.norisk.gg` blockiert das Backend zentral in `utils/http_client.rs`.
  - Im Frontend blockiert sie die CSP.
  - Es werden keine Login-Daten an NoRisk gemeldet.
- Packs und Standardprofile kommen aus `packs/*.json` statt von `api.norisk.gg`. Optional lassen sie sich remote laden (`PACKS_URL`).
- Der **Updater ist aus**, bis ein eigener Endpoint und ein eigener Signaturschlüssel existieren.
- Die internen Crate-Namen (`noriskclient_launcher_v3_lib`) bleiben bewusst unverändert. So bleiben Upstream-Updates per `git subtree pull` mergebar.

**Mod**

- Module (`module/Module`) haben typisierte Settings (`setting/*`) und werden als JSON gespeichert (`config/salami/config.json`, verzögert und atomar).
- Nur **aktive** Module bekommen Tick- und HUD-Aufrufe. Ein fehlerhaftes Modul wird abgeschaltet statt das Spiel abstürzen zu lassen.
- **Safe Mode / Easy Leave**: Die Kategorie *Utility* (Kampf-Automatisierung) wird hart deaktiviert:
  - per Taste,
  - manuell,
  - oder automatisch auf Servern einer editierbaren Liste.
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
./gradlew runClientGameTest
```

Der Test startet Minecraft mit einer Testwelt und prüft:

- ClickGUI per Taste,
- Klicks auf Module und Einstellungen,
- Easy Leave und die Safe-Mode-Sperre,
- den Salami-Look,
- Auto Totem, Double Hand Totem, Auto Crystal, Anchor Macro (inkl. Safe Anchor), Ghost Spear, Live Tracker, Breach Swap, Auto Mace, Auto Elytra, Speed Lunge und Kits in echten Testszenen,
- Screenshots aller Visuals und des HUD-Editors.

Screenshots: `mod/build/run/clientGameTest/screenshots/`.

## Module (Stand)

| Kategorie | Modul | Kurz |
|---|---|---|
| Client | Salami-Modus | kompletter Salami-Look (Launcher-Schalter wird übernommen) |
| Client | Safe Mode | sperrt alle Utility-Module; automatisch auf Servern der Liste |
| Visuals | Watermark · Info HUD · Armor HUD · Item Counter · Combo Counter · PvP-Logo | **Overlay-System**: alle frei verschiebbar im HUD-Editor (ClickGUI → „HUD bearbeiten“) |
| Visuals | Crosshair | eigenes Fadenkreuz: Kreuz, Punkt, Kreis, Salamischeibe; Farbe, Größe, Abstand, Dicke |
| Visuals | Block Outline | farbiger Umriss für den anvisierten Block |
| Visuals | Live Tracker | Rüstung (mit Haltbarkeit) und Hand-Items über dem Namen von Gegnern |
| Sonstiges | Kits | Inventar-Layouts für Crystal, Anchor, Mace, Spear, Sword, Axe, SMP, NethPot, UHC und 3 eigene; Taste K sortiert das Inventar |
| Utility | Auto Totem · Hover Totem · Double Hand Totem | Totems in Offhand/Hotbar |
| Utility | Auto Crystal | Kristall setzen/zerstören am Fadenkreuz, Selbstschutz-Grenze |
| Utility | Anchor Macro | Seelenanker laden/zünden; **Safe Anchor** setzt vorher Glowstone als Schutz |
| Utility | Breach Swap · Attribute Swap | im Schlag-Tick auf Bresche-Streitkolben bzw. andere Waffe wechseln, danach zurück |
| Utility | Auto Mace · Auto Elytra | Smash-Treffer im Fall; Elytra beim Fallen an, vor dem Boden/Treffer zurück zum Brustpanzer |
| Utility | Speed Lunge · Ghost Spear | automatische Ausfall-Stöße mit dem Speer; Drop → Offhand → Slot-Makro |
| Utility | Block ESP | Blöcke (CPvP, Kisten, Erze, Spawner) hervorheben, optional durch Wände |

Easy Leave (Taste Ende) schaltet alle Utility-Module sofort aus.

## Hinweis zu Utility-Modulen

Kampf-Automatisierung (Auto Totem, Auto Crystal, Anchors …) ist auf den meisten Servern verboten und führt dort zum Bann. Diese Module sind in der ClickGUI klar als *Utility* markiert. Gedacht sind sie für Singleplayer, eigene Server und Server, die so etwas ausdrücklich erlauben.

Der Salami Client enthält **keine** Funktionen, um Anti-Cheat oder Server-Moderation zu umgehen oder zu täuschen.

## Upstream-Updates des Launchers holen

```bash
git subtree pull --prefix=launcher https://github.com/NoRiskClient/noriskclient-launcher.git v3 --squash
python -I scripts/rebrand_translations.py
```

## Lizenz

GPL-3.0, siehe [LICENSE](LICENSE).

Der Launcher basiert auf dem NoRisk Launcher (GPL-3.0). Dieser basiert wiederum teilweise auf [LiquidLauncher](https://github.com/CCBlueX/LiquidLauncher).

„NoRisk“ und das NoRisk-Logo sind Marken ihrer Inhaber und werden im Salami Client nicht verwendet.
