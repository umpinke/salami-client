# Salami Client – Hinweise für Claude

## Neue Cosmetics: kein Release

Emotes, Hüte, Buddys und offizielle Capes sind Daten auf dem Cape-Server, kein Code in der Mod:

- Emotes: `server/capes/emotes.json`
- Hüte: `server/capes/hats.json` (Würfel mit Farbe; Modell und Symbol zeichnet das Spiel daraus)
- Buddys: `server/capes/buddies.json` (dasselbe Format ohne Platz) und zusätzlich als Schulter-Hut in `hats.json`
- Offizielle Capes: `server/capes/official-capes.json` und das Bild als `server/capes/official-capes/<id>.png`

Aufbau jeweils in `server/capes/README.md` („… hinzufügen“). Für etwas Neues nur diese Dateien ändern, committen
und der „salami“-Session zum Hochladen auf den Server geben – der Cape-Server lädt sie von selbst neu und alle
Spiele bekommen es ohne Launcher-Update. Der Nutzer will ausdrücklich kein Update pro Cosmetic. Die Mod bündelt
`emotes.json`, `hats.json` und `buddies.json` beim Bauen nur als Notfall-Kopie. Neue Arten von Cosmetics genauso bauen (Katalog
auf dem Server, `RemoteCatalog` in der Mod), nicht fest in den Code.

Hüte baut man am einfachsten in Blender: `scripts/hats/hats.blend` (Würfel an einer Figur, eine Sammlung
`hat:<id>` je Hut) und `scripts/hats/blender_hats.py`, das sie nach `server/capes/hats.json` schreibt. Über die
Blender-Anbindung geht das nur, wenn Blender läuft. Die id `wizard` ist in den Tests (Server und Gametest) der
Hut, den es nicht gibt – nie als echte id vergeben (der Zauberhut heißt deshalb `wizard_hat`).

Buddys (der Nutzer sagt „Buddys“, im Spiel heißen sie „…-Kumpel“) sind etwas anderes als Mini Me: Begleiter
aus Würfeln mit eigenem Katalog `server/capes/buddies.json`, eigenem Modul (`BuddyModule`) und denselben
Plätzen wie ein Mini Me (Hand, Schulter, Kopf, läuft hinterher – `FollowerSim` teilen sich beide). Jeder Buddy
steht zusätzlich als Schulter-Hut in `hats.json`, damit Spiele vor 1.8.5 ihn weiter sehen; neuere blenden
solche Hüte im Reiter Hüte aus. In Blender ist ein Buddy ein Schulter-Hut mit der Eigenschaft `buddy`
(`write_buddies_json`).

Emotes können mehr als Bewegung: Requisiten an einem Körperteil (`props`), ein Wurf (`throw`, nur Optik),
Geräusche (`sounds`) und eine Verwandlung (`costume`, der Spieler wird nicht gezeichnet, an seiner Stelle steht
eine Figur). Das alles sind Daten in `emotes.json` aus denselben Würfeln wie Hüte (`BoxModel`); die Modelle
stehen in `hats.blend` als Sammlungen `emote:<id>:<platz>` (`write_emote_models`).

Der Shop im Launcher (Reiter Hüte, Buddys, Emotes, Mini Me) zeigt dieselben Kataloge an der eigenen Figur: geholt über
`salami_cosmetics_catalog`, geprüft in `launcher/src/services/salami-cosmetics-service.ts`, gezeichnet in
`launcher/src/lib/skin3d/cosmetics.ts`. Auch er hat die Dateien als Notfall-Kopie. Kommt ein neues Feld
ins Hut-, Buddy- oder Emote-Format, muss es an drei Stellen gelesen werden: Server, Mod und Launcher.

## Launcher-Design

Der Launcher hat einen eigenen Look („Feinkost“: Salami-Rot, Fett-Creme, Pfefferbraun, Theken-Etiketten,
Salamischeibe als Motiv) statt des NoRisk-Aufbaus. Regeln und Bausteine stehen in
`launcher/src/themes/salami/DESIGN.md` – neue Seiten und Komponenten daran ausrichten, keine kalten
Grau-/Blauflächen und kein CSS-`lowercase` auf deutschen Texten.

## Versionsnummern bei Releases

Jedes Release (alles, was ins Auto-Update geht) erhöht die Launcher-Version um genau **0.0.1** gegenüber
der letzten veröffentlichten Version: 1.8.1 → 1.8.2 → 1.8.3 … Betas tragen die nächste Nummer mit
Vorab-Teil (z. B. 1.8.2-beta.1).

Keine Sprünge wie 1.9.0 oder 2.0.0 – außer der Nutzer sagt ausdrücklich, welche Version es werden soll.
`launcher/scripts/release.mjs` bricht bei größeren Sprüngen ab; `--allow-version-jump` nur auf seine
ausdrückliche Ansage benutzen.

Die Version steht an drei Stellen: `launcher/src-tauri/tauri.conf.json`, `Cargo.toml` (Zeile nach
`name = "salami-client-launcher"`, nicht die gleich lautende Version von log4rs) und `Cargo.lock`.
