import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsMx from '@angular/common/locales/es-MX';
import { provideRouter, withNavigationErrorHandler, withPreloading, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { cargaInterceptor } from './core/interceptors/carga.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { PrecargaPorRolStrategy } from './core/precarga-por-rol.strategy';

// Sin esto los pipes date/number usan en-US y las fechas salían "28 Oct 2026" o "Jan". es-MX (no "es") a
// propósito: conserva la coma de miles y el punto decimal, así los montos se ven igual que antes.
registerLocaleData(localeEsMx);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'es-MX' },
    // Fundido entre pantallas al navegar (estilos en styles.css, ::view-transition-*). La primera carga
    // no lo usa: ahí ya está la pantalla de carga con el logo.
    // Precarga en segundo plano las pantallas del panel que el usuario puede abrir (ver PrecargaPorRolStrategy).
    // Si al abrir un link (p. ej. /samai o /aldea-nanuu) el navegador tenía en caché un index.html viejo tras un
    // despliegue y el chunk de la pantalla ya cambió de nombre, recarga una sola vez para traer la versión actual.
    provideRouter(
      routes,
      withViewTransitions({ skipInitialTransition: true }),
      withPreloading(PrecargaPorRolStrategy),
      withNavigationErrorHandler((evento) => {
        const mensaje = String((evento as { error?: unknown })?.error ?? evento ?? '');
        if (/Failed to fetch dynamically imported module|Importing a module script failed|ChunkLoadError|error loading dynamically imported module/i.test(mensaje)) {
          try {
            const clave = 'we_recarga_chunk';
            const ultima = Number(sessionStorage.getItem(clave) ?? '0');
            if (Date.now() - ultima > 15_000) {
              sessionStorage.setItem(clave, String(Date.now()));
              window.location.reload();
            }
          } catch {
            window.location.reload();
          }
        }
      }),
    ),
    provideHttpClient(withInterceptors([cargaInterceptor, authInterceptor])),
  ],
};
