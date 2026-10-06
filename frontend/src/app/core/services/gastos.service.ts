import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Gasto,
  GastoRecurrente,
  GastoRecurrentePago,
  GastoRecurrenteRequest,
  GastoResumen,
} from '../models/gasto.model';

@Injectable({ providedIn: 'root' })
export class GastosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/gastos`;

  listar(): Promise<Gasto[]> {
    return firstValueFrom(this.http.get<Gasto[]>(this.baseUrl));
  }

  /** Gasto de una sola vez (papelería, insumos...): concepto, fecha y monto; ticket opcional. */
  crear(concepto: string, fecha: string, monto: number, ticket: File | null): Promise<Gasto> {
    return firstValueFrom(this.http.post<Gasto>(this.baseUrl, formularioGasto({ concepto, fecha, monto }, ticket)));
  }

  /** El ticket vive detrás de autenticación (ver GastoController), así que no es un <img src>
   * directo: hay que traerlo con el JWT (el interceptor se lo agrega) y abrirlo como blob URL. */
  async verTicket(id: number): Promise<void> {
    const blob = await firstValueFrom(this.http.get(`${this.baseUrl}/${id}/ticket`, { responseType: 'blob' }));
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }

  // ---- Resumen y gastos recurrentes ----

  resumen(mes: string | null): Promise<GastoResumen> {
    const params = mes ? new HttpParams().set('mes', mes) : undefined;
    return firstValueFrom(this.http.get<GastoResumen>(`${this.baseUrl}/resumen`, { params }));
  }

  listarRecurrentes(): Promise<GastoRecurrente[]> {
    return firstValueFrom(this.http.get<GastoRecurrente[]>(`${this.baseUrl}/recurrentes`));
  }

  crearRecurrente(request: GastoRecurrenteRequest): Promise<GastoRecurrente> {
    return firstValueFrom(this.http.post<GastoRecurrente>(`${this.baseUrl}/recurrentes`, request));
  }

  editarRecurrente(id: number, request: GastoRecurrenteRequest): Promise<GastoRecurrente> {
    return firstValueFrom(this.http.put<GastoRecurrente>(`${this.baseUrl}/recurrentes/${id}`, request));
  }

  eliminarRecurrente(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/recurrentes/${id}`));
  }

  /** Vencimientos sin pagar (pendientes y vencidos), del más próximo al más lejano. */
  pagosSinPagar(): Promise<GastoRecurrentePago[]> {
    return firstValueFrom(this.http.get<GastoRecurrentePago[]>(`${this.baseUrl}/recurrentes/pagos`));
  }

  /** Paga un vencimiento con el monto real; crea el gasto correspondiente. Ticket opcional. */
  pagarVencimiento(pagoId: number, monto: number, fecha: string, ticket: File | null): Promise<Gasto> {
    return firstValueFrom(
      this.http.post<Gasto>(`${this.baseUrl}/recurrentes/pagos/${pagoId}/pagar`, formularioGasto({ monto, fecha }, ticket)),
    );
  }

  omitirVencimiento(pagoId: number): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/recurrentes/pagos/${pagoId}/omitir`, {}));
  }

  /** gastoId = el del gasto que generó el pago; el vencimiento vuelve a quedar sin pagar. */
  deshacerPago(gastoId: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/recurrentes/pagos/gasto/${gastoId}`));
  }
}

function formularioGasto(campos: Record<string, string | number>, ticket: File | null): FormData {
  const formData = new FormData();
  for (const [clave, valor] of Object.entries(campos)) formData.append(clave, String(valor));
  if (ticket) formData.append('ticket', ticket);
  return formData;
}
