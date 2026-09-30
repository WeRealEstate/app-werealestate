import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Gasto } from '../models/gasto.model';

@Injectable({ providedIn: 'root' })
export class GastosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/gastos`;

  listar(): Promise<Gasto[]> {
    return firstValueFrom(this.http.get<Gasto[]>(this.baseUrl));
  }

  crear(tipoGastoId: number, fecha: string, monto: number, ticket: File | null): Promise<Gasto> {
    const formData = new FormData();
    formData.append('tipoGastoId', String(tipoGastoId));
    formData.append('fecha', fecha);
    formData.append('monto', String(monto));
    if (ticket) {
      formData.append('ticket', ticket);
    }
    return firstValueFrom(this.http.post<Gasto>(this.baseUrl, formData));
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
}
