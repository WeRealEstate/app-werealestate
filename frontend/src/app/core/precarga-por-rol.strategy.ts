import { Injectable, inject } from '@angular/core';
import { CanActivateFn, PreloadingStrategy, Route, Router } from '@angular/router';
import { Observable, firstValueFrom, from, of } from 'rxjs';
import { REQUISITOS_DE_GUARD } from './guards/requisitos-ruta';
import { AuthService } from './services/auth.service';

/** Cuánto esperar tras la carga antes de empezar: deja que la pantalla actual termine sus propias peticiones. */
const RETRASO_MS = 2000;

/** Conexiones en las que no se precarga nada (decisión del usuario): ahorro de datos o 2G/3G. */
const CONEXIONES_LENTAS = new Set(['slow-2g', '2g', '3g']);

interface InfoConexion {
  saveData?: boolean;
  effectiveType?: string;
}

/** Ahorro de datos activado o conexión lenta, según la Network Information API. Safari y Firefox no la tienen: ahí se
 * precarga normal. Se consulta en cada turno, así que si la conexión empeora a media precarga, se detiene. */
function conexionLenta(): boolean {
  const conexion = (navigator as Navigator & { connection?: InfoConexion }).connection;
  if (!conexion) return false;
  return conexion.saveData === true || CONEXIONES_LENTAS.has(conexion.effectiveType ?? '');
}

/**
 * Precarga en segundo plano el código de las pantallas del panel para que cambiar de pantalla sea casi instantáneo,
 * pero solo las que el usuario puede abrir: un asesor no descarga las de administración. Lo que exige cada pantalla
 * sale de sus roleGuard/moduloGuard (REQUISITOS_DE_GUARD), sin ejecutarlos. Empieza RETRASO_MS después de cargar y va
 * de una en una, para no competir con las peticiones de la pantalla que se está usando, y no hace nada sin sesión
 * (login y páginas públicas) ni con datos lentos. Las librerías de Excel y PDF no entran: se cargan al usarlas.
 *
 * El router vuelve a llamar a preload() después de cada navegación con lo que aún no se cargó: si se entró sin sesión,
 * la precarga arranca sola después de iniciarla.
 */
@Injectable({ providedIn: 'root' })
export class PrecargaPorRolStrategy implements PreloadingStrategy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** Termina cuando acaba la última precarga encolada; la siguiente espera su turno. */
  private fila: Promise<unknown> | null = null;

  preload(route: Route, load: () => Observable<unknown>): Observable<unknown> {
    if (!this.debePrecargar(route)) return of(null);
    const anterior = this.fila ?? new Promise((resolver) => setTimeout(resolver, RETRASO_MS));
    const turno = anterior.then(() =>
      conexionLenta() || !this.auth.isAuthenticated() ? null : firstValueFrom(load()).catch(() => null),
    );
    this.fila = turno;
    return from(turno);
  }

  private debePrecargar(route: Route): boolean {
    const usuario = this.auth.currentUser();
    if (!usuario || !this.auth.isAuthenticated()) return false;
    const panel = this.router.config.find((r) => r.path === 'panel');
    if (!panel?.children?.includes(route)) return false;
    for (const guard of route.canActivate ?? []) {
      const requisito = REQUISITOS_DE_GUARD.get(guard as CanActivateFn);
      if (!requisito) continue;
      if (requisito.roles && !requisito.roles.includes(usuario.rol)) return false;
      if (requisito.modulos && !this.auth.tieneAlgunModulo([...requisito.modulos])) return false;
    }
    return true;
  }
}
