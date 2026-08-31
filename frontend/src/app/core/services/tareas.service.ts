import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tarea, TareaCreateRequest } from '../models/tarea.model';

@Injectable({ providedIn: 'root' })
export class TareasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tareas`;

  misTareas(): Promise<Tarea[]> {
    return firstValueFrom(this.http.get<Tarea[]>(this.baseUrl));
  }

  tareasCreadas(): Promise<Tarea[]> {
    return firstValueFrom(this.http.get<Tarea[]>(`${this.baseUrl}/creadas`));
  }

  crear(request: TareaCreateRequest): Promise<Tarea> {
    return firstValueFrom(this.http.post<Tarea>(this.baseUrl, request));
  }

  cambiarEstado(id: number, completada: boolean): Promise<Tarea> {
    return firstValueFrom(this.http.put<Tarea>(`${this.baseUrl}/${id}/estado`, { completada }));
  }
}
