import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
import {
  PagoVenta,
  PagoVentaCreateRequest,
  Venta,
  VentaCreateRequest,
  VentaUpdateRequest,
} from '../models/venta.model';

export interface BuscarVentasParams {
  busqueda?: string;
  pagina: number;
  tamano: number;
}

@Injectable({ providedIn: 'root' })
export class VentasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ventas`;

  buscarPaginado(params: BuscarVentasParams): Promise<Pagina<Venta>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.busqueda) httpParams = httpParams.set('busqueda', params.busqueda);

    return firstValueFrom(this.http.get<Pagina<Venta>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }

  obtener(id: number): Promise<Venta> {
    return firstValueFrom(this.http.get<Venta>(`${this.baseUrl}/${id}`));
  }

  /** Para el ícono de "ver información de venta" en /panel/lotes. Rechaza (404) si el lote no
   * tiene una venta asociada (ej. su estado se cambió a mano, no desde el módulo de ventas). */
  obtenerPorLote(loteId: number): Promise<Venta> {
    return firstValueFrom(this.http.get<Venta>(`${this.baseUrl}/por-lote/${loteId}`));
  }

  crear(request: VentaCreateRequest): Promise<Venta> {
    return firstValueFrom(this.http.post<Venta>(this.baseUrl, request));
  }

  /** Temporal: para el botón "Modificar venta" en el detalle, se va a quitar más adelante. */
  actualizar(id: number, request: VentaUpdateRequest): Promise<Venta> {
    return firstValueFrom(this.http.put<Venta>(`${this.baseUrl}/${id}`, request));
  }

  listarPagos(ventaId: number): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.get<PagoVenta[]>(`${this.baseUrl}/${ventaId}/pagos`));
  }

  registrarPago(ventaId: number, request: PagoVentaCreateRequest): Promise<PagoVenta> {
    return firstValueFrom(this.http.post<PagoVenta>(`${this.baseUrl}/${ventaId}/pagos`, request));
  }
}
