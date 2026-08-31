import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Desarrollo,
  Lead,
  LeadCreateRequest,
  LeadUpdateRequest,
  Seguimiento,
  SeguimientoCreateRequest,
} from '../models/lead.model';

@Injectable({ providedIn: 'root' })
export class LeadsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/leads`;

  listar(): Promise<Lead[]> {
    return firstValueFrom(this.http.get<Lead[]>(this.baseUrl));
  }

  listarFrios(): Promise<Lead[]> {
    return firstValueFrom(this.http.get<Lead[]>(`${this.baseUrl}/frios`));
  }

  obtener(id: number): Promise<Lead> {
    return firstValueFrom(this.http.get<Lead>(`${this.baseUrl}/${id}`));
  }

  crear(request: LeadCreateRequest): Promise<Lead> {
    return firstValueFrom(this.http.post<Lead>(this.baseUrl, request));
  }

  actualizar(id: number, request: LeadUpdateRequest): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}`, request));
  }

  reasignar(id: number, nuevoAsesorId: number): Promise<Lead> {
    return firstValueFrom(this.http.put<Lead>(`${this.baseUrl}/${id}/reasignar`, { nuevoAsesorId }));
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
}
