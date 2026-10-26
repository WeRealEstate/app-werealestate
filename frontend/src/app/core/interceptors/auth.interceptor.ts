import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Endpoints de los planos públicos y el cotizador público (/samai, /aldea-nanuu, /cotizador-publico/lotes): se piden SIN
 * la sesión aunque el navegador tenga una, para que el link público se vea igual para todos (un admin que lo abre en el
 * mismo navegador donde usa el panel vería "Reservado por We" en vez de "Vendido"). */
const ENDPOINT_PUBLICO_DE_LOTES = '/lotes/publico';

/** Adjunta el JWT a cada request y cierra sesión automáticamente si el backend responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = req.url.includes(ENDPOINT_PUBLICO_DE_LOTES) ? null : auth.token;

  const authedReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authedReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
