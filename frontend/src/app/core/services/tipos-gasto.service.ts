import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TipoGasto, TipoGastoCreateRequest, TipoGastoUpdateRequest } from '../models/gasto.model';

@Injectable({ providedIn: 'root' })
export class TiposGastoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tipos-gasto`;

  listar(): Promise<TipoGasto[]> {
    return firstValueFrom(this.http.get<TipoGasto[]>(this.baseUrl));
  }

  /** Solo los activos, para el <select> del formulario de registrar gasto. Accesible también para líderes de área. */
  listarActivos(): Promise<TipoGasto[]> {
    return firstValueFrom(this.http.get<TipoGasto[]>(`${this.baseUrl}/activos`));
  }

  crear(request: TipoGastoCreateRequest): Promise<TipoGasto> {
    return firstValueFrom(this.http.post<TipoGasto>(this.baseUrl, request));
  }

  actualizar(id: number, request: TipoGastoUpdateRequest): Promise<TipoGasto> {
    return firstValueFrom(this.http.put<TipoGasto>(`${this.baseUrl}/${id}`, request));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
