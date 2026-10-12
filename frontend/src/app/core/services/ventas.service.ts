import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
import {
  PagoVenta,
  PagoVentaCreateRequest,
  ReciboEditarRequest,
  ReciboHistorial,
  Venta,
  VentaCreateRequest,
  VentaUpdateRequest,
} from '../models/venta.model';

export interface BuscarVentasParams {
  busqueda?: string;
  /** Por fecha de venta: "desc" = la más reciente primero (default), "asc" = la más antigua primero. */
  orden?: 'asc' | 'desc';
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
    if (params.orden) httpParams = httpParams.set('orden', params.orden);

    return firstValueFrom(this.http.get<Pagina<Venta>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }

  /** Lotes vendidos (cada lote una vez) y ventas registradas (una por operación). */
  contadores(): Promise<{ lotesVendidos: number; ventasRegistradas: number }> {
    return firstValueFrom(this.http.get<{ lotesVendidos: number; ventasRegistradas: number }>(`${this.baseUrl}/contadores`));
  }

  contarLotesVendidosSamaiCampestre(): Promise<number> {
    return firstValueFrom(this.http.get<number>(`${this.baseUrl}/samai-campestre/lotes-vendidos`));
  }

  /** Abrir una venta por su número visible (/panel/ventas/11). El número puede cambiar si se registra
   * otra venta con fecha anterior, así que solo sirve para encontrarla: lo que escribe (abonos,
   * modificar) usa el `id` de la venta devuelta. */
  obtenerPorNumero(numero: number): Promise<Venta> {
    return firstValueFrom(this.http.get<Venta>(`${this.baseUrl}/por-numero/${numero}`));
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

  /** Elimina la venta por completo (solo admin): exige su contraseña. liberarLotes: los lotes que sigan
   * VENDIDO vuelven a DISPONIBLE. La comisión de la venta se queda en el historial de Finanzas. */
  eliminar(id: number, password: string, liberarLotes: boolean): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/${id}/eliminar`, { password, liberarLotes }));
  }

  listarPagos(ventaId: number): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.get<PagoVenta[]>(`${this.baseUrl}/${ventaId}/pagos`));
  }

  /** Registra o corrige si el contrato está firmado y desde cuándo. */
  registrarContrato(ventaId: number, firmado: boolean, fechaFirma: string | null): Promise<Venta> {
    return firstValueFrom(this.http.put<Venta>(`${this.baseUrl}/${ventaId}/contrato`, { firmado, fechaFirma }));
  }

  /** Edita folio, nota y, por línea, tipo/meses/aportación (nunca monto ni fecha). */
  editarRecibo(ventaId: number, reciboId: number, request: ReciboEditarRequest): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.put<PagoVenta[]>(`${this.baseUrl}/${ventaId}/recibos/${reciboId}`, request));
  }

  /** Anula un recibo (no se borra): su dinero deja de contar en el saldo y en las comisiones. */
  anularRecibo(ventaId: number, reciboId: number, motivo: string): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.post<PagoVenta[]>(`${this.baseUrl}/${ventaId}/recibos/${reciboId}/anular`, { motivo }));
  }

  historialRecibos(ventaId: number): Promise<ReciboHistorial[]> {
    return firstValueFrom(this.http.get<ReciboHistorial[]>(`${this.baseUrl}/${ventaId}/recibos/historial`));
  }

  registrarPago(ventaId: number, request: PagoVentaCreateRequest): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.post<PagoVenta[]>(`${this.baseUrl}/${ventaId}/pagos`, request));
  }
}
