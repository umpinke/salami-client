# Salami Online-Zähler

Zählt, wie viele Salami-Launcher gerade offen sind. Die Zahl steht im Launcher oben rechts im Spielen-Tab.

Läuft unter **https://salami-client-zaehler.umpinke.de**: systemd-Dienst `salami-online` auf `127.0.0.1:8787`, davor nginx mit Let's Encrypt.

- Der Launcher schickt alle 30 s ein Lebenszeichen mit einer **zufälligen ID**. Die ID wird bei jedem Start neu erzeugt. Name, Account und andere Daten werden nicht gesendet.
- Wer sich 90 s nicht meldet, gilt als offline. Beim Schließen meldet sich der Launcher sofort ab.
- Alles liegt nur im Arbeitsspeicher. Es gibt keine Datenbank und kein Log der IDs.
- Pro IP zählen höchstens 10 Launcher, und pro IP sind höchstens 30 Anfragen pro Minute erlaubt. So kann niemand die Zahl künstlich hochtreiben.

Benötigt **Node.js 18 oder neuer**. Es gibt keine Abhängigkeiten und kein `npm install`.

## Einrichten (Linux-Server mit systemd)

1. **Node prüfen** (sonst installieren, z. B. `sudo apt install nodejs`, mindestens Version 18):

   ```bash
   node --version
   ```

2. **Dateien hochladen**. Auf deinem PC im Repo:

   ```bash
   scp server/online-counter/server.mjs server/online-counter/salami-online.service DEIN_USER@DEIN_SERVER:/tmp/
   ```

3. **Auf dem Server installieren**:

   ```bash
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin salami
   sudo mkdir -p /opt/salami-online
   sudo mv /tmp/server.mjs /opt/salami-online/
   sudo mv /tmp/salami-online.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now salami-online
   ```

   Liegt Node nicht unter `/usr/bin/node` (prüfen mit `which node`), passe `ExecStart` in der Service-Datei an.

4. **Port freigeben** (bei `ufw`, sonst entsprechend in deiner Firewall bzw. beim Hoster):

   ```bash
   sudo ufw allow 8787/tcp
   ```

5. **Testen**, auf dem Server und dann von deinem PC aus:

   ```bash
   curl http://DEIN_SERVER:8787/v1/online
   ```

   Die Antwort muss `{"online":0}` lauten.

6. **Adresse in den Launcher eintragen**: In `launcher/src-tauri/src/branding.rs` steht `ONLINE_COUNTER_URL`:

   ```rust
   pub const ONLINE_COUNTER_URL: Option<&str> = Some("http://DEIN_SERVER:8787");
   ```

   Danach den Launcher neu bauen bzw. ein Update veröffentlichen.

Logs ansehen: `journalctl -u salami-online -f`. Nach einem Update von `server.mjs`: `sudo systemctl restart salami-online`.

## Mit Domain und HTTPS (empfohlen)

Hast du eine Domain, stell den Zähler hinter einen Reverse-Proxy. Mit **Caddy** holt der Proxy das Zertifikat selbst:

```
online.deine-domain.de {
    reverse_proxy 127.0.0.1:8787
}
```

In der Service-Datei dann `HOST=127.0.0.1` und `TRUST_PROXY=1` setzen. So erkennt der Zähler die echte IP für die Obergrenze pro IP. Port 8787 muss dann **nicht** in der Firewall offen sein. Im Launcher steht anschließend `Some("https://online.deine-domain.de")`.

Bei nginx genauso: `proxy_pass http://127.0.0.1:8787;` und `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`.

## Ohne systemd (Windows-Server, Docker, zum Ausprobieren)

```bash
node server.mjs
```

Unter Windows bleibt der Dienst z. B. mit [NSSM](https://nssm.cc/) oder der Aufgabenplanung dauerhaft aktiv.

## Einstellungen (Umgebungsvariablen)

| Variable | Standard | Bedeutung |
|---|---|---|
| `PORT` | `8787` | Port |
| `HOST` | `0.0.0.0` | Adresse, auf der gelauscht wird (`127.0.0.1` hinter einem Proxy) |
| `TRUST_PROXY` | `0` | `1`: echte IP aus `X-Forwarded-For` lesen (nur hinter einem Proxy!) |
| `TTL_SECONDS` | `90` | Ab wann ein stiller Launcher als offline gilt |
| `MAX_PER_IP` | `10` | Höchstens so viele gezählte Launcher pro IP |
| `RATE_PER_MINUTE` | `30` | Anfragen pro IP und Minute, danach `429` |
| `MAX_SESSIONS` | `100000` | Obergrenze insgesamt (Schutz des Arbeitsspeichers) |

## API

| Anfrage | Antwort |
|---|---|
| `POST /v1/heartbeat` mit `{"id":"<uuid>"}` | `{"online":42}` |
| `POST /v1/leave` mit `{"id":"<uuid>"}` | `{"online":41}` |
| `GET /v1/online` | `{"online":41}`, mit offenem CORS, z. B. für die Website |
| `GET /health` | `ok` |
