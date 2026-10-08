import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { CargaInicialService } from '../services/carga-inicial.service';

/** Cuenta las peticiones en curso para saber cuándo la primera pantalla ya terminó de cargar sus datos. */
export const cargaInterceptor: HttpInterceptorFn = (req, next) => {
  const carga = inject(CargaInicialService);
  carga.peticionIniciada();
  return next(req).pipe(finalize(() => carga.peticionTerminada()));
};
