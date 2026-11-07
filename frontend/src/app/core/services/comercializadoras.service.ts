import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AsesoresExternosService } from './asesores-externos.service';
import {
  Comercializadora,
  ComercializadoraPublica,
  ComercializadoraRequest,
  IntegranteComercializadora,
  IntegranteRequest,
  QuitarIntegranteResultado,
  UnirseResultado,
} from '../models/comercializadora.model';

/** Comercializadoras: el panel (Admin y Administración con Asesores externos) y el alta pública del equipo por el
 * enlace secreto de cada una (sin sesión). */
@Injectable({ providedIn: 'root' })
export class ComercializadorasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/comercializadoras`;
  private readonly publicoUrl = `${environment.apiUrl}/comercializadoras-publico`;
  /** Integrantes y líderes son asesores externos: tras cambiarlos aquí, la lista de activos guardada en memoria (ver
   * AsesoresExternosService) se vuelve a pedir. */
  private readonly asesores = inject(AsesoresExternosService);

  listar(): Promise<Comercializadora[]> {
    return firstValueFrom(this.http.get<Comercializadora[]>(this.baseUrl));
  }

  integrantes(id: number): Promise<IntegranteComercializadora[]> {
    return firstValueFrom(this.http.get<IntegranteComercializadora[]>(`${this.baseUrl}/${id}/integrantes`));
  }

  actualizarIntegrante(id: number, asesorId: number, request: IntegranteRequest): Promise<IntegranteComercializadora> {
    return this.yRefrescarAsesores(
      firstValueFrom(this.http.put<IntegranteComercializadora>(`${this.baseUrl}/${id}/integrantes/${asesorId}`, request)),
    );
  }

  /** Sin historial se elimina; con ventas, comisiones, etc. solo se desactiva (lo dice el resultado). */
  quitarIntegrante(id: number, asesorId: number): Promise<QuitarIntegranteResultado> {
    return this.yRefrescarAsesores(
      firstValueFrom(this.http.delete<QuitarIntegranteResultado>(`${this.baseUrl}/${id}/integrantes/${asesorId}`)),
    );
  }

  crear(request: ComercializadoraRequest): Promise<Comercializadora> {
    return this.yRefrescarAsesores(firstValueFrom(this.http.post<Comercializadora>(this.baseUrl, request)));
  }

  actualizar(id: number, request: ComercializadoraRequest & { activa: boolean }): Promise<Comercializadora> {
    return this.yRefrescarAsesores(firstValueFrom(this.http.put<Comercializadora>(`${this.baseUrl}/${id}`, request)));
  }

  regenerarEnlace(id: number): Promise<Comercializadora> {
    return firstValueFrom(this.http.post<Comercializadora>(`${this.baseUrl}/${id}/regenerar-enlace`, {}));
  }

  eliminar(id: number): Promise<void> {
    return this.yRefrescarAsesores(firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`)));
  }

  private yRefrescarAsesores<R>(peticion: Promise<R>): Promise<R> {
    return peticion.finally(() => this.asesores.invalidarActivos());
  }

  // ---- Público ----

  infoPublica(token: string): Promise<ComercializadoraPublica> {
    return firstValueFrom(this.http.get<ComercializadoraPublica>(`${this.publicoUrl}/${encodeURIComponent(token)}`));
  }

  unirse(token: string, datos: { nombre: string; celular: string; correo: string; pin: string }): Promise<UnirseResultado> {
    return firstValueFrom(
      this.http.post<UnirseResultado>(`${this.publicoUrl}/${encodeURIComponent(token)}/unirse`, datos),
    );
  }
}
