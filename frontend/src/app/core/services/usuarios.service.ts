import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, UsuarioCreateRequest, UsuarioUpdateRequest } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/usuarios`;

  listar(): Promise<Usuario[]> {
    return firstValueFrom(this.http.get<Usuario[]>(this.baseUrl));
  }

  crear(request: UsuarioCreateRequest): Promise<Usuario> {
    return firstValueFrom(this.http.post<Usuario>(this.baseUrl, request));
  }

  actualizar(id: number, request: UsuarioUpdateRequest): Promise<Usuario> {
    return firstValueFrom(this.http.put<Usuario>(`${this.baseUrl}/${id}`, request));
  }
}
