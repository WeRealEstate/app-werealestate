import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, UsuarioCreateRequest, UsuarioUpdateRequest } from '../models/user.model';
import { UsuarioResumen } from '../models/lead.model';

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/usuarios`;

  listar(): Promise<Usuario[]> {
    return firstValueFrom(this.http.get<Usuario[]>(this.baseUrl));
  }

  /** Lista ligera (id/nombre) de usuarios activos no-admin, para pickers de "asignar a". Accesible también para líderes de área. */
  asignables(): Promise<UsuarioResumen[]> {
    return firstValueFrom(this.http.get<UsuarioResumen[]>(`${this.baseUrl}/asignables`));
  }

  crear(request: UsuarioCreateRequest): Promise<Usuario> {
    return firstValueFrom(this.http.post<Usuario>(this.baseUrl, request));
  }

  actualizar(id: number, request: UsuarioUpdateRequest): Promise<Usuario> {
    return firstValueFrom(this.http.put<Usuario>(`${this.baseUrl}/${id}`, request));
  }

  restablecerPassword(id: number, password: string): Promise<void> {
    return firstValueFrom(this.http.put<void>(`${this.baseUrl}/${id}/password`, { password }));
  }
}
