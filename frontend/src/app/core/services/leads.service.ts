import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
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

export interface BuscarLeadsParams {
  busqueda?: string;
  estado?: string | null;
  asesorId?: number | null;
  etiquetaId?: number | null;
  archivados?: boolean;
  pagina: number;
  tamano: number;
}

@Injectable({ providedIn: 'root' })
export class LeadsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/leads`;

  /** Asegura `etiquetas` como arreglo: un backend desactualizado o una fila sin
   * etiquetas puede mandar el campo en null o ausente, y eso rompe cualquier
   * `for`/`.length` sobre él en los componentes. */
  private normalizar(lead: Lead): Lead {
    return { ...lead, etiquetas: lead.etiquetas ?? [], desarrolloDetalle: lead.desarrolloDetalle ?? null };
  }

  listar(): Promise<Lead[]> {
    return firstValueFrom(this.http.get<Lead[]>(this.baseUrl).pipe(map((leads) => leads.map((l) => this.normalizar(l)))));
  }

  /** Lista principal, paginada: la búsqueda y los filtros corren en el servidor sobre el total,
   * no solo sobre lo ya cargado con "Cargar más". */
  buscarPaginado(params: BuscarLeadsParams): Promise<Pagina<Lead>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.busqueda) httpParams = httpParams.set('busqueda', params.busqueda);
    if (params.estado) httpParams = httpParams.set('estado', params.estado);
    if (params.asesorId != null) httpParams = httpParams.set('asesorId', params.asesorId);
    if (params.etiquetaId != null) httpParams = httpParams.set('etiquetaId', params.etiquetaId);
    if (params.archivados) httpParams = httpParams.set('archivados', 'true');

    return firstValueFrom(
      this.http.get<Pagina<Lead>>(`${this.baseUrl}/buscar`, { params: httpParams }).pipe(
        map((pagina) => ({ ...pagina, contenido: pagina.contenido.map((l) => this.normalizar(l)) })),
      ),
    );
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
