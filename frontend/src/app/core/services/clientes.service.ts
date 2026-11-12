import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Amortizacion } from '../models/amortizacion.model';
import { Cliente, ClienteEliminacion, ClienteLista, ClienteRequest, NumeracionCliente } from '../models/cliente.model';
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

  /** La tabla de amortización de cada compra del cliente (titular o copropietario), de la más reciente a la más
   * antigua. */
  amortizaciones(id: number): Promise<Amortizacion[]> {
    return firstValueFrom(this.http.get<Amortizacion[]>(`${this.baseUrl}/${id}/amortizaciones`));
  }

  /** Siguiente número de cliente de ese desarrollo y, si "numero" ya lo tiene otro cliente, quién y cuántos se
   * recorrerían. */
  numeracion(desarrolloId: number, numero: number | null, excluirId: number): Promise<NumeracionCliente> {
    let params = new HttpParams().set('desarrolloId', desarrolloId).set('excluirId', excluirId);
    if (numero !== null) params = params.set('numero', numero);
    return firstValueFrom(this.http.get<NumeracionCliente>(`${this.baseUrl}/numeracion`, { params }));
  }

  /** Número de contrato de una venta del cliente (desde su detalle); mismo efecto que en el detalle de la venta,
   * pero por una ruta que exige el módulo Clientes en vez del de Ventas. */
  guardarNumeroContratoDeVenta(ventaId: number, numeroContrato: string | null, password: string | null = null): Promise<Venta> {
    return firstValueFrom(this.http.put<Venta>(`${this.baseUrl}/ventas/${ventaId}/numero-contrato`, { numeroContrato, password }));
  }

  /** Cambia el número del cliente en un desarrollo (se mueve junto con quienes lo comparten). */
  cambiarNumero(id: number, desarrolloId: number, numero: number): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`${this.baseUrl}/${id}/numero`, { desarrolloId, numero }));
  }

  crear(request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.post<Cliente>(this.baseUrl, request));
  }

  actualizar(id: number, request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`${this.baseUrl}/${id}`, request));
  }

  /** Lo que se borraría al eliminar el cliente (ventas como titular, copropiedades, registros de We Forms…). */
  vistaPreviaEliminacion(id: number): Promise<ClienteEliminacion> {
    return firstValueFrom(this.http.get<ClienteEliminacion>(`${this.baseUrl}/${id}/eliminacion`));
  }

  /** Elimina el cliente con sus ventas como titular (admin y administración, con su contraseña). */
  eliminar(id: number, password: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/${id}/eliminar`, { password }));
  }
}
