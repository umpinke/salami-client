// Salami Capes – Prüf-Webseite für Admins. Spricht nur mit diesem Server (../v1/…).
"use strict";

const API = new URL("../v1/", location.href);
const TOKEN_KEY = "salami-capes-admin";
const REFRESH_MS = 30_000;
/** Ausschnitte im 64x32-Raster (wie launcher/src/components/salami-capes/cape-layout.ts). */
const PARTS = {
  outside: [1, 1, 10, 16],
  inside: [12, 1, 10, 16],
  elytra: [36, 2, 10, 20],
};
const PREVIEW_SCALE = 6;
const HINTS = {
  pending: "Diese Capes sieht bisher nur der Spieler selbst. Freigegebene Capes sehen alle Salami-Spieler.",
  approved: "Für alle sichtbar. „Sperren“ nimmt ein Cape wieder zurück.",
  rejected: "Abgelehnte Capes sieht nur der Spieler selbst. Er bekommt den Grund im Spiel angezeigt.",
  reported:
    "Von Spielern gemeldete Capes. Ab 3 Meldungen ist ein Cape sofort für alle versteckt, bis du entscheidest. „Behalten“ verwirft die Meldungen, „Sperren“ nimmt das Cape zurück.",
};
const EMPTY = {
  pending: "Alles geprüft – keine Capes in der Warteschlange.",
  approved: "Noch keine freigegebenen Capes.",
  rejected: "Keine abgelehnten Capes.",
  reported: "Keine offenen Meldungen.",
};
/** Gründe wie REPORT_REASONS in server.mjs. */
const REPORT_REASONS = {
  logo: "Fremdes Logo, Marke oder Figur",
  hate: "Verbotene Symbole oder Hass",
  sexual: "Sexuelles oder brutale Gewalt",
  person: "Echte Person oder Beleidigung",
  other: "Etwas anderes",
};

const $ = (id) => document.getElementById(id);
let token = readToken();
let status = "pending";
let refreshTimer = null;
let loading = false;

// Angemeldet bleiben (auch in der Handy-App), bis man sich abmeldet.
function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

function storeToken(value) {
  token = value;
  try {
    sessionStorage.removeItem(TOKEN_KEY);
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {}
}

class AuthError extends Error {}

async function api(path, options = {}) {
  const response = await fetch(new URL(path, API), {
    method: options.method || "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: "no-store",
  });
  if (response.status === 401) throw new AuthError((await response.json().catch(() => ({}))).error || "nicht angemeldet");
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))).error;
    throw new Error(error || `Fehler ${response.status}`);
  }
  return options.raw ? response : response.json();
}

// ------------------------------------------------------------------ Anmeldung

function showLogin(message = "") {
  stopRefresh();
  $("review").hidden = true;
  $("who").hidden = true;
  $("login").hidden = false;
  $("login-error").textContent = message;
  $("token").focus();
}

async function start() {
  if (!token) return showLogin();
  try {
    const me = await api("admin/me");
    $("admin-name").textContent = me.name;
    $("who").hidden = false;
    setupPush();
    $("login").hidden = true;
    $("review").hidden = false;
    selectTab(status);
  } catch (e) {
    storeToken("");
    showLogin(e instanceof AuthError ? "Falscher Admin-Schlüssel." : e.message);
  }
}

$("login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  storeToken($("token").value.trim());
  $("token").value = "";
  start();
});

$("logout").addEventListener("click", () => {
  storeToken("");
  $("grid").replaceChildren();
  showLogin();
});

// ------------------------------------------------------------------ Liste

function stopRefresh() {
  clearInterval(refreshTimer);
  refreshTimer = null;
}

function selectTab(next) {
  status = next;
  for (const tab of document.querySelectorAll(".tabs button")) {
    tab.setAttribute("aria-selected", String(tab.dataset.status === status));
  }
  $("hint").textContent = HINTS[status];
  $("grid").replaceChildren();
  stopRefresh();
  load();
  refreshTimer = setInterval(() => {
    // Nicht neu laden, während jemand gerade einen Ablehnungsgrund tippt.
    if (!document.querySelector(".reject-form:not([hidden])")) load();
  }, REFRESH_MS);
}

for (const tab of document.querySelectorAll(".tabs button")) {
  tab.addEventListener("click", () => selectTab(tab.dataset.status));
}

async function load() {
  if (loading) return;
  loading = true;
  $("refresh").classList.add("spin");
  try {
    const data = await api(`admin/capes?status=${status}`);
    $("list-error").textContent = "";
    for (const [key, value] of Object.entries(data.counts)) {
      const badge = document.querySelector(`[data-count="${key}"]`);
      badge.textContent = String(value);
      badge.toggleAttribute("data-zero", value === 0);
    }
    // Von Meldungen versteckte Capes stehen in beiden Zahlen – getrennt zeigen statt addieren
    const open = [
      data.counts.pending > 0 ? String(data.counts.pending) : "",
      data.counts.reported > 0 ? `${data.counts.reported} gemeldet` : "",
    ].filter(Boolean);
    setBadge(data.counts.pending + data.counts.reported);
    document.title = open.length > 0 ? `(${open.join(", ")}) Salami Capes – Prüfung` : "Salami Capes – Prüfung";
    render(data.capes);
  } catch (e) {
    if (e instanceof AuthError) {
      storeToken("");
      return showLogin("Bitte neu anmelden.");
    }
    $("list-error").textContent = `Liste konnte nicht geladen werden: ${e.message}`;
  } finally {
    loading = false;
    $("refresh").classList.remove("spin");
  }
}

/** Karten nur neu bauen, wenn sich die Liste geändert hat (sonst flackern die Bilder). */
function render(capes) {
  const grid = $("grid");
  const existing = new Map([...grid.children].map((card) => [card.dataset.key, card]));
  const cards = capes.map((cape) => {
    const reports = (cape.reports || []).length;
    const key = `${cape.id}:${cape.status}:${cape.reviewedAt || ""}:${reports}:${cape.users.map((u) => u.uuid).join(",")}`;
    return existing.get(key) || buildCard(cape, key);
  });
  grid.replaceChildren(...cards);
  $("empty").hidden = cards.length > 0;
  $("empty").textContent = EMPTY[status];
}

const dateFormat = new Intl.DateTimeFormat("de-DE", { dateStyle: "medium", timeStyle: "short" });
const formatDate = (iso) => (iso ? dateFormat.format(new Date(iso)) : "–");

function playerLink(name, uuid) {
  const link = document.createElement("a");
  link.href = `https://namemc.com/profile/${encodeURIComponent(uuid)}`;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = name;
  link.title = uuid;
  return link;
}

function buildCard(cape, key) {
  const card = $("card-template").content.firstElementChild.cloneNode(true);
  card.dataset.key = key;
  card.querySelector(".submitter").append(playerLink(cape.submittedName, cape.submittedBy));
  card.querySelector(".submitted").textContent = formatDate(cape.submittedAt);

  const users = card.querySelector(".users");
  if (cape.users.length === 0) users.textContent = cape.viaLauncher ? "niemand (im Launcher eingereicht)" : "niemand";
  cape.users.slice(0, 10).forEach((user, i) => {
    if (i > 0) users.append(", ");
    users.append(playerLink(user.name, user.uuid));
  });
  if (cape.users.length > 10) users.append(` und ${cape.users.length - 10} weitere`);

  const reviewed = cape.reviewedAt ? `${formatDate(cape.reviewedAt)} von ${cape.reviewedBy}` : "";
  card.querySelector(".reviewed").textContent = reviewed;
  card.querySelector(".reviewed-label").hidden = card.querySelector(".reviewed").hidden = !reviewed;
  card.querySelector(".reason").textContent = cape.reason || "";
  card.querySelector(".reason-label").hidden = card.querySelector(".reason").hidden = !cape.reason;

  const reports = cape.reports || [];
  card.classList.toggle("reported", reports.length > 0);
  if (reports.length > 0) {
    const box = card.querySelector(".reports");
    box.hidden = false;
    box.querySelector(".reports-title").textContent = cape.hiddenByReports
      ? `${reports.length} Meldungen – für alle versteckt seit ${formatDate(cape.hiddenByReports)}`
      : `${reports.length} ${reports.length === 1 ? "Meldung" : "Meldungen"} – noch für alle sichtbar`;
    const list = box.querySelector("ul");
    for (const report of reports) {
      const item = document.createElement("li");
      item.append(`${REPORT_REASONS[report.reason] || report.reason} – `, playerLink(report.name, report.by), `, ${formatDate(report.at)}`);
      if (report.note) {
        const note = document.createElement("div");
        note.className = "note";
        note.textContent = `„${report.note}“`;
        item.append(note);
      }
      list.append(item);
    }
  }

  const approve = card.querySelector(".approve");
  const reject = card.querySelector(".reject");
  const form = card.querySelector(".reject-form");
  // Freigegeben oder von Meldungen versteckt: Admin behält es (Meldungen weg) oder sperrt es
  const wasApproved = cape.status === "approved" || Boolean(cape.hiddenByReports);
  approve.hidden = cape.status === "approved" && reports.length === 0;
  reject.hidden = cape.status === "rejected";
  if (cape.status === "rejected") approve.textContent = "Doch freigeben";
  if (wasApproved) {
    approve.textContent = "Behalten";
    reject.textContent = "Sperren";
  }

  approve.addEventListener("click", () => review(card, cape.id, "approve"));
  reject.addEventListener("click", () => {
    form.hidden = false;
    card.querySelector(".actions").hidden = true;
    form.querySelector("input").focus();
  });
  form.querySelector(".cancel").addEventListener("click", () => {
    form.hidden = true;
    card.querySelector(".actions").hidden = false;
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    review(card, cape.id, "reject", form.querySelector("input").value.trim());
  });

  drawPreviews(card, cape.id);
  return card;
}

async function review(card, id, action, reason) {
  card.classList.add("busy");
  try {
    await api(`admin/capes/${id}/${action}`, { method: "POST", body: action === "reject" ? { reason } : {} });
    card.remove();
    await load();
  } catch (e) {
    card.classList.remove("busy");
    if (e instanceof AuthError) {
      storeToken("");
      return showLogin("Bitte neu anmelden.");
    }
    $("list-error").textContent = `Das hat nicht geklappt: ${e.message}`;
  }
}

// ------------------------------------------------------------------ Vorschau

async function drawPreviews(card, id) {
  let bitmap;
  try {
    const response = await api(`admin/img/${id}.png`, { raw: true });
    bitmap = await createImageBitmap(await response.blob());
  } catch {
    card.querySelector(".previews").replaceChildren("Bild konnte nicht geladen werden.");
    return;
  }
  const scale = bitmap.width / 64;
  for (const canvas of card.querySelectorAll("canvas[data-part]")) {
    const part = canvas.dataset.part;
    const [x, y, w, h] = part === "texture" ? [0, 0, 64, 32] : PARTS[part];
    const pixelScale = part === "texture" ? 1 : PREVIEW_SCALE;
    // Hochauflösende Capes in voller Auflösung zeichnen, kleine pixelig vergrößern.
    canvas.width = w * Math.max(scale, pixelScale);
    canvas.height = h * Math.max(scale, pixelScale);
    if (part !== "texture") {
      canvas.style.width = `${w * PREVIEW_SCALE}px`;
      canvas.style.height = `${h * PREVIEW_SCALE}px`;
    }
    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(bitmap, x * scale, y * scale, w * scale, h * scale, 0, 0, canvas.width, canvas.height);
  }
  bitmap.close();
}

// ------------------------------------------------------------------ App (Handy / Desktop)

/** Zahl offener Capes am App-Symbol (wo das Gerät es kann). */
function setBadge(n) {
  try {
    if (!("setAppBadge" in navigator)) return;
    if (n > 0) navigator.setAppBadge(n).catch(() => {});
    else navigator.clearAppBadge().catch(() => {});
  } catch {}
}

$("refresh").addEventListener("click", () => load());

// ------------------------------------------------------------------ Push-Benachrichtigungen

const PUSH_API = new URL("../push/", location.href);
const pushSupported = "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

function toKey(base64) {
  const raw = atob((base64 + "=".repeat((4 - (base64.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

async function pushPost(path, body) {
  const response = await fetch(new URL(path, PUSH_API), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error((await response.json().catch(() => ({}))).error || `Fehler ${response.status}`);
}

async function currentSubscription() {
  const registration = await navigator.serviceWorker.ready;
  return registration.pushManager.getSubscription();
}

function showPushState(on) {
  const button = $("push");
  button.classList.toggle("on", on);
  button.title = on ? "Push ist an – tippen zum Ausschalten" : "Push-Benachrichtigungen einschalten";
  button.setAttribute("aria-label", button.title);
}

async function setupPush() {
  if (!pushSupported) return;
  $("push").hidden = false;
  const sub = await currentSubscription().catch(() => null);
  const on = Boolean(sub) && Notification.permission === "granted";
  showPushState(on);
  // Anmeldung beim Server auffrischen (falls der Server sie verloren hat)
  if (on) pushPost("subscribe", { subscription: sub.toJSON() }).catch(() => {});
  else if (Notification.permission === "default" && standalone) $("hint").textContent = "Tipp: Mit der Glocke oben bekommst du bei neuen Capes eine Push-Nachricht.";
}

$("push").addEventListener("click", async () => {
  const button = $("push");
  button.disabled = true;
  try {
    const existing = await currentSubscription();
    if (existing && Notification.permission === "granted") {
      await pushPost("unsubscribe", { endpoint: existing.endpoint }).catch(() => {});
      await existing.unsubscribe();
      showPushState(false);
      $("list-error").textContent = "";
      $("hint").textContent = "Push-Benachrichtigungen sind aus.";
      return;
    }
    const permission = await Notification.requestPermission();
    if (permission !== "granted") {
      $("list-error").textContent =
        "Benachrichtigungen sind blockiert. Erlaube sie in den Einstellungen des Handys für diese App (bzw. Chrome) und tippe dann nochmal auf die Glocke.";
      return;
    }
    const { key } = await (await fetch(new URL("key", PUSH_API))).json();
    const registration = await navigator.serviceWorker.ready;
    const sub = existing || (await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toKey(key) }));
    await pushPost("subscribe", { subscription: sub.toJSON() });
    await pushPost("test", { endpoint: sub.endpoint });
    showPushState(true);
    $("list-error").textContent = "";
    $("hint").textContent = "Push ist an. Du bekommst gleich eine Test-Nachricht, danach eine bei jedem neuen Cape.";
  } catch (e) {
    $("list-error").textContent = `Push konnte nicht eingeschaltet werden: ${e.message}`;
  } finally {
    button.disabled = false;
  }
});

// Antippen einer Benachrichtigung bei offener App: Liste neu laden
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.addEventListener("message", (event) => {
    if (event.data === "reload" && refreshTimer) {
      selectTab("pending");
    }
  });
}

// Kommt die App aus dem Hintergrund zurück, sofort neu laden statt bis zu 30 s zu warten.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && refreshTimer) load();
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}

const standalone = matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
let installPrompt = null;
window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  installPrompt = event;
  $("install").hidden = false;
  $("install-hint").hidden = true;
});
$("install").addEventListener("click", async () => {
  if (!installPrompt) return;
  installPrompt.prompt();
  await installPrompt.userChoice.catch(() => {});
  installPrompt = null;
  $("install").hidden = true;
});
window.addEventListener("appinstalled", () => {
  $("install").hidden = true;
  $("install-hint").hidden = true;
});
if (!standalone && /iphone|ipad|ipod/i.test(navigator.userAgent)) {
  $("install-hint").textContent = "Als App aufs iPhone: unten auf „Teilen“ tippen, dann „Zum Home-Bildschirm“.";
  $("install-hint").hidden = false;
}

start();
