import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CotizacionCreateRequest, CotizacionHistorial } from '../models/cotizacion.model';
import { Pagina } from '../models/pagina.model';

export interface BuscarCotizacionesParams {
  busqueda?: string;
  asesorId?: number | null;
  proyecto?: string | null;
  pagina: number;
  tamano: number;
}

@Injectable({ providedIn: 'root' })
export class CotizacionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cotizaciones`;

  registrar(request: CotizacionCreateRequest): Promise<CotizacionHistorial> {
    return firstValueFrom(this.http.post<CotizacionHistorial>(this.baseUrl, request));
  }

  /** Solo el admin puede consultar el historial completo (lo exige el backend). */
  listar(): Promise<CotizacionHistorial[]> {
    return firstValueFrom(this.http.get<CotizacionHistorial[]>(this.baseUrl));
  }

  /** Historial paginado: la búsqueda y los filtros corren en el servidor sobre el total, no solo
   * sobre lo ya cargado con "Cargar más". */
  buscarPaginado(params: BuscarCotizacionesParams): Promise<Pagina<CotizacionHistorial>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.busqueda) httpParams = httpParams.set('busqueda', params.busqueda);
    if (params.asesorId != null) httpParams = httpParams.set('asesorId', params.asesorId);
    if (params.proyecto) httpParams = httpParams.set('proyecto', params.proyecto);

    return firstValueFrom(this.http.get<Pagina<CotizacionHistorial>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }
}
