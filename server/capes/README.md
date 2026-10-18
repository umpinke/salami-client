# Salami Cape-Server

Sorgt dafür, dass alle mit dem Salami Client die Capes der anderen Salami-Spieler sehen. Eigene Capes müssen vorher von einem Admin auf der Prüf-Webseite freigegeben werden.

Geplante Adresse: **https://salami-client-capes.umpinke.de** (systemd-Dienst `salami-capes` auf `127.0.0.1:8788`, davor nginx mit Let's Encrypt). Die Prüf-Webseite liegt unter `/admin/`.

## So funktioniert es

- **Mitgelieferte Capes** (Salami, Peperoni, Schneidebrett) sehen alle Salami-Spieler sofort.
- **Eigene Capes** schickt salami-core beim Spielen an den Server. Sie landen in der Warteschlange. Bis ein Admin sie freigibt, sieht sie nur der Spieler selbst. Lehnt ein Admin ab, bekommt der Spieler den Grund im Spiel angezeigt.
- **Anmeldung** wie beim Betreten eines Minecraft-Servers: Das Spiel meldet eine zufällige `serverId` bei Mojang an, der Cape-Server fragt bei Mojang nach (`hasJoined`). Das Zugangstoken des Spielers sieht der Cape-Server nie. So kann niemand ein Cape für einen fremden Account setzen.
- **Bilder** werden vollständig dekodiert und als schlichtes PNG neu gespeichert (keine Metadaten, nichts Kaputtes). Die Cape-ID ist ein Hash über die Pixel: dasselbe Bild muss nur einmal geprüft werden, egal wer es hochlädt.
- **Ungeprüfte Capes, die niemand mehr trägt**, verschwinden automatisch aus der Warteschlange.
- Gespeichert wird pro Spieler: UUID, Name, gewähltes Cape. Mitspieler-Abfragen (welche UUIDs auf deinem Server sind) werden nicht protokolliert.
- Grenzen: 10 neue Capes pro Spieler und Tag, höchstens 500 Capes in der Warteschlange, Anfragen pro IP begrenzt, nach 10 falschen Admin-Schlüsseln ist die IP 10 Minuten gesperrt.

Benötigt **Node.js 18 oder neuer**. Es gibt keine Abhängigkeiten und kein `npm install`.

## Einrichten (Linux-Server mit systemd)

1. **Dateien hochladen**. Auf deinem PC im Repo:

   ```bash
   scp -r server/capes DEIN_USER@DEIN_SERVER:/tmp/salami-capes
   ```

2. **Auf dem Server installieren** (den Benutzer `salami` gibt es schon vom Online-Zähler, sonst `sudo useradd --system --no-create-home --shell /usr/sbin/nologin salami`):

   ```bash
   sudo mkdir -p /opt/salami-capes
   sudo cp -r /tmp/salami-capes/server.mjs /tmp/salami-capes/png.mjs /tmp/salami-capes/admin /opt/salami-capes/
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

5. **nginx** (Subdomain per DNS auf den Server zeigen lassen, Zertifikat mit `certbot --nginx -d salami-client-capes.umpinke.de`):

   ```nginx
   server {
       server_name salami-client-capes.umpinke.de;
       # nginx erlaubt sonst nur 1 MiB – Capes sind bis 4 MiB groß (Base64 ~5,4 MiB)
       client_max_body_size 8m;
       location / {
           proxy_pass http://127.0.0.1:8788;
           proxy_set_header Host $host;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       }
   }
   ```

6. **Adresse in den Launcher eintragen**: `CAPE_SERVER_URL` in `launcher/src-tauri/src/branding.rs`. Der Launcher gibt sie beim Spielstart als `-Dsalami.capes.url` an salami-core weiter. Danach ein Update veröffentlichen.

Logs: `journalctl -u salami-capes -f`. Nach einem Update der Dateien: `sudo systemctl restart salami-capes`. Die Daten liegen in `/var/lib/salami-capes` (`capes.json` und `images/`) – diesen Ordner ins Backup aufnehmen.

## Capes prüfen

`https://salami-client-capes.umpinke.de/admin/` öffnen und mit dem Admin-Schlüssel anmelden. Pro Cape siehst du die Vorder- und Innenseite, die Elytra und die ganze Textur, wer es eingereicht hat und wer es gerade trägt.

- **Freigeben**: ab sofort für alle sichtbar (Mitspieler laden es spätestens nach 10 Minuten neu).
- **Ablehnen**: optional mit Grund. Der Spieler sieht den Grund im Spiel und im Launcher.
- Unter „Freigegeben“ lassen sich Capes wieder **sperren**, unter „Abgelehnt“ **doch freigeben**.

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

## Testen

```bash
node --test server/capes/test.mjs
```

Startet den Server mit einem nachgebauten Mojang-Dienst und prüft Anmeldung, Warteschlange, Freigabe, PNG-Prüfung und Admin-Sperre.

## API

| Anfrage | Antwort |
|---|---|
| `POST /v1/auth/challenge` | `{"serverId":"…"}`, 60 s gültig, nur einmal verwendbar |
| `PUT /v1/capes/me` mit `{"name","serverId","cape"}` | `{"status":"approved"\|"pending"\|"rejected","id"?,"reason"?}` – `cape`: `{"builtin":"salami"}`, `{"png":"<base64>"}` oder `null` |
| `POST /v1/capes/lookup` mit `{"uuids":[…]}` (max. 200) | `{"capes":{"<uuid>":{"builtin":"salami"}\|{"id":"<capeId>"}}}` – nur sichtbare Capes |
| `GET /v1/capes/img/<capeId>.png` | freigegebenes Cape-Bild |
| `POST /v1/capes/status` mit `{"ids":[…]}` (max. 100) | `{"status":{"<capeId>":{"status":"pending",…}}}` – für den Launcher |
| `GET /admin/` | Prüf-Webseite |
| `GET /v1/admin/capes?status=pending` | Liste für Admins (`Authorization: Bearer <schlüssel>`) |
| `POST /v1/admin/capes/<capeId>/approve` \| `/reject` mit `{"reason"}` | Freigeben / Ablehnen |
| `GET /health` | `ok` |
