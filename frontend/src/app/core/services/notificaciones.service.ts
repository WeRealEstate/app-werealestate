import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notificacion, TipoNotificacion } from '../models/notificacion.model';
import { alLimpiarSesion } from '../utils/cache-memoria';

/**
 * Las notificaciones pendientes del usuario, en un solo lugar para la campana y el inicio del asesor: antes cada uno
 * las pedía por su cuenta (el inicio del asesor las bajaba dos veces) y la campana las volvía a pedir en cada cambio
 * de pantalla. Calcularlas es de lo más pesado del servidor (revisa leads, tareas, eventos, lotes y ventas), así que
 * se comparten la lista y la petición en curso.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/notificaciones`;

  private readonly listaSignal = signal<Notificacion[]>([]);
  readonly lista = this.listaSignal.asReadonly();

  private ultimaCarga = 0;
  private enCurso: Promise<Notificacion[]> | null = null;
  /** Sube al cambiar de sesión: una respuesta que llegue después ya no se guarda. */
  private version = 0;

  constructor() {
    alLimpiarSesion(() => {
      this.version++;
      this.enCurso = null;
      this.ultimaCarga = 0;
      this.listaSignal.set([]);
    });
  }

  /** Pide las notificaciones y las deja en `lista`. Si ya hay una petición en camino devuelve esa misma; con
   * `siPasaronMs`, si la última carga es más reciente que eso, no pide nada y devuelve la lista que ya tiene. */
  refrescar(siPasaronMs = 0): Promise<Notificacion[]> {
    if (this.enCurso) return this.enCurso;
    if (siPasaronMs > 0 && this.ultimaCarga > 0 && Date.now() - this.ultimaCarga < siPasaronMs) {
      return Promise.resolve(this.listaSignal());
    }
    const version = this.version;
    const peticion: Promise<Notificacion[]> = firstValueFrom(this.http.get<Notificacion[]>(this.baseUrl))
      .then((lista) => {
        if (version === this.version) {
          this.listaSignal.set(lista);
          this.ultimaCarga = Date.now();
        }
        return lista;
      })
      .finally(() => {
        if (this.enCurso === peticion) this.enCurso = null;
      });
    this.enCurso = peticion;
    return peticion;
  }

  /** Saca una de la lista al descartarla o al navegar desde ella, sin volver a pedirlas todas. */
  quitar(notificacion: Notificacion): void {
    this.listaSignal.update((lista) => lista.filter((n) => n !== notificacion));
  }

  /** Persiste que el usuario ya vio esta ocurrencia concreta: no vuelve a aparecer aunque la
   * condición que la generó siga vigente (a menos que la firma cambie, ver backend). */
  marcarLeida(tipo: TipoNotificacion, entidadId: number, firma: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/leida`, { tipo, entidadId, firma }));
  }
}
