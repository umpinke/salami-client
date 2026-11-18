# Salami Cape-Server

Sorgt dafür, dass alle mit dem Salami Client die Capes der anderen Salami-Spieler sehen. Eigene Capes müssen vorher von einem Admin auf der Prüf-Webseite freigegeben werden. Außerdem laufen hier [Freunde und Chat](#freunde-und-chat).

Adresse: **https://salami-client-zaehler.umpinke.de/capes** – derselbe nginx und dasselbe Zertifikat wie der Online-Zähler, nur unter dem Pfad `/capes/` (systemd-Dienst `salami-capes` auf `127.0.0.1:8788`). Die Prüf-Webseite liegt unter **https://salami-client-zaehler.umpinke.de/capes/admin/**.

## So funktioniert es

- **Mitgelieferte Capes** (Salami, Peperoni, Schneidebrett) sehen alle Salami-Spieler sofort.
- **Live**: Jedes laufende Spiel hält eine Verbindung zu `/v1/capes/events` (Server-Sent Events). Wechselt jemand sein Cape oder gibt ein Admin eines frei, sehen es alle Mitspieler sofort. Gesendet werden nur UUID und sichtbares Cape, alle 20 s kommt ein Lebenszeichen.
- **Emotes** (Winken, Tanzen …): Das Spiel meldet sich einmal an (`POST /v1/emotes/session`, wie beim Cape über Mojang) und schickt Emotes mit dem Token an `POST /v1/emotes`. Der Server gibt sie sofort über `/v1/capes/events` an alle weiter und speichert nichts. Das Token liegt nur im Speicher (12 h), nach einem Neustart meldet sich das Spiel neu an. Höchstens 20 Emotes pro Spieler und Minute (`EMOTES_PER_MINUTE`).
- **Neue Cosmetics ohne Update**: Welche Emotes, Hüte und offiziellen Capes es gibt, steht als Daten neben `server.mjs` – in [`emotes.json`](emotes.json), [`hats.json`](hats.json) und [`official-capes.json`](official-capes.json) (dazu die Cape-Bilder im Ordner `official-capes/`). Das Spiel holt die Listen beim Start (`GET /v1/emotes/catalog`, `/v1/hats/catalog`, `/v1/capes/catalog`). Der Server sieht alle 30 s nach, ob sich eine Datei geändert hat, lädt sie dann neu und meldet es allen offenen Spielen – ein neues Emote, ein neuer Hut oder ein neues Cape ist nach spätestens einer halben Minute bei allen, ohne Neustart und ohne Launcher-Update. Auch der Launcher holt die Hut- und die Emote-Liste: Sein Shop zeigt unter „Hüte“ und „Emotes“ alles, was es gibt, an der eigenen Figur. Siehe [Emotes hinzufügen](#emotes-hinzufügen), [Hüte hinzufügen](#hüte-hinzufügen) und [Offizielle Capes hinzufügen](#offizielle-capes-hinzufügen).
- **Eigene Capes** reicht der Launcher (ab 1.3.4) gleich nach dem Hinzufügen ein (`/v1/capes/submit`), ohne das getragene Cape zu ändern. Ältere Versionen schicken sie erst beim Spielen mit dem Cape (salami-core). Sie landen in der Warteschlange. Bis ein Admin sie freigibt, sieht sie nur der Spieler selbst. Lehnt ein Admin ab, bekommt der Spieler den Grund im Spiel und im Launcher angezeigt.
- **Anmeldung** wie beim Betreten eines Minecraft-Servers: Das Spiel meldet eine zufällige `serverId` bei Mojang an, der Cape-Server fragt bei Mojang nach (`hasJoined`). Das Zugangstoken des Spielers sieht der Cape-Server nie. So kann niemand ein Cape für einen fremden Account setzen.
- **Bilder** werden vollständig dekodiert und als schlichtes PNG neu gespeichert (keine Metadaten, nichts Kaputtes). Die Cape-ID ist ein Hash über die Pixel: dasselbe Bild muss nur einmal geprüft werden, egal wer es hochlädt.
- **Freigegebene Capes kann jeder tragen**: Der Launcher zeigt sie in der „Galerie“ (`/v1/capes/gallery`, ohne Namen der Einreicher) – vorn die offiziellen Capes mit Namen, dahinter die der Spieler. Ein Klick holt das Bild zu den eigenen Capes und zieht es an. Weil die Cape-ID nur an den Pixeln hängt, ist es sofort für alle sichtbar, ohne neue Prüfung und ohne das Tageslimit. Wird ein Cape wieder gesperrt, verschwindet es aus der Galerie und ist bei allen, die es tragen, wieder unsichtbar.
- **Ungeprüfte Capes, die niemand mehr trägt**, verschwinden automatisch aus der Warteschlange – außer sie wurden im Launcher eingereicht (Prüfseite: „niemand (im Launcher eingereicht)“).
- Gespeichert wird pro Spieler: UUID, Name, gewähltes Cape. Mitspieler-Abfragen (welche UUIDs auf deinem Server sind) werden nicht protokolliert.
- **Melden**: Ein freigegebenes Cape, das gegen die Cape-Regeln verstößt, kann jeder Salami-Spieler im Launcher in der „Galerie“ melden (`/v1/capes/report`, angemeldet wie beim Einreichen, eine Meldung pro Spieler und Cape; offizielle Capes lassen sich nicht melden). Ab 3 Meldungen verschiedener Spieler ist das Cape sofort für alle versteckt und wartet wieder auf Prüfung. Auf der Prüf-Webseite steht es unter „Gemeldet“.
- Grenzen: 10 neue Capes pro Spieler und Tag, höchstens 500 Capes in der Warteschlange, Anfragen pro IP begrenzt, nach 10 falschen Admin-Schlüsseln ist die IP 10 Minuten gesperrt.

Benötigt **Node.js 18 oder neuer**. Es gibt keine Abhängigkeiten und kein `npm install`.

## Freunde und Chat

Salami-Spieler können sich gegenseitig als Freunde hinzufügen, sehen, wer von ihren Freunden gerade spielt, und sich kurze Nachrichten schreiben – im Spiel (salami-core) und im Launcher (Reiter „Freunde“, dort auch mit Bildern und Clips). Der Code steckt in `friends.mjs`, die Daten in `friends.json` im Datenordner.

- **Anmeldung** wie bei den Capes: Challenge holen, bei Mojang anmelden (`joinServer`), dann `POST /v1/friends/session`. Der Server gibt ein zufälliges Token zurück, 30 Tage gültig. Pro Spieler gelten höchstens 5 Tokens (Spiel, Launcher, zweiter PC …), beim sechsten fällt das älteste weg. Gespeichert wird nur der SHA-256 des Tokens: ein Neustart meldet niemanden ab, und wer `friends.json` liest, kann sich damit nicht anmelden. Alle anderen Anfragen schicken `Authorization: Bearer <token>`. Fehlt es oder ist es abgelaufen, kommt `401 {"error":"nicht angemeldet"}` und der Client meldet sich einfach neu an.
- **Freundesanfragen** gehen per Minecraft-Name (Groß-/Kleinschreibung egal), aber nur an Spieler, die sich schon einmal mit dem Salami Client angemeldet haben. Fragen sich zwei gegenseitig an, sind sie sofort befreundet. Ablehnen und Zurückziehen ist dieselbe Anfrage (`deny`). Offene Anfragen verfallen nach 30 Tagen.
- **Online** ist, wer mindestens eine Live-Verbindung (`/v1/friends/events`) offen hat. Der Launcher verbindet sich mit `?client=launcher`, das Spiel ohne Angabe. Freunde sehen dazu `status`: `"game"` (im Spiel, hat Vorrang) oder `"launcher"`, offline `null`. Ändert sich das (Spiel startet, während der Launcher offen ist), kommt ein weiteres `presence`-Ereignis. Bricht die letzte ab, wartet der Server 10 s, bevor die Freunde „offline“ sehen – kurzes Neuverbinden flackert also nicht.
- **Nachrichten** gibt es nur zwischen Freunden, 1–256 Zeichen. Steuerzeichen und `§` (Minecraft-Farbcodes) werden entfernt, Leerzeichen am Anfang und Ende abgeschnitten. Jede Nachricht kommt sofort beim Empfänger an und auch auf allen Verbindungen des Absenders (zweiter Client). Ungelesene zählt der Server pro Freund mit, Abrufen des Verlaufs oder `read` setzt den Zähler zurück.
- **Bilder und Clips** (`POST /v1/friends/media?to=<uuid>&name=<Dateiname>`, Körper = die Datei, `Content-Type` = ihr Typ): PNG, JPEG, WebP bis 10 MB, MP4 und WebM bis 50 MB, nur an Freunde. Der Server prüft Typ und die ersten Bytes, schreibt die Datei nach `friends-media/` im Datenordner und legt eine Nachricht mit `media: { id, kind: "image"|"clip", mime, size, name }` an. Ihr `text` ist `[Bild] <Name>` bzw. `[Clip] <Name>` – das zeigen Clients, die keine Anhänge kennen. Abholen mit `GET /v1/friends/media/<id>` (Token nötig): nur die beiden, zwischen denen die Datei verschickt wurde, alle anderen bekommen 404. Die Datei verschwindet zusammen mit ihrer Nachricht (30 Tage, mehr als 100 Nachrichten, Freundschaft beendet). Jeder Spieler hat 500 MB für seine verschickten Dateien; ist das voll, laufen seine ältesten ab – die Nachricht bleibt und trägt `media.expired: true`. Höchstens 10 Dateien in 10 Minuten pro Spieler, alle Dateien zusammen höchstens 20 GB (danach `507`). Einstellbar: `FRIENDS_IMAGE_MB`, `FRIENDS_CLIP_MB`, `FRIENDS_MEDIA_QUOTA_MB`, `FRIENDS_MEDIA_TOTAL_MB`.
- **Grenzen**: 100 Freunde pro Spieler, 50 eigene offene Anfragen, 100 offene Anfragen an denselben Spieler, 8 Nachrichten in 10 Sekunden, 5 Live-Verbindungen pro Spieler, 240 Anfragen pro IP und Minute. Die Anmeldung zählt wie die Cape-Anmeldung zu `RATE_PER_MINUTE`, die Live-Verbindungen zusätzlich zu `MAX_STREAMS` und `MAX_STREAMS_PER_IP`.
- **Datenschutz**:
  - Nachrichten liegen **im Klartext** auf dem Server (`friends.json`), höchstens 30 Tage lang und höchstens die letzten 100 pro Gespräch. Beendet einer die Freundschaft, ist das Gespräch für beide weg.
  - Es gibt keine Admin-API und keine Webseite, um Nachrichten zu lesen, und sie landen nicht im Log. Wer Zugriff auf den Server selbst hat, könnte die Datei aber öffnen.
  - Wer online ist, sehen nur Freunde – auch nicht, wer einem nur eine Anfrage geschickt hat.
  - Gespeichert pro Spieler: UUID, letzter Name, letzte Anmeldung, Freundschaften (seit wann), offene Anfragen, Nachrichten, Ungelesen-Zähler und die Token-Hashes.
  - Bilder und Clips liegen wie die Nachrichten unverschlüsselt auf dem Server (`friends-media/`), höchstens 30 Tage. Abrufen können sie nur Absender und Empfänger. Geprüft oder freigegeben werden sie nicht – anders als Capes sieht sie nur der Freund, an den sie gehen.
- **nginx**: Der bestehende `location /capes/`-Block (ohne Puffern, 1 h Timeout) trägt auch `/capes/v1/friends/events`. Für Clips braucht er `client_max_body_size 55m;` (bisher 8m, sonst antwortet nginx bei größeren Clips mit 413) und am besten `proxy_request_buffering off;`, damit Uploads direkt durchgereicht werden.

Live-Ereignisse auf `/v1/friends/events` (Token in der `Authorization`-Kopfzeile), je eine Zeile `data: <json>`. Zeilen ohne `data: ` (`retry: 5000`, `: verbunden`, `: ping` alle 20 s) einfach überspringen.

| Ereignis | Wann |
|---|---|
| `{"type":"ready"}` | sofort nach dem Verbinden, immer das erste. Danach `GET /v1/friends` für den aktuellen Stand holen |
| `{"type":"presence","uuid","name","online":true\|false,"status":"game"\|"launcher"\|null}` | ein Freund kommt online, wechselt zwischen Launcher und Spiel oder geht offline (offline erst nach der Gnadenfrist) |
| `{"type":"message","message":{"id","from","to","text","at","media"?},"name":"<Absender>"}` | neue Nachricht – beim Empfänger und auf allen Verbindungen des Absenders, auch der, die sie geschickt hat (doppelte per `id` erkennen) |
| `{"type":"request","uuid","name"}` | neue Freundesanfrage an mich |
| `{"type":"friends","uuid","name","online","status"}` | Freundschaft beginnt – an beide, `uuid`/`name`/`online` sind jeweils der andere |
| `{"type":"removed","uuid"}` | der andere hat die Freundschaft beendet, meine Anfrage abgelehnt oder seine zurückgezogen |

## Einrichten (Linux-Server mit systemd)

1. **Dateien hochladen**. Auf deinem PC im Repo:

   ```bash
   scp -r server/capes DEIN_USER@DEIN_SERVER:/tmp/salami-capes
   ```

2. **Auf dem Server installieren** (den Benutzer `salami` gibt es schon vom Online-Zähler, sonst `sudo useradd --system --no-create-home --shell /usr/sbin/nologin salami`):

   ```bash
   sudo mkdir -p /opt/salami-capes
   sudo cp -r /tmp/salami-capes/*.mjs /tmp/salami-capes/emotes.json /tmp/salami-capes/hats.json /tmp/salami-capes/official-capes.json /tmp/salami-capes/official-capes /tmp/salami-capes/admin /opt/salami-capes/
   sudo cp /tmp/salami-capes/salami-capes.service /etc/systemd/system/
   ```

3. **Admin-Schlüssel anlegen**. Pro Admin ein Name und ein langes Geheimnis (mindestens 16 Zeichen), z. B. erzeugt mit `openssl rand -base64 24`:

   ```bash
   echo "ADMIN_TOKENS=leo:$(openssl rand -base64 24)" | sudo tee /etc/salami-capes.env
   sudo chmod 600 /etc/salami-capes.env
   sudo cat /etc/salami-capes.env
   ```

   Weitere Admins mit Komma anhängen: `ADMIN_TOKENS=leo:geheimnis1,max:geheimnis2`. Der Name steht später bei „Geprüft von“. Nach Änderungen `sudo systemctl restart salami-capes`.

4. **Starten**:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now salami-capes
   curl http://127.0.0.1:8788/health
   ```

5. **nginx**: im bestehenden `server`-Block von `salami-client-zaehler.umpinke.de` (der mit `listen 443 ssl` und dem Zertifikat) **zusätzlich** diesen `location`-Block eintragen. Der Schrägstrich am Ende von `proxy_pass` ist wichtig: er schneidet `/capes` ab.

   ```nginx
   location /capes/ {
       proxy_pass http://127.0.0.1:8788/;
       proxy_set_header Host $host;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       # nginx erlaubt sonst nur 1 MiB – Capes sind bis 4 MiB groß (Base64 ~5,4 MiB),
       # Clips im Freunde-Chat bis 50 MiB
       client_max_body_size 55m;
       # Uploads direkt durchreichen statt erst auf die Platte zu puffern
       proxy_request_buffering off;
       # Live-Verbindung (/v1/capes/events): nicht puffern, lange offen lassen
       proxy_buffering off;
       proxy_read_timeout 1h;
       proxy_http_version 1.1;
       proxy_set_header Connection "";
   }
   ```

   Dann `sudo nginx -t && sudo systemctl reload nginx` und prüfen: `curl https://salami-client-zaehler.umpinke.de/capes/health` muss `ok` liefern, `curl -N https://salami-client-zaehler.umpinke.de/capes/v1/capes/events` muss offen bleiben und `: verbunden` zeigen.

6. **Adresse in den Launcher eintragen**: `CAPE_SERVER_URL` in `launcher/src-tauri/src/branding.rs`. Der Launcher gibt sie beim Spielstart als `-Dsalami.capes.url` an salami-core weiter. Danach ein Update veröffentlichen.

Logs: `journalctl -u salami-capes -f`. Nach einem Update der Dateien: `sudo systemctl restart salami-capes`. Zum Dienst gehören alle `.mjs`-Dateien aus `server/capes` (`server.mjs`, `friends.mjs`, `png.mjs`, `catalog.mjs`, `emotes.mjs`, `hats.mjs`, `official.mjs`) – fehlt eine, startet er nicht. Dazu kommen die Kataloge `emotes.json`, `hats.json`, `official-capes.json` und der Ordner `official-capes/` (Schritt 2); ohne einen Katalog gibt es die Emotes, Hüte oder offiziellen Capes einfach nicht. Die Daten liegen in `/var/lib/salami-capes` (`capes.json`, `friends.json` und `images/`) – diesen Ordner ins Backup aufnehmen.

## Capes prüfen

`https://salami-client-zaehler.umpinke.de/capes/admin/` öffnen und mit dem Admin-Schlüssel anmelden. Pro Cape siehst du die Vorder- und Innenseite, die Elytra und die ganze Textur, wer es eingereicht hat und wer es gerade trägt.

- **Freigeben**: ab sofort für alle sichtbar – Mitspieler bekommen es live, der Spieler selbst eine Chat-Meldung.
- **Ablehnen**: optional mit Grund. Der Spieler sieht den Grund im Spiel und im Launcher.
- Unter „Freigegeben“ lassen sich Capes wieder **sperren**, unter „Abgelehnt“ **doch freigeben**.
- Unter **„Gemeldet“** stehen alle Capes mit offenen Meldungen: Grund, wer gemeldet hat, Notiz und Datum. **Behalten** verwirft die Meldungen (ein verstecktes Cape ist danach wieder für alle sichtbar, an seinem alten Platz in der Galerie), **Sperren** nimmt das Cape zurück. Beides wird im Log festgehalten (`journalctl -u salami-capes`).

Die Seite lässt sich als App aufs Handy legen (PWA: `manifest.webmanifest`, Service Worker `sw.js`, der nur die Oberfläche zwischenspeichert, nie `/v1/`). Der Admin-Schlüssel bleibt im Browser gespeichert. Die Glocke abonniert Web-Push für neue Capes. Das Backend dafür ist **nicht** Teil dieses Servers, sondern der Host-Dienst `salami-cape-mail` (nginx `/capes/push/`). Er prüft Admin-Schlüssel über `/v1/admin/me` und liest die Log-Zeile „neues Cape … wartet auf Prüfung“, also diese Zeile in `server.mjs` nicht umformulieren.

## Emotes hinzufügen

1. In `emotes.json` einen Eintrag ergänzen (am einfachsten ein ähnliches Emote kopieren). Aufbau:

   ```json
   {
     "id": "salute",
     "names": { "en_us": "Salute", "de_de": "Salutieren" },
     "duration": 2.2,
     "tracks": {
       "right_arm": { "keys": [{ "t": 0, "rot": [-135, 0, 30] }] }
     }
   }
   ```

   - `id`: a–z, 0–9, `_`, höchstens 24 Zeichen. `names`: Anzeigename je Sprache, `en_us` ist Pflicht.
   - `duration` in Sekunden (0.2–15) oder `"loop": true` – dann läuft das Emote, bis sich der Spieler bewegt (höchstens 15 s).
   - `tracks`: je Körperteil (`head`, `right_arm`, `left_arm`, `right_leg`, `left_leg`) Schlüsselbilder mit Zeit `t` (Sekunden, aufsteigend) und Drehung `rot` = `[x, y, z]` in Grad. Dazwischen wird weich überblendet (`"ease": "linear"` für gleichmäßig).
   - `cycle`: Die Schlüsselbilder wiederholen sich in diesem Takt (für Winken, Tanzen …). Ohne `cycle` laufen sie einmal und halten das letzte.
   - `base`: `none` (Winkel gelten direkt, Standard), `current` (zur normalen Haltung addiert – beim Kopf bleibt so die Blickrichtung) oder `head` (zur Blickrichtung addiert, z. B. der zeigende Arm).
   - `icon`: Zeitpunkt, dessen Haltung das Symbol im Menü zeigt. Das Symbol zeichnet das Spiel selbst, ein Bild ist nicht nötig.
   - Richtungen: x negativ = Arm nach vorn/oben, z positiv hebt den hängenden rechten Arm seitlich (links: negativ), y dreht den nach vorn gestreckten Arm zur Seite.
2. Datei auf den Server neben `server.mjs` legen. Der Server lädt sie von selbst (Log: `Emote-Katalog geladen: 15 Einträge`); fehlerhafte Einträge nennt er im Log und lässt sie weg, eine kaputte Datei ändert nichts.
3. Fertig – höchstens 64 Emotes. Mit dem nächsten Launcher-Release steckt die Liste zusätzlich als Notfall-Kopie in der Mod und im Launcher (für Starts ohne Verbindung zum Server); nötig ist das Release dafür nicht.

Entfernen geht genauso: Eintrag löschen, Datei hochladen.

## Hüte hinzufügen

Ein Hut ist ein kleines Modell aus farbigen Würfeln. Bild und Symbol braucht er nicht – das Spiel zeichnet beides aus den Daten.

1. In `hats.json` einen Eintrag ergänzen (am einfachsten einen ähnlichen Hut kopieren). Aufbau:

   ```json
   {
     "id": "witch",
     "names": { "en_us": "Witch hat", "de_de": "Hexenhut" },
     "parts": [
       { "color": "#4b2a9e", "boxes": [[-6, -8.75, -6, 12, 0.75, 12]] },
       {
         "color": "#5a35b8",
         "at": [0, -8.75, 0],
         "rot": [-6, 0, 5],
         "boxes": [[-3.5, -4, -3.5, 7, 4, 7]]
       }
     ]
   }
   ```

   - `id` und `names` wie bei den Emotes (`none` ist vergeben).
   - `parts`: Teile des Hutes. Jedes Teil hat eine Farbe (`color`, `#rrggbb`) und Würfel (`boxes`): `[x, y, z, Breite, Höhe, Tiefe]`.
   - Maße in Pixeln des Spielermodells, Nullpunkt mitten im Hals: Der Kopf reicht von x −4 bis 4, von y −8 (Scheitel) bis 0 und von z −4 (Gesicht) bis 4. **Oben ist negativ** – ein Hut beginnt also bei y = −8 und geht zu kleineren Werten.
   - `at` und `rot`: Drehpunkt und Drehung eines Teils (Grad, um x, y, z). Die Würfel zählen dann ab dem Drehpunkt – so lassen sich schiefe Spitzen, Hörner oder Ohren bauen.
   - `parts` in einem Teil: Unterteile, die an diesem Teil hängen und sich mitdrehen (höchstens 3 Ebenen).
   - `anchor`: `head` (Standard, sitzt auf dem Kopf, rutscht über einen Helm) oder `shoulder` (schwebt über der Schulter wie die Weihnachtsmütze; y = 0 ist dann die Unterkante).
   - Optional: `scale` (Größe, 0.1–3), `glow` (`true` = leuchtet im Dunkeln), `bob` (schwebt so viele Pixel auf und ab), `sway` (pendelt so viele Grad).
   - Grenzen: höchstens 64 Hüte, je Hut 64 Würfel und 32 Teile.

   Statt die Zahlen von Hand zu schreiben, kann man den Hut in Blender bauen: In [`scripts/hats/hats.blend`](../../scripts/hats/hats.blend) stehen alle Hüte als Würfel an einer Figur (1 Einheit = 1 Pixel). Jeder Hut ist eine Sammlung `hat:<id>`, die Farbe eines Würfels ist der Name seines Materials, ein Empty ist ein Drehpunkt. [`blender_hats.py`](../../scripts/hats/blender_hats.py) schreibt die Hüte der Szene in diese Datei (`write_hats_json`) – die Anleitung steht oben im Skript und in Blender im Text „Anleitung“.
2. Datei auf den Server neben `server.mjs` legen. Der Server lädt sie von selbst (Log: `Hut-Katalog geladen: 10 Einträge`); fehlerhafte Einträge nennt er im Log und lässt sie weg, eine kaputte Datei ändert nichts.
3. Fertig. Im Spiel steht der Hut unter Cosmetics → Hüte, andere Salami-Spieler sehen ihn, sobald ihn jemand trägt.

Entfernt man einen Hut aus der Datei, ist er bei allen weg, die ihn tragen.

## Offizielle Capes hinzufügen

Ein offizielles Cape ist ein Bild, das der Server selbst mitbringt. Es ist ohne Prüfung freigegeben, jeder kann es tragen.

1. Das Bild als `official-capes/<id>.png` neben `server.mjs` legen. Format wie bei eigenen Capes: PNG, Breite = 2 × Höhe, 64–2048 Pixel breit, höchstens 4 MiB. Die Vorlage dafür gibt es im Launcher (Capes → „Vorlage herunterladen“).
2. In `official-capes.json` den Eintrag ergänzen:

   ```json
   { "capes": [{ "id": "frost", "names": { "en_us": "Frost", "de_de": "Frost" } }] }
   ```

3. Fertig – höchstens 64. Der Server lädt beides von selbst (Log: `Cape-Katalog geladen: 1 Einträge`). Das Cape steht im Spiel unter Cosmetics → Capes mit dem Kennzeichen „Offiziell“ und im Launcher vorn in der Galerie. Ein Klick holt es zu den eigenen Capes und zieht es an; alle Salami-Spieler sehen es sofort.

Gut zu wissen:

- Ein neues Bild unter demselben Namen ersetzt das Cape im Menü. Wer das alte schon trägt, behält es (es bleibt freigegeben).
- Offizielle Capes lassen sich nicht melden und auf der Prüf-Webseite nicht sperren. Zum Entfernen den Eintrag aus `official-capes.json` nehmen: Das Cape verschwindet aus dem Menü, bleibt aber als gewöhnliches freigegebenes Cape bestehen, bis ein Admin es auf der Prüf-Webseite sperrt.
- Die mitgelieferten Salami-Capes (Salami, Aurora, Drache …) sind im Spiel gemalt und animiert; sie stehen fest in der Mod. Capes vom Server sind Bilder ohne Animation.

## Cape-Regeln

Diese Regeln zeigt der Launcher beim ersten eigenen Cape und auf der Cape-Seite. Daran richtet sich die Prüfung:

1. Nur Bilder, die du selbst gemacht hast oder benutzen darfst.
2. Keine fremden Logos, Marken oder Figuren (z. B. Firmen, Vereine, Anime, Filme, andere Creator).
3. Keine verbotenen Symbole (z. B. Hakenkreuz, SS-Runen) und nichts, was Hass gegen Menschen verbreitet.
4. Nichts Sexuelles und keine brutale Gewalt.
5. Keine echten Personen ohne deren Erlaubnis, keine Beleidigungen.
6. Keine Werbung, keine Server-Adressen oder Discord-Links.

Verstößt ein freigegebenes Cape doch dagegen und jemand meldet es: schnell prüfen und sperren. Wer als Rechteinhaber oder ohne Launcher etwas meldet (z. B. per Discord), das Cape unter „Freigegeben“ sperren.

## Einstellungen (Umgebungsvariablen)

| Variable | Standard | Bedeutung |
|---|---|---|
| `PORT` | `8788` | Port |
| `HOST` | `0.0.0.0` | Adresse, auf der gelauscht wird (`127.0.0.1` hinter einem Proxy) |
| `TRUST_PROXY` | `0` | `1`: echte IP aus `X-Forwarded-For` lesen (nur hinter einem Proxy!) |
| `DATA_DIR` | `./data` | Datenordner (`capes.json`, `images/`) |
| `ADMIN_TOKENS` | leer | `name:schlüssel,name2:schlüssel2` |
| `RATE_PER_MINUTE` | `30` | Anmeldungen/Cape-Änderungen pro IP und Minute |
| `LOOKUP_RATE_PER_MINUTE` | `120` | Mitspieler-Abfragen und Bilder pro IP und Minute |
| `UPLOADS_PER_DAY` | `10` | Neue eigene Capes pro Spieler und Tag |
| `MAX_PENDING` | `500` | Höchstens so viele Capes in der Warteschlange |
| `MAX_STREAMS` | `5000` | Höchstens so viele Live-Verbindungen insgesamt (Capes und Freunde zusammen) |
| `MAX_STREAMS_PER_IP` | `20` | Live-Verbindungen pro IP (Capes und Freunde zusammen, ein Spiel braucht meist zwei) |
| `REPORTS_TO_HIDE` | `3` | Ab so vielen Meldungen verschiedener Spieler ist ein Cape sofort versteckt |
| `REPORTS_PER_DAY` | `20` | Meldungen pro Spieler und Tag |
| `EMOTES_PER_MINUTE` | `20` | Emotes pro Spieler und Minute |
| `EMOTES_FILE` | `emotes.json` neben `server.mjs` | Emote-Katalog |
| `HATS_FILE` | `hats.json` neben `server.mjs` | Hut-Katalog |
| `OFFICIAL_CAPES_FILE` | `official-capes.json` neben `server.mjs` | Liste der offiziellen Capes |
| `OFFICIAL_CAPES_DIR` | `official-capes` neben der Liste | Ordner mit den Bildern (`<id>.png`) |
| `CATALOG_RELOAD_MS` | `30000` | So oft (ms) wird nachgesehen, ob sich ein Katalog geändert hat |
| `FRIENDS_RATE_PER_MINUTE` | `240` | Freunde- und Chat-Anfragen pro IP und Minute (die Anmeldung zählt zu `RATE_PER_MINUTE`) |
| `FRIENDS_OFFLINE_GRACE_MS` | `10000` | So lange (ms) nach der letzten Live-Verbindung gilt ein Spieler noch als online |

## Testen

```bash
node --test server/capes/*test.mjs
```

Startet den Server mit einem nachgebauten Mojang-Dienst und prüft Anmeldung, Warteschlange, Freigabe, Galerie, Meldungen, PNG-Prüfung und Admin-Sperre (`test.mjs`) sowie Freunde, Nachrichten und Live-Ereignisse (`friends.test.mjs`). Einen Ordner (`node --test server/capes/`) nimmt Node 22+ nicht mehr an, daher das Muster.

## API

| Anfrage | Antwort |
|---|---|
| `POST /v1/auth/challenge` | `{"serverId":"…"}`, 60 s gültig, nur einmal verwendbar |
| `PUT /v1/capes/me` mit `{"name","serverId","cape","hat"?,"minime"?}` | `{"status":"approved"\|"pending"\|"rejected","id"?,"reason"?}` – `cape`: `{"builtin":"salami"}`, `{"png":"<base64>"}` oder `null` – `hat`: ein Hut aus dem Katalog (`"santa"`, `"crown"` …, Cosmetics → Hüte) oder weglassen; unbekannte Hüte werden ignoriert – `minime`: `{"name":"<Minecraft-Name>","place":"hand"\|"shoulder"\|"head"\|"follow"}` (Cosmetics → Mini Me) oder weglassen; Ungültiges wird ignoriert |
| `POST /v1/capes/lookup` mit `{"uuids":[…]}` (max. 200) | `{"capes":{"<uuid>":{"builtin":"salami"}\|{"id":"<capeId>"}}}` – nur sichtbare Capes. Trägt der Spieler einen Hut, steht zusätzlich `"hat":"santa"` darin (auch ohne Cape: `{"hat":"santa"}`), ebenso sein Mini Me als `"minime":{"name","place"}` |
| `GET /v1/capes/img/<capeId>.png` | freigegebenes Cape-Bild |
| `GET /v1/capes/gallery` | `{"capes":[{"id","approvedAt","wearers","official"?,"names"?}]}` – alle freigegebenen Capes (höchstens 500): zuerst die offiziellen (`"official":true` und `names`), dann die eigenen der Spieler, neueste zuerst – für die Galerie im Launcher |
| `GET /v1/capes/catalog` | `{"version":"<16 Hex-Zeichen>","capes":[{"id","names","cape","width","height"}]}` – die offiziellen Capes aus `official-capes.json`; `cape` ist die Bild-ID für `/v1/capes/img/<cape>.png` |
| `GET /v1/hats/catalog` | `{"version":"<16 Hex-Zeichen>","hats":[…]}` – alle Hüte aus `hats.json`, geprüft und bereinigt |
| `GET /v1/capes/events` | Server-Sent Events: `data: {"uuid":"…","cape":{…}\|null}` bei jeder sichtbaren Änderung, `data: {"emote":{"uuid":"…","id":"wave"}}` bei jedem Emote (ohne `uuid` oben, damit ältere Spiele die Zeile überspringen), `: ping` alle 20 s |
| `POST /v1/emotes/session` mit `{"name","serverId"}` | `{"token","uuid","name"}` – Anmeldung wie bei `PUT /v1/capes/me`, Token 12 h gültig, nur im Speicher |
| `POST /v1/emotes` mit `{"emote":"wave"}` und `Authorization: Bearer <token>` | `{"ok":true}` – erlaubt sind die Emotes aus dem Katalog; unbekannte → 400, ohne gültiges Token → 401, mehr als 20 pro Minute → 429 |
| `GET /v1/emotes/catalog` | `{"version":"<16 Hex-Zeichen>","emotes":[…]}` – alle Emotes aus `emotes.json`, geprüft und bereinigt. Ändert sich einer der drei Kataloge, kommt auf `/v1/capes/events` zusätzlich `data: {"catalog":"<version>","kind":"emotes"\|"hats"\|"capes"}` |
| `POST /v1/capes/status` mit `{"ids":[…]}` (max. 100) | `{"status":{"<capeId>":{"status":"pending",…}}}` – für den Launcher |
| `POST /v1/capes/submit` mit `{"name","serverId","png"}` | `{"status":"approved"\|"pending"\|"rejected","id","reason"?}` – Launcher reicht ein eigenes Cape zur Prüfung ein, das getragene Cape bleibt |
| `POST /v1/capes/report` mit `{"name","serverId","id","reason","note"?}` | `{"status":"reported"\|"already_reported"}` – freigegebenes Cape melden; `reason`: `logo`, `hate`, `sexual`, `person` oder `other`, `note` höchstens 200 Zeichen |
| `GET /admin/` | Prüf-Webseite |
| `GET /v1/admin/capes?status=pending` | Liste für Admins (`Authorization: Bearer <schlüssel>`), `status`: `pending`, `approved`, `rejected` oder `reported` |
| `POST /v1/admin/capes/<capeId>/approve` \| `/reject` mit `{"reason"}` | Freigeben / Ablehnen |
| `GET /health` | `ok` |

Freunde und Chat: alle Antworten JSON, Fehler als `{"error":"…"}` (für Spieler lesbar). Außer `session` brauchen alle `Authorization: Bearer <token>`, sonst `401 {"error":"nicht angemeldet"}`. UUIDs mit Bindestrichen, klein geschrieben; Zeiten (`since`, `at`) als ISO-Text. Bei 429 `{"error":"zu viele Anfragen"}` (pro IP) kurz warten.

| Anfrage | Antwort |
|---|---|
| `POST /v1/friends/session` mit `{"name","serverId"}` | `{"token","uuid","name"}` – Anmeldung wie bei `PUT /v1/capes/me` (400/401/503 wie dort), Token 30 Tage gültig |
| `GET /v1/friends` | `{"me":{"uuid","name"},"friends":[{"uuid","name","online","since"}],"incoming":[{"uuid","name","at"}],"outgoing":[{"uuid","name","at"}],"unread":{"<uuid>":3}}` – Freunde online zuerst, dann nach Namen; Anfragen neueste zuerst; `unread` nur Freunde mit mindestens einer ungelesenen Nachricht |
| `POST /v1/friends/request` mit `{"name"}` | `{"status":"requested","uuid","name"}`, oder `{"status":"friends","uuid","name"}`, wenn der andere mich schon angefragt hatte. Nochmal anfragen: wieder `requested`. 400 Name ungültig oder man selbst, 404 `"<name> nutzt den Salami Client nicht oder war noch nie online"`, 409 schon befreundet oder Freundesliste voll, 429 zu viele offene Anfragen |
| `POST /v1/friends/accept` mit `{"uuid"}` | `{"status":"friends","uuid","name"}` – 404 ohne offene Anfrage von `uuid` (schon befreundet: auch 200), 409 Freundesliste voll |
| `POST /v1/friends/deny` mit `{"uuid"}` | `{"status":"none"}` – Anfrage von `uuid` ablehnen oder eigene an `uuid` zurückziehen, auch wenn es keine (mehr) gibt. Sind beide schon befreundet, ändert `deny` nichts und antwortet `{"status":"friends","uuid","name"}` – beenden geht nur mit `remove` |
| `POST /v1/friends/remove` mit `{"uuid"}` | `{"status":"none"}` – Freundschaft beenden, Gespräch und Ungelesen-Zähler für beide weg (auch offene Anfragen) |
| `GET /v1/friends/messages?with=<uuid>` | `{"messages":[{"id","from","to","text","at"}]}` – die letzten 100, älteste zuerst; setzt meine Ungelesenen von `uuid` zurück. 403 `"ihr seid keine Freunde"` |
| `POST /v1/friends/messages` mit `{"to","text"}` | `{"message":{"id","from","to","text","at"}}` – `id`: 12 Hex-Zeichen, eindeutig und aufsteigend. 400 leer oder zu lang, 403 keine Freunde, 429 `"zu schnell, kurz warten"` |
| `POST /v1/friends/read` mit `{"with"}` | `{"ok":true}` – meine Ungelesenen von `with` auf 0 |
| `GET /v1/friends/events` | Server-Sent Events, siehe [Freunde und Chat](#freunde-und-chat). 503 `"zu viele Verbindungen"` ab der sechsten pro Spieler oder über `MAX_STREAMS`/`MAX_STREAMS_PER_IP` |
