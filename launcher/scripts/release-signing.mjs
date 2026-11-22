// Signieren der Update-Dateien als eigener Schritt von release.mjs.
//
// Der Updater-Schlüssel und sein Passwort gehen nur an die Signier-Prozesse (`tauri signer sign`),
// nie an den Build: Cargo-Build-Skripte, Gradle, npm-Skripte und alles, was die herunterladen,
// sehen ihn so nicht. Das Passwort fragt der Signierschritt verdeckt im Terminal ab – oder nimmt
// TAURI_SIGNING_PRIVATE_KEY_PASSWORD, wenn es in der eigenen Shell gesetzt ist. Solange der
// Schlüssel kein Passwort hat, wird gar nicht gefragt.
import { spawnSync } from "node:child_process";
import { createHash, createPublicKey, randomBytes, verify } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/** Umgebungsvariablen mit Updater-Schlüssel oder -Passwort (Namen von Tauri v1 und v2). */
export const SIGNING_VARS = [
  "TAURI_SIGNING_PRIVATE_KEY",
  "TAURI_SIGNING_PRIVATE_KEY_PASSWORD",
  "TAURI_SIGNING_PRIVATE_KEY_PATH",
  "TAURI_PRIVATE_KEY",
  "TAURI_PRIVATE_KEY_PASSWORD",
  "TAURI_PRIVATE_KEY_PATH",
  "TAURI_KEY_PASSWORD",
];

/**
 * Nimmt Schlüssel und Passwort aus der Umgebung dieses Prozesses heraus, damit kein anderer
 * Kindprozess (Build, git, gh …) sie erbt. → die Werte, die gesetzt waren
 */
export function takeSigningVars(env = process.env) {
  const taken = {};
  for (const name of SIGNING_VARS) {
    if (env[name] !== undefined) {
      taken[name] = env[name];
      delete env[name];
    }
  }
  return taken;
}

/** Kopie der Umgebung ohne Schlüssel und Passwort. */
export function withoutSigningVars(env = process.env) {
  const copy = { ...env };
  for (const name of SIGNING_VARS) delete copy[name];
  return copy;
}

/**
 * Prüft eine Updater-Signatur (minisign, wie `tauri signer sign` sie schreibt) gegen den
 * öffentlichen Schlüssel aus tauri.conf.json – genau das, was installierte Launcher vor einem
 * Update tun. So geht nie ein Release raus, das sie ablehnen würden.
 */
export function verifyUpdateSignature(file, signature, pubkey) {
  const decode = (b64) => Buffer.from(b64, "base64").toString("utf8").trim().split(/\r?\n/);
  const keyBytes = Buffer.from(decode(pubkey)[1] ?? "", "base64");
  const [, sigLine = "", trustedLine = "", globalLine = ""] = decode(signature);
  const sigBytes = Buffer.from(sigLine, "base64");
  if (keyBytes.length !== 42 || sigBytes.length !== 74) return false;
  if (!sigBytes.subarray(2, 10).equals(keyBytes.subarray(2, 10))) return false;
  const key = createPublicKey({
    key: Buffer.concat([Buffer.from("302a300506032b6570032100", "hex"), keyBytes.subarray(10)]),
    format: "der",
    type: "spki",
  });
  const data = readFileSync(file);
  // „ED“ = vorab gehasht (BLAKE2b-512), „Ed“ = Datei direkt signiert
  const message = sigBytes.subarray(0, 2).toString() === "ED" ? createHash("blake2b512").update(data).digest() : data;
  const trusted = Buffer.from(trustedLine.replace(/^trusted comment: /, ""));
  return (
    verify(null, message, key, sigBytes.subarray(10)) &&
    verify(null, Buffer.concat([sigBytes.subarray(10), trusted]), key, Buffer.from(globalLine, "base64"))
  );
}

/** Fragt verdeckt im Terminal (keine Anzeige der Zeichen). Ohne Terminal: Fehler. */
export function askHidden(prompt, { input = process.stdin, output = process.stderr } = {}) {
  return new Promise((resolve, reject) => {
    if (!input.isTTY || typeof input.setRawMode !== "function") {
      reject(
        new Error(
          "Der Updater-Schlüssel braucht ein Passwort, aber hier gibt es kein Terminal für die Eingabe.\n" +
            "Im Terminal starten oder TAURI_SIGNING_PRIVATE_KEY_PASSWORD in der eigenen Shell setzen.",
        ),
      );
      return;
    }
    output.write(prompt);
    let value = "";
    const cleanup = () => {
      input.removeListener("data", onData);
      input.setRawMode(false);
      input.pause();
    };
    const onData = (chunk) => {
      for (const ch of chunk) {
        if (ch === "\r" || ch === "\n" || ch === "\u0004") {
          cleanup();
          output.write("\n");
          resolve(value);
          return;
        }
        if (ch === "\u0003") {
          // Strg+C
          cleanup();
          output.write("\n");
          reject(new Error("Abgebrochen."));
          return;
        }
        if (ch === "\u007f" || ch === "\b") value = [...value].slice(0, -1).join("");
        else if (ch >= " ") value += ch;
      }
    };
    input.setEncoding("utf8");
    input.setRawMode(true);
    input.resume();
    input.on("data", onData);
  });
}

/**
 * Ein `tauri signer sign <file>`: Schlüssel und Passwort nur in der Umgebung dieses einen Prozesses,
 * nie in einer Befehlszeile. Ohne Eingabe (stdin zu), damit er nie selbst nachfragt.
 */
function runSigner(cli, file, key, password) {
  rmSync(`${file}.sig`, { force: true });
  const result = spawnSync(process.execPath, [cli, "signer", "sign", file], {
    env: { ...withoutSigningVars(process.env), TAURI_PRIVATE_KEY: key, TAURI_PRIVATE_KEY_PASSWORD: password },
    stdio: ["ignore", "pipe", "pipe"],
    encoding: "utf8",
    timeout: 120_000,
    windowsHide: true,
  });
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}`.trim();
  if (result.status === 0 && existsSync(`${file}.sig`)) return { ok: true };
  return {
    ok: false,
    wrongPassword: /wrong password|incorrect updater private key password/i.test(output),
    message: output || result.error?.message || `Exit ${result.status}`,
  };
}

/** Schlüssel und Passwort an einer Probedatei prüfen: signieren und gegen den öffentlichen Schlüssel prüfen. */
function probe(cli, key, password, pubkey) {
  const dir = mkdtempSync(join(tmpdir(), "salami-sign-probe-"));
  try {
    const file = join(dir, "probe.bin");
    writeFileSync(file, randomBytes(64));
    const result = runSigner(cli, file, key, password);
    if (!result.ok) return result;
    if (!verifyUpdateSignature(file, readFileSync(`${file}.sig`, "utf8").trim(), pubkey)) {
      return {
        ok: false,
        message:
          "Der Updater-Schlüssel passt nicht zum öffentlichen Schlüssel in tauri.conf.json (plugins.updater.pubkey) –\n" +
          "installierte Launcher würden solche Updates ablehnen. Richtige Schlüsseldatei?",
      };
    }
    return { ok: true };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

/**
 * Schlüssel laden und das Passwort klären, an einer Probedatei geprüft (signieren und gegen
 * `pubkey` prüfen). → { key, password }
 *
 * Schlüssel: TAURI_SIGNING_PRIVATE_KEY aus der eigenen Shell, sonst `keyFile`.
 * Passwort:  TAURI_SIGNING_PRIVATE_KEY_PASSWORD aus der eigenen Shell (auch leer), sonst zuerst ohne
 *            Passwort versuchen und nur, wenn der Schlüssel eins hat, verdeckt fragen (`attempts` Versuche).
 * Fehler → Exception mit klarer Meldung.
 *
 * @param {object} o
 * @param {string} o.cli          Pfad zu @tauri-apps/cli/tauri.js
 * @param {string} o.keyFile      %USERPROFILE%\.salami-client\updater.key
 * @param {Record<string,string>} o.fromShell  Ergebnis von takeSigningVars()
 * @param {string} o.pubkey       plugins.updater.pubkey aus tauri.conf.json
 */
async function unlock({ cli, keyFile, fromShell, pubkey, ask = askHidden, attempts = 3, log = console.log }) {
  if (!existsSync(cli)) throw new Error(`Tauri-CLI nicht gefunden: ${cli} (yarn install im Ordner launcher)`);
  const key = fromShell.TAURI_SIGNING_PRIVATE_KEY?.trim() || (existsSync(keyFile) ? readFileSync(keyFile, "utf8").trim() : "");
  if (!key) throw new Error(`Signaturschlüssel fehlt: ${keyFile}`);

  let password;
  const shellPassword = fromShell.TAURI_SIGNING_PRIVATE_KEY_PASSWORD ?? fromShell.TAURI_PRIVATE_KEY_PASSWORD;
  if (shellPassword !== undefined) {
    const result = probe(cli, key, shellPassword, pubkey);
    if (!result.ok) {
      throw new Error(
        result.wrongPassword
          ? "Das Passwort aus TAURI_SIGNING_PRIVATE_KEY_PASSWORD passt nicht zum Updater-Schlüssel – nichts signiert."
          : `Signieren klappt nicht:\n${result.message}`,
      );
    }
    password = shellPassword;
  } else {
    const result = probe(cli, key, "", pubkey);
    if (result.ok) password = "";
    else if (!result.wrongPassword) throw new Error(`Signieren klappt nicht:\n${result.message}`);
    for (let attempt = 1; password === undefined; attempt++) {
      const typed = await ask("Passwort des Updater-Schlüssels (Eingabe bleibt unsichtbar): ");
      const check = probe(cli, key, typed, pubkey);
      if (check.ok) password = typed;
      else if (!check.wrongPassword) throw new Error(`Signieren klappt nicht:\n${check.message}`);
      else if (attempt >= attempts) throw new Error("Passwort des Updater-Schlüssels ist falsch – nichts signiert.");
      else log("Falsches Passwort, bitte nochmal.");
    }
  }
  return { key, password };
}

/** Nur prüfen, ob Schlüssel und Passwort zusammen eine gültige Signatur für `pubkey` ergeben. */
export async function checkSigning(options) {
  await unlock(options);
}

/**
 * Signiert alle `files` (→ <datei>.sig) mit dem Updater-Schlüssel. Vorher werden Schlüssel und
 * Passwort an einer Probedatei geprüft (siehe unlock) – bei einem Fehler ist nichts signiert.
 */
export async function signUpdateFiles({ files, ...options }) {
  const { cli, log = console.log } = options;
  const { key, password } = await unlock(options);
  for (const file of files) {
    const result = runSigner(cli, file, key, password);
    if (!result.ok) throw new Error(`Signieren fehlgeschlagen: ${file}\n${result.message}`);
    log(`Signiert: ${file}`);
  }
}
