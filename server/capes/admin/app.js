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
};
const EMPTY = {
  pending: "Alles geprüft – keine Capes in der Warteschlange.",
  approved: "Noch keine freigegebenen Capes.",
  rejected: "Keine abgelehnten Capes.",
};

const $ = (id) => document.getElementById(id);
let token = readToken();
let status = "pending";
let refreshTimer = null;
let loading = false;

function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

function storeToken(value) {
  token = value;
  try {
    if (value) sessionStorage.setItem(TOKEN_KEY, value);
    else sessionStorage.removeItem(TOKEN_KEY);
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
  try {
    const data = await api(`admin/capes?status=${status}`);
    $("list-error").textContent = "";
    for (const [key, value] of Object.entries(data.counts)) {
      const badge = document.querySelector(`[data-count="${key}"]`);
      badge.textContent = String(value);
      badge.toggleAttribute("data-zero", value === 0);
    }
    document.title = data.counts.pending > 0 ? `(${data.counts.pending}) Salami Capes – Prüfung` : "Salami Capes – Prüfung";
    render(data.capes);
  } catch (e) {
    if (e instanceof AuthError) {
      storeToken("");
      return showLogin("Bitte neu anmelden.");
    }
    $("list-error").textContent = `Liste konnte nicht geladen werden: ${e.message}`;
  } finally {
    loading = false;
  }
}

/** Karten nur neu bauen, wenn sich die Liste geändert hat (sonst flackern die Bilder). */
function render(capes) {
  const grid = $("grid");
  const existing = new Map([...grid.children].map((card) => [card.dataset.key, card]));
  const cards = capes.map((cape) => {
    const key = `${cape.id}:${cape.status}:${cape.reviewedAt || ""}:${cape.users.map((u) => u.uuid).join(",")}`;
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
  if (cape.users.length === 0) users.textContent = "niemand";
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

  const approve = card.querySelector(".approve");
  const reject = card.querySelector(".reject");
  const form = card.querySelector(".reject-form");
  approve.hidden = cape.status === "approved";
  reject.hidden = cape.status === "rejected";
  if (cape.status === "rejected") approve.textContent = "Doch freigeben";
  if (cape.status === "approved") reject.textContent = "Sperren";

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

start();
