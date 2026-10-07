import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AsesorExterno,
  AsesorExternoCreateRequest,
  AsesorExternoUpdateRequest,
  AsesorFicha,
  AsesorFichaRequest,
} from '../models/asesor-externo.model';

@Injectable({ providedIn: 'root' })
export class AsesoresExternosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/asesores-externos`;

  listar(): Promise<AsesorExterno[]> {
    return firstValueFrom(this.http.get<AsesorExterno[]>(this.baseUrl));
  }

  /** Solo los activos, para el <select> del formulario de venta. Accesible también para líderes de área. */
  listarActivos(): Promise<AsesorExterno[]> {
    return firstValueFrom(this.http.get<AsesorExterno[]>(`${this.baseUrl}/activos`));
  }

  crear(request: AsesorExternoCreateRequest): Promise<AsesorExterno> {
    return firstValueFrom(this.http.post<AsesorExterno>(this.baseUrl, request));
  }

  actualizar(id: number, request: AsesorExternoUpdateRequest): Promise<AsesorExterno> {
    return firstValueFrom(this.http.put<AsesorExterno>(`${this.baseUrl}/${id}`, request));
  }

  /** Datos secundarios del asesor: experiencia, dónde están contrato y expediente, quién lo trajo, notas. */
  ficha(id: number): Promise<AsesorFicha> {
    return firstValueFrom(this.http.get<AsesorFicha>(`${this.baseUrl}/${id}/ficha`));
  }

  actualizarFicha(id: number, request: AsesorFichaRequest): Promise<AsesorFicha> {
    return firstValueFrom(this.http.put<AsesorFicha>(`${this.baseUrl}/${id}/ficha`, request));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
