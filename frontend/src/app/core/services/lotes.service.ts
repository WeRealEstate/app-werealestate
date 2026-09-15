import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
import {
  Lote,
  LoteCreateRequest,
  LoteImportBatchRequest,
  LoteImportResultado,
  LoteUpdateRequest,
  MovimientoLote,
  PlanoDesarrollo,
} from '../models/lote.model';

export interface BuscarLotesParams {
  manzana?: string;
  numeroLote?: string;
  desarrolloId?: number | null;
  estado?: string | null;
  superficieMin?: number | null;
  superficieMax?: number | null;
  pagina: number;
  tamano: number;
}

export interface BuscarMovimientosParams {
  manzana?: string;
  numeroLote?: string;
  desarrolloId?: number | null;
  pagina: number;
  tamano: number;
}

@Injectable({ providedIn: 'root' })
export class LotesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/lotes`;

  buscarPaginado(params: BuscarLotesParams): Promise<Pagina<Lote>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.manzana) httpParams = httpParams.set('manzana', params.manzana);
    if (params.numeroLote) httpParams = httpParams.set('numeroLote', params.numeroLote);
    if (params.desarrolloId != null) httpParams = httpParams.set('desarrolloId', params.desarrolloId);
    if (params.estado) httpParams = httpParams.set('estado', params.estado);
    if (params.superficieMin != null) httpParams = httpParams.set('superficieMin', params.superficieMin);
    if (params.superficieMax != null) httpParams = httpParams.set('superficieMax', params.superficieMax);

    return firstValueFrom(this.http.get<Pagina<Lote>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }

  listarDisponibles(desarrolloId: number): Promise<Lote[]> {
    return firstValueFrom(
      this.http.get<Lote[]>(`${this.baseUrl}/disponibles`, { params: { desarrolloId } }),
    );
  }

  /** Para /cotizador-publico/lotes: sin sesión, ver LoteController/SecurityConfig. */
  listarPublicoPorProyecto(proyecto: 'samai' | 'nanuu'): Promise<Lote[]> {
    return firstValueFrom(this.http.get<Lote[]>(`${this.baseUrl}/publico`, { params: { proyecto } }));
  }

  /** nombreAsesor es obligatorio para apartar (no para liberar); nota siempre es opcional — ver
   * LoteService.cambiarEstadoPublico. */
  cambiarEstadoPublico(id: number, estado: string, nombreAsesor?: string, nota?: string): Promise<Lote> {
    return firstValueFrom(
      this.http.put<Lote>(`${this.baseUrl}/publico/${id}/estado`, {
        estado,
        nombreAsesor: nombreAsesor ?? null,
        nota: nota ?? null,
      }),
    );
  }

  buscarMovimientos(params: BuscarMovimientosParams): Promise<Pagina<MovimientoLote>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.manzana) httpParams = httpParams.set('manzana', params.manzana);
    if (params.numeroLote) httpParams = httpParams.set('numeroLote', params.numeroLote);
    if (params.desarrolloId != null) httpParams = httpParams.set('desarrolloId', params.desarrolloId);

    return firstValueFrom(
      this.http.get<Pagina<MovimientoLote>>(`${this.baseUrl}/movimientos`, { params: httpParams }),
    );
  }

  obtener(id: number): Promise<Lote> {
    return firstValueFrom(this.http.get<Lote>(`${this.baseUrl}/${id}`));
  }

  crear(request: LoteCreateRequest): Promise<Lote> {
    return firstValueFrom(this.http.post<Lote>(this.baseUrl, request));
  }

  actualizar(id: number, request: LoteUpdateRequest): Promise<Lote> {
    return firstValueFrom(this.http.put<Lote>(`${this.baseUrl}/${id}`, request));
  }

  cambiarEstado(id: number, estado: string): Promise<Lote> {
    return firstValueFrom(this.http.put<Lote>(`${this.baseUrl}/${id}/estado`, { estado }));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }

  importar(request: LoteImportBatchRequest): Promise<LoteImportResultado> {
    return firstValueFrom(this.http.post<LoteImportResultado>(`${this.baseUrl}/importar`, request));
  }

  /** Plano interactivo de un desarrollo (imagen + pin de cada lote), para /panel/plano. */
  obtenerMapa(desarrolloId: number): Promise<PlanoDesarrollo> {
    return firstValueFrom(this.http.get<PlanoDesarrollo>(`${this.baseUrl}/mapa`, { params: { desarrolloId } }));
  }

  /** Ubica el pin de un lote en el plano; pasar ambos en null lo borra. Exclusivo de admin. */
  actualizarPosicionMapa(id: number, mapaX: number | null, mapaY: number | null): Promise<Lote> {
    return firstValueFrom(this.http.put<Lote>(`${this.baseUrl}/${id}/mapa`, { mapaX, mapaY }));
  }
}
