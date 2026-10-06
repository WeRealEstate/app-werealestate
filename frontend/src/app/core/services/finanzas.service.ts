import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Comision,
  ComisionDetalle,
  ComisionResumen,
  ComisionesPorEntregar,
  FinanzasIngresos,
  FinanzasValor,
  IngresoDetalle,
} from '../models/finanzas.model';

@Injectable({ providedIn: 'root' })
export class FinanzasService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/finanzas`;
  private readonly baseUrl = `${this.apiUrl}/comisiones`;

  listarComisiones(): Promise<Comision[]> {
    return firstValueFrom(this.http.get<Comision[]>(this.baseUrl));
  }

  resumenComisiones(): Promise<ComisionResumen> {
    return firstValueFrom(this.http.get<ComisionResumen>(`${this.baseUrl}/resumen`));
  }

  /** Lo que toca entregar al sábado indicado (sin fecha: el próximo sábado, hoy si es sábado). */
  comisionesPorEntregar(fecha?: string): Promise<ComisionesPorEntregar> {
    const params = fecha ? new HttpParams().set('fecha', fecha) : undefined;
    return firstValueFrom(this.http.get<ComisionesPorEntregar>(`${this.baseUrl}/por-entregar`, { params }));
  }

  detalleComision(id: number): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.get<ComisionDetalle>(`${this.baseUrl}/${id}`));
  }

  /** Exactamente uno de los dos: el otro se calcula sobre el valor de la venta. */
  editarComision(id: number, cambio: { porcentaje: number } | { monto: number }): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.put<ComisionDetalle>(`${this.baseUrl}/${id}`, cambio));
  }

  cancelarComision(id: number): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.post<ComisionDetalle>(`${this.baseUrl}/${id}/cancelar`, {}));
  }

  reactivarComision(id: number): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.post<ComisionDetalle>(`${this.baseUrl}/${id}/reactivar`, {}));
  }

  entregarComision(id: number, monto: number, fecha: string | null, notas: string | null): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.post<ComisionDetalle>(`${this.baseUrl}/${id}/entregas`, { monto, fecha, notas }));
  }

  anularEntrega(comisionId: number, entregaId: number): Promise<ComisionDetalle> {
    return firstValueFrom(this.http.delete<ComisionDetalle>(`${this.baseUrl}/${comisionId}/entregas/${entregaId}`));
  }

  /** Valor total vendido, por desarrollo y por lote. */
  valorVendido(): Promise<FinanzasValor> {
    return firstValueFrom(this.http.get<FinanzasValor>(`${this.apiUrl}/valor-vendido`));
  }

  /** Esperado contra recibido por mes (desde/hasta como "yyyy-MM"; el atraso se calcula desde el primer mes). */
  ingresos(desde: string | null, hasta: string | null, desarrolloId: number | null): Promise<FinanzasIngresos> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    if (desarrolloId) params = params.set('desarrolloId', desarrolloId);
    return firstValueFrom(this.http.get<FinanzasIngresos>(`${this.apiUrl}/ingresos`, { params }));
  }

  ingresosDelMes(mes: string, desarrolloId: number | null): Promise<IngresoDetalle> {
    const params = desarrolloId ? new HttpParams().set('desarrolloId', desarrolloId) : undefined;
    return firstValueFrom(this.http.get<IngresoDetalle>(`${this.apiUrl}/ingresos/${mes}`, { params }));
  }
}
