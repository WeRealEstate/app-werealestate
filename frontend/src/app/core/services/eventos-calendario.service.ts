import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventoCalendario, EventoCalendarioRequest } from '../models/evento-calendario.model';

@Injectable({ providedIn: 'root' })
export class EventosCalendarioService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/eventos-calendario`;

  listar(): Promise<EventoCalendario[]> {
    return firstValueFrom(this.http.get<EventoCalendario[]>(this.baseUrl));
  }

  crear(request: EventoCalendarioRequest): Promise<EventoCalendario> {
    return firstValueFrom(this.http.post<EventoCalendario>(this.baseUrl, request));
  }

  actualizar(id: number, request: EventoCalendarioRequest): Promise<EventoCalendario> {
    return firstValueFrom(this.http.put<EventoCalendario>(`${this.baseUrl}/${id}`, request));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
