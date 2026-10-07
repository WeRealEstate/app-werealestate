import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente, ClienteLista, ClienteRequest } from '../models/cliente.model';

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

  crear(request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.post<Cliente>(this.baseUrl, request));
  }

  actualizar(id: number, request: ClienteRequest): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`${this.baseUrl}/${id}`, request));
  }
}
