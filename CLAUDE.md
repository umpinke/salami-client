# Salami Client – Hinweise für Claude

## Neue Emotes: kein Release

Emotes sind Daten in `server/capes/emotes.json` (Aufbau: `server/capes/README.md`, „Emotes hinzufügen“). Für ein
neues Emote nur diese Datei ändern, committen und der „salami“-Session zum Hochladen auf den Server geben – der
Cape-Server lädt sie von selbst neu und alle Spiele bekommen das Emote ohne Launcher-Update. Der Nutzer will
ausdrücklich kein Update pro Emote. Die Mod bündelt dieselbe Datei beim Bauen nur als Notfall-Kopie.

## Versionsnummern bei Releases

Jedes Release (alles, was ins Auto-Update geht) erhöht die Launcher-Version um genau **0.0.1** gegenüber
der letzten veröffentlichten Version: 1.8.1 → 1.8.2 → 1.8.3 … Betas tragen die nächste Nummer mit
Vorab-Teil (z. B. 1.8.2-beta.1).

Keine Sprünge wie 1.9.0 oder 2.0.0 – außer der Nutzer sagt ausdrücklich, welche Version es werden soll.
`launcher/scripts/release.mjs` bricht bei größeren Sprüngen ab; `--allow-version-jump` nur auf seine
ausdrückliche Ansage benutzen.

Die Version steht an drei Stellen: `launcher/src-tauri/tauri.conf.json`, `Cargo.toml` (Zeile nach
`name = "salami-client-launcher"`, nicht die gleich lautende Version von log4rs) und `Cargo.lock`.
