import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ColumnaPersonalizada } from '../models/lead.model';

@Injectable({ providedIn: 'root' })
export class ColumnasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/columnas`;

  /** Columnas del asesor actual, o de otro asesor si se pasa asesorId (solo un admin puede hacerlo). */
  listar(asesorId?: number | null): Promise<ColumnaPersonalizada[]> {
    const params = asesorId != null ? new HttpParams().set('asesorId', asesorId) : undefined;
    return firstValueFrom(this.http.get<ColumnaPersonalizada[]>(this.baseUrl, { params }));
  }

  crear(nombre: string, asesorId?: number | null): Promise<ColumnaPersonalizada> {
    return firstValueFrom(this.http.post<ColumnaPersonalizada>(this.baseUrl, { nombre, asesorId }));
  }

  renombrar(id: number, nombre: string): Promise<ColumnaPersonalizada> {
    return firstValueFrom(this.http.put<ColumnaPersonalizada>(`${this.baseUrl}/${id}`, { nombre }));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }
}
