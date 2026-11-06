# Salami Client Launcher

Der Launcher des Salami Client (Tauri 2, React, Rust). Wie man ihn baut und veröffentlicht, steht im [README im Hauptordner](../README.md).

## Fehler melden

Fehler und Wünsche bitte als [Issue auf GitHub](https://github.com/umpinke/salami-client/issues) oder im Discord melden.

## Selbst bauen

Voraussetzungen: Node.js 18 oder neuer, Rust (stable), Yarn 1 und für die Clip-Aufnahme LLVM.

```bash
yarn install
yarn tauri dev     # Entwicklung
yarn tauri build   # Installer
```

## Lizenz und Herkunft

Der Launcher ist ein Fork des NoRisk Client Launchers, der wiederum teilweise auf [LiquidLauncher](https://github.com/CCBlueX/LiquidLauncher) basiert. Beide stehen unter der [GNU General Public License v3.0](LICENSE), deshalb gilt sie auch für diesen Launcher:

- Der Quellcode ist öffentlich: https://github.com/umpinke/salami-client
- Wer ihn weiterverwendet, muss seinen eigenen Quellcode ebenfalls unter der GPL veröffentlichen.

Der Salami Client nutzt keine Dienste, Server, Logos oder Pakete von NoRisk. „NoRisk“ ist eine Marke ihrer Inhaber.
