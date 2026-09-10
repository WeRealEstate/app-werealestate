import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Promocion, PromocionCreateRequest, PromocionUpdateRequest } from '../models/promocion.model';

@Injectable({ providedIn: 'root' })
export class PromocionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/promociones`;

  /** Cualquier asesor o admin puede consultarlas: son las que aparecen en el Cotizador. */
  listarActivas(): Promise<Promocion[]> {
    return firstValueFrom(this.http.get<Promocion[]>(`${this.baseUrl}/activas`));
  }

  /** Catálogo completo (activas e inactivas), solo para la pantalla de administración. */
  listar(): Promise<Promocion[]> {
    return firstValueFrom(this.http.get<Promocion[]>(this.baseUrl));
  }

  crear(request: PromocionCreateRequest): Promise<Promocion> {
    return firstValueFrom(this.http.post<Promocion>(this.baseUrl, request));
  }

  actualizar(id: number, request: PromocionUpdateRequest): Promise<Promocion> {
    return firstValueFrom(this.http.put<Promocion>(`${this.baseUrl}/${id}`, request));
  }

  cambiarEstado(id: number, activa: boolean): Promise<Promocion> {
    return firstValueFrom(this.http.put<Promocion>(`${this.baseUrl}/${id}/estado`, { activa }));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
