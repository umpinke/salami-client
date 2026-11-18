# Salami Client – Hinweise für Claude

## Neue Cosmetics: kein Release

Emotes, Hüte und offizielle Capes sind Daten auf dem Cape-Server, kein Code in der Mod:

- Emotes: `server/capes/emotes.json`
- Hüte: `server/capes/hats.json` (Würfel mit Farbe; Modell und Symbol zeichnet das Spiel daraus)
- Offizielle Capes: `server/capes/official-capes.json` und das Bild als `server/capes/official-capes/<id>.png`

Aufbau jeweils in `server/capes/README.md` („… hinzufügen“). Für etwas Neues nur diese Dateien ändern, committen
und der „salami“-Session zum Hochladen auf den Server geben – der Cape-Server lädt sie von selbst neu und alle
Spiele bekommen es ohne Launcher-Update. Der Nutzer will ausdrücklich kein Update pro Cosmetic. Die Mod bündelt
`emotes.json` und `hats.json` beim Bauen nur als Notfall-Kopie. Neue Arten von Cosmetics genauso bauen (Katalog
auf dem Server, `RemoteCatalog` in der Mod), nicht fest in den Code.

## Versionsnummern bei Releases

Jedes Release (alles, was ins Auto-Update geht) erhöht die Launcher-Version um genau **0.0.1** gegenüber
der letzten veröffentlichten Version: 1.8.1 → 1.8.2 → 1.8.3 … Betas tragen die nächste Nummer mit
Vorab-Teil (z. B. 1.8.2-beta.1).

Keine Sprünge wie 1.9.0 oder 2.0.0 – außer der Nutzer sagt ausdrücklich, welche Version es werden soll.
`launcher/scripts/release.mjs` bricht bei größeren Sprüngen ab; `--allow-version-jump` nur auf seine
ausdrückliche Ansage benutzen.

Die Version steht an drei Stellen: `launcher/src-tauri/tauri.conf.json`, `Cargo.toml` (Zeile nach
`name = "salami-client-launcher"`, nicht die gleich lautende Version von log4rs) und `Cargo.lock`.
