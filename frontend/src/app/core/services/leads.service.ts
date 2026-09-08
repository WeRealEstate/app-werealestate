import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Desarrollo,
  Lead,
  LeadCreateRequest,
  LeadImportBatchRequest,
  LeadImportResultado,
  LeadUpdateRequest,
  MoverColumnaRequest,
  Seguimiento,
  SeguimientoCreateRequest,
  SeguimientoProximo,
} from '../models/lead.model';

@Injectable({ providedIn: 'root' })
export class LeadsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/leads`;

  /** Asegura `etiquetas` como arreglo: un backend desactualizado o una fila sin
   * etiquetas puede mandar el campo en null o ausente, y eso rompe cualquier
   * `for`/`.length` sobre él en los componentes. */
  private normalizar(lead: Lead): Lead {
    return { ...lead, etiquetas: lead.etiquetas ?? [] };
  }

  listar(): Promise<Lead[]> {
    return firstValueFrom(this.http.get<Lead[]>(this.baseUrl).pipe(map((leads) => leads.map((l) => this.normalizar(l)))));
  }

  listarFrios(): Promise<Lead[]> {
    return firstValueFrom(
      this.http.get<Lead[]>(`${this.baseUrl}/frios`).pipe(map((leads) => leads.map((l) => this.normalizar(l)))),
    );
  }

  listarArchivados(): Promise<Lead[]> {
    return firstValueFrom(
      this.http.get<Lead[]>(`${this.baseUrl}/archivados`).pipe(map((leads) => leads.map((l) => this.normalizar(l)))),
    );
  }

  archivar(id: number): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}/archivar`, {}).pipe(map((l) => this.normalizar(l))));
  }

  desarchivar(id: number): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}/desarchivar`, {}).pipe(map((l) => this.normalizar(l))));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }

  obtener(id: number): Promise<Lead> {
    return firstValueFrom(this.http.get<Lead>(`${this.baseUrl}/${id}`).pipe(map((l) => this.normalizar(l))));
  }

  crear(request: LeadCreateRequest): Promise<Lead> {
    return firstValueFrom(this.http.post<Lead>(this.baseUrl, request).pipe(map((l) => this.normalizar(l))));
  }

  actualizar(id: number, request: LeadUpdateRequest): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}`, request).pipe(map((l) => this.normalizar(l))));
  }

  importar(request: LeadImportBatchRequest): Promise<LeadImportResultado> {
    return firstValueFrom(this.http.post<LeadImportResultado>(`${this.baseUrl}/importar`, request));
  }

  reasignar(id: number, nuevoAsesorId: number): Promise<Lead> {
    return firstValueFrom(
      this.http.put<Lead>(`${this.baseUrl}/${id}/reasignar`, { nuevoAsesorId }).pipe(map((l) => this.normalizar(l))),
    );
  }

  moverColumna(id: number, request: MoverColumnaRequest): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}/mover-columna`, request).pipe(map((l) => this.normalizar(l))));
  }

  asignarEtiquetas(id: number, etiquetaIds: number[]): Promise<Lead> {
    return firstValueFrom(
      this.http.put<Lead>(`${this.baseUrl}/${id}/etiquetas`, { etiquetaIds }).pipe(map((l) => this.normalizar(l))),
    );
  }

  listarSeguimientos(leadId: number): Promise<Seguimiento[]> {
    return firstValueFrom(this.http.get<Seguimiento[]>(`${this.baseUrl}/${leadId}/seguimientos`));
  }

  crearSeguimiento(leadId: number, request: SeguimientoCreateRequest): Promise<Seguimiento> {
    return firstValueFrom(this.http.post<Seguimiento>(`${this.baseUrl}/${leadId}/seguimientos`, request));
  }

  listarDesarrollos(): Promise<Desarrollo[]> {
    return firstValueFrom(this.http.get<Desarrollo[]>(`${environment.apiUrl}/desarrollos`));
  }

  proximosSeguimientos(): Promise<SeguimientoProximo[]> {
    return firstValueFrom(this.http.get<SeguimientoProximo[]>(`${environment.apiUrl}/seguimientos/proximos`));
  }
}
