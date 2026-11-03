import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsMx from '@angular/common/locales/es-MX';
import { provideRouter, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { cargaInterceptor } from './core/interceptors/carga.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

// Sin esto los pipes date/number usan en-US y las fechas salían "28 Oct 2026" o "Jan". es-MX (no "es") a
// propósito: conserva la coma de miles y el punto decimal, así los montos se ven igual que antes.
registerLocaleData(localeEsMx);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'es-MX' },
    // Fundido entre pantallas al navegar (estilos en styles.css, ::view-transition-*). La primera carga
    // no lo usa: ahí ya está la pantalla de carga con el logo.
    provideRouter(routes, withViewTransitions({ skipInitialTransition: true })),
    provideHttpClient(withInterceptors([cargaInterceptor, authInterceptor])),
  ],
};
