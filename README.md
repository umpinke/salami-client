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
