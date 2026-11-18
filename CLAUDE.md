# Salami Client – Hinweise für Claude

## Versionsnummern bei Releases

Jedes Release (alles, was ins Auto-Update geht) erhöht die Launcher-Version um genau **0.0.1** gegenüber
der letzten veröffentlichten Version: 1.8.1 → 1.8.2 → 1.8.3 … Betas tragen die nächste Nummer mit
Vorab-Teil (z. B. 1.8.2-beta.1).

Keine Sprünge wie 1.9.0 oder 2.0.0 – außer der Nutzer sagt ausdrücklich, welche Version es werden soll.
`launcher/scripts/release.mjs` bricht bei größeren Sprüngen ab; `--allow-version-jump` nur auf seine
ausdrückliche Ansage benutzen.

Die Version steht an drei Stellen: `launcher/src-tauri/tauri.conf.json`, `Cargo.toml` (Zeile nach
`name = "salami-client-launcher"`, nicht die gleich lautende Version von log4rs) und `Cargo.lock`.
