/* Service worker de We Real Estate: hace la app instalable y recibe las notificaciones push.
   No guarda copias de la API ni de las páginas (los datos deben ser los de hoy); solo la pantalla sin conexión. */

const CACHE = 'we-offline-v1';
const ARCHIVOS_OFFLINE = ['/offline.html', '/images/we-logo.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(ARCHIVOS_OFFLINE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (event) =>
  event.waitUntil(
    caches
      .keys()
      .then((claves) => Promise.all(claves.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  ),
);

// Siempre red; solo si no hay conexión al abrir una página se muestra la pantalla "Sin conexión".
// Un fetch real (no vacío) también es lo que Chrome espera para instalar la app como WebAPK.
self.addEventListener('fetch', (event) => {
  if (event.request.mode !== 'navigate') return;
  event.respondWith(fetch(event.request).catch(() => caches.match('/offline.html')));
});

self.addEventListener('push', (event) => {
  let datos = {};
  try {
    datos = event.data ? event.data.json() : {};
  } catch (e) {
    datos = { body: event.data ? event.data.text() : '' };
  }
  const titulo = datos.title || 'We Real Estate';
  event.waitUntil(
    self.registration.showNotification(titulo, {
      body: datos.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: datos.tag || undefined,
      data: { url: datos.url || '/panel' },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const destino = new URL((event.notification.data && event.notification.data.url) || '/panel', self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ventanas) => {
      for (const v of ventanas) {
        if (v.url.startsWith(self.location.origin) && 'focus' in v) {
          if ('navigate' in v) v.navigate(destino);
          return v.focus();
        }
      }
      return self.clients.openWindow(destino);
    }),
  );
});
