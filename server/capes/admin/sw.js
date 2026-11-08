// Salami Capes – Service Worker: macht die Prüf-Seite als App installierbar und startet sie
// auch bei schlechtem Netz. Nur die Oberfläche wird zwischengespeichert, nie API-Antworten.
"use strict";
const CACHE = "salami-capes-admin-v3";
const SHELL = ["./", "app.css", "app.js", "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png", "badge-96.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

// Oberfläche: erst Netz (immer aktuell), bei Funkloch aus dem Speicher. Alles andere geht direkt ans Netz.
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== location.origin || !url.pathname.startsWith(new URL("./", location.href).pathname)) return;
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      })
      .catch(() => caches.match(event.request).then((hit) => hit || caches.match("./"))),
  );
});

// Push vom Server (neues Cape): Benachrichtigung zeigen, Antippen öffnet die App.
self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {}
  event.waitUntil(
    self.registration.showNotification(data.title || "Salami Capes", {
      body: data.body || "Neues Cape wartet auf Freigabe.",
      icon: "icon-192.png",
      badge: "badge-96.png",
      tag: data.tag || "salami-capes",
      renotify: true,
      data: { url: new URL("./", self.registration.scope).href },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = event.notification.data?.url || self.registration.scope;
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      for (const client of list) {
        if (client.url.startsWith(self.registration.scope)) {
          client.postMessage("reload");
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
