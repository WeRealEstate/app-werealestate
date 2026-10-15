import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente, ClienteLista, ClienteRequest, NumeracionCliente } from '../models/cliente.model';
import { Venta } from '../models/venta.model';

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clientes`;

  listar(): Promise<ClienteLista[]> {
    return firstValueFrom(this.http.get<ClienteLista[]>(this.baseUrl));
  }

  /** Hasta 20 coincidencias por nombre, teléfono o CURP (selector de la venta). */
  buscar(q: string): Promise<ClienteLista[]> {
    return firstValueFrom(this.http.get<ClienteLista[]>(`${this.baseUrl}/buscar`, { params: new HttpParams().set('q', q) }));
  }

  obtener(id: number): Promise<Cliente> {
    return firstValueFrom(this.http.get<Cliente>(`${this.baseUrl}/${id}`));
  }

  /** Siguiente número de cliente y, si "numero" ya lo tiene otro cliente, quién y cuántos se recorrerían. */
  numeracion(numero: number | null, excluirId: number): Promise<NumeracionCliente> {
    let params = new HttpParams().set('excluirId', excluirId);
    if (numero !== null) params = params.set('numero', numero);
    return firstValueFrom(this.http.get<NumeracionCliente>(`${this.baseUrl}/numeracion`, { params }));
  }

  /** Número de contrato de una venta del cliente (desde su detalle); mismo efecto que en el detalle de la venta,
   * pero por una ruta que exige el módulo Clientes en vez del de Ventas. */
  guardarNumeroContratoDeVenta(ventaId: number, numeroContrato: string | null): Promise<Venta> {
    return firstValueFrom(this.http.put<Venta>(`${this.baseUrl}/ventas/${ventaId}/numero-contrato`, { numeroContrato }));
  }

  cambiarNumero(id: number, numero: number): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`${this.baseUrl}/${id}/numero`, { numero }));
  }

  crear(request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.post<Cliente>(this.baseUrl, request));
  }

  actualizar(id: number, request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`${this.baseUrl}/${id}`, request));
  }
}
