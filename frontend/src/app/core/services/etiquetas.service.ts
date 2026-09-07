import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Etiqueta, EtiquetaCreateRequest, EtiquetaUpdateRequest } from '../models/lead.model';

@Injectable({ providedIn: 'root' })
export class EtiquetasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/etiquetas`;

  listar(asesorId?: number): Promise<Etiqueta[]> {
    const url = asesorId != null ? `${this.baseUrl}?asesorId=${asesorId}` : this.baseUrl;
    return firstValueFrom(this.http.get<Etiqueta[]>(url));
  }

  crear(request: EtiquetaCreateRequest): Promise<Etiqueta> {
    return firstValueFrom(this.http.post<Etiqueta>(this.baseUrl, request));
  }

  actualizar(id: number, request: EtiquetaUpdateRequest): Promise<Etiqueta> {
    return firstValueFrom(this.http.put<Etiqueta>(`${this.baseUrl}/${id}`, request));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
