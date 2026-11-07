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
import { CacheLista } from '../utils/cache-memoria';

@Injectable({ providedIn: 'root' })
export class AsesoresExternosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/asesores-externos`;
  /** Los activos casi no cambian y los piden Nueva venta y el detalle de cada venta: 5 minutos en memoria (ver
   * CacheLista), y se vacían al crear, editar o borrar un asesor (aquí o desde Comercializadoras). */
  private readonly cacheActivos = new CacheLista<AsesorExterno>();

  listar(): Promise<AsesorExterno[]> {
    return firstValueFrom(this.http.get<AsesorExterno[]>(this.baseUrl));
  }

  /** Solo los activos, para el <select> del formulario de venta. Accesible también para líderes de área. */
  listarActivos(): Promise<AsesorExterno[]> {
    return this.cacheActivos.obtener(() => firstValueFrom(this.http.get<AsesorExterno[]>(`${this.baseUrl}/activos`)));
  }

  /** Para quien cambia asesores por otra API (Comercializadoras): la próxima lista de activos se pide de nuevo. */
  invalidarActivos(): void {
    this.cacheActivos.invalidar();
  }

  crear(request: AsesorExternoCreateRequest): Promise<AsesorExterno> {
    return this.yRefrescarActivos(firstValueFrom(this.http.post<AsesorExterno>(this.baseUrl, request)));
  }

  actualizar(id: number, request: AsesorExternoUpdateRequest): Promise<AsesorExterno> {
    return this.yRefrescarActivos(firstValueFrom(this.http.put<AsesorExterno>(`${this.baseUrl}/${id}`, request)));
  }

  /** Datos secundarios del asesor: experiencia, dónde están contrato y expediente, quién lo trajo, notas. */
  ficha(id: number): Promise<AsesorFicha> {
    return firstValueFrom(this.http.get<AsesorFicha>(`${this.baseUrl}/${id}/ficha`));
  }

  actualizarFicha(id: number, request: AsesorFichaRequest): Promise<AsesorFicha> {
    return firstValueFrom(this.http.put<AsesorFicha>(`${this.baseUrl}/${id}/ficha`, request));
  }

  eliminar(id: number): Promise<void> {
    return this.yRefrescarActivos(firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`)));
  }

  private yRefrescarActivos<R>(peticion: Promise<R>): Promise<R> {
    return peticion.finally(() => this.cacheActivos.invalidar());
  }
}
