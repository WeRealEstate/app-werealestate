/* Service worker de We Real Estate: hace la app instalable y recibe las notificaciones push.
   No guarda copias de la API ni de las páginas: siempre va a la red (los datos deben ser los de hoy). */

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

// Necesario para que el navegador ofrezca instalar la app; pasa todo directo a la red.
self.addEventListener('fetch', () => {});

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
