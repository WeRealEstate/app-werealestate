import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { cargaInterceptor } from './core/interceptors/carga.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Fundido entre pantallas al navegar (estilos en styles.css, ::view-transition-*). La primera carga
    // no lo usa: ahí ya está la pantalla de carga con el logo.
    provideRouter(routes, withViewTransitions({ skipInitialTransition: true })),
    provideHttpClient(withInterceptors([cargaInterceptor, authInterceptor])),
  ],
};
