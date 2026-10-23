import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Comercializadora,
  ComercializadoraPublica,
  ComercializadoraRequest,
  IntegranteComercializadora,
  UnirseResultado,
} from '../models/comercializadora.model';

/** Comercializadoras: el panel (Admin y Administración con Asesores externos) y el alta pública del equipo por el
 * enlace secreto de cada una (sin sesión). */
@Injectable({ providedIn: 'root' })
export class ComercializadorasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/comercializadoras`;
  private readonly publicoUrl = `${environment.apiUrl}/comercializadoras-publico`;

  listar(): Promise<Comercializadora[]> {
    return firstValueFrom(this.http.get<Comercializadora[]>(this.baseUrl));
  }

  integrantes(id: number): Promise<IntegranteComercializadora[]> {
    return firstValueFrom(this.http.get<IntegranteComercializadora[]>(`${this.baseUrl}/${id}/integrantes`));
  }

  crear(request: ComercializadoraRequest): Promise<Comercializadora> {
    return firstValueFrom(this.http.post<Comercializadora>(this.baseUrl, request));
  }

  actualizar(id: number, request: ComercializadoraRequest & { activa: boolean }): Promise<Comercializadora> {
    return firstValueFrom(this.http.put<Comercializadora>(`${this.baseUrl}/${id}`, request));
  }

  regenerarEnlace(id: number): Promise<Comercializadora> {
    return firstValueFrom(this.http.post<Comercializadora>(`${this.baseUrl}/${id}/regenerar-enlace`, {}));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }

  // ---- Público ----

  infoPublica(token: string): Promise<ComercializadoraPublica> {
    return firstValueFrom(this.http.get<ComercializadoraPublica>(`${this.publicoUrl}/${encodeURIComponent(token)}`));
  }

  unirse(token: string, datos: { nombre: string; celular: string; correo: string }): Promise<UnirseResultado> {
    return firstValueFrom(
      this.http.post<UnirseResultado>(`${this.publicoUrl}/${encodeURIComponent(token)}/unirse`, datos),
    );
  }
}
