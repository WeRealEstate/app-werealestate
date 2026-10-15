import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
import {
  PagoVenta,
  MedioFirma,
  AplicarDepositoRequest,
  NumeracionVenta,
  PagoVentaCreateRequest,
  ReciboEditarRequest,
  ReciboHistorial,
  Venta,
  VentaCreateRequest,
  VentaUpdateRequest,
} from '../models/venta.model';

export interface BuscarVentasParams {
  busqueda?: string;
  /** Por número de venta: "desc" = el más alto primero (default), "asc" = el más bajo primero. */
  orden?: 'asc' | 'desc';
  pagina: number;
  tamano: number;
  /** Solo las ventas con algún lote de ese desarrollo (una venta mixta aparece en cada uno). */
  desarrolloId?: number | null;
}

@Injectable({ providedIn: 'root' })
export class VentasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ventas`;

  buscarPaginado(params: BuscarVentasParams): Promise<Pagina<Venta>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.busqueda) httpParams = httpParams.set('busqueda', params.busqueda);
    if (params.orden) httpParams = httpParams.set('orden', params.orden);
    if (params.desarrolloId) httpParams = httpParams.set('desarrolloId', params.desarrolloId);

    return firstValueFrom(this.http.get<Pagina<Venta>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }

  /** Lotes vendidos (cada lote una vez) y ventas registradas (una por operación). */
  contadores(desarrolloId?: number | null): Promise<{ lotesVendidos: number; ventasRegistradas: number }> {
    const params = desarrolloId ? new HttpParams().set('desarrolloId', desarrolloId) : undefined;
    return firstValueFrom(
      this.http.get<{ lotesVendidos: number; ventasRegistradas: number }>(`${this.baseUrl}/contadores`, { params }),
    );
  }

  /** Siguiente número automático y, si `numero` ya lo tiene otra venta (distinta de `excluirId`), cuál y cuántas se recorrerían. */
  numeracion(numero?: number | null, excluirId?: number | null): Promise<NumeracionVenta> {
    let params = new HttpParams();
    if (numero) params = params.set('numero', numero);
    if (excluirId) params = params.set('excluirId', excluirId);
    return firstValueFrom(this.http.get<NumeracionVenta>(`${this.baseUrl}/numeracion`, { params }));
  }

  /** Aplica el depósito pendiente de una venta que nació de un registro de venta (We Forms). */
  aplicarDeposito(id: number, request: AplicarDepositoRequest): Promise<Venta> {
    return firstValueFrom(this.http.post<Venta>(`${this.baseUrl}/${id}/aplicar-deposito`, request));
  }

  contarLotesVendidosSamaiCampestre(): Promise<number> {
    return firstValueFrom(this.http.get<number>(`${this.baseUrl}/samai-campestre/lotes-vendidos`));
  }

  /** Abrir una venta por su número visible (/panel/ventas/11). El número puede cambiar si alguien lo
   * edita o recorre, así que solo sirve para encontrarla: lo que escribe (abonos, modificar) usa el `id`
   * de la venta devuelta. */
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

  /** Elimina la venta por completo (admin y administración): exige su contraseña. liberarLotes: los lotes que sigan
   * VENDIDO vuelven a DISPONIBLE. La comisión de la venta se queda en el historial de Finanzas. */
  eliminar(id: number, password: string, liberarLotes: boolean): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/${id}/eliminar`, { password, liberarLotes }));
  }

  listarPagos(ventaId: number): Promise<PagoVenta[]> {
    return firstValueFrom(this.http.get<PagoVenta[]>(`${this.baseUrl}/${ventaId}/pagos`));
  }

  /** Registra o corrige si el contrato está firmado y desde cuándo. */
  registrarContrato(
    ventaId: number,
    datos: { firmado: boolean; fechaFirma: string | null; medio: MedioFirma | null; driveUrl: string | null },
  ): Promise<Venta> {
    return firstValueFrom(this.http.put<Venta>(`${this.baseUrl}/${ventaId}/contrato`, datos));
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
