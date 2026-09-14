import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notificacion, TipoNotificacion } from '../models/notificacion.model';

@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/notificaciones`;

  listar(): Promise<Notificacion[]> {
    return firstValueFrom(this.http.get<Notificacion[]>(this.baseUrl));
  }

  /** Persiste que el usuario ya vio esta ocurrencia concreta: no vuelve a aparecer aunque la
   * condición que la generó siga vigente (a menos que la firma cambie, ver backend). */
  marcarLeida(tipo: TipoNotificacion, entidadId: number, firma: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/leida`, { tipo, entidadId, firma }));
  }
}
