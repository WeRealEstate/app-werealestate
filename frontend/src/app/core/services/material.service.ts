import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MaterialItem } from '../models/material.model';

export interface MaterialForm {
  titulo: string;
  descripcion: string;
  categoria: string;
  proyecto: string;
  texto: string;
  archivo: File | null;
  quitarArchivo?: boolean;
}

/** Material de venta: el panel de administración (sesión) y la consulta de los asesores en el plano público
 * (PIN en la cabecera X-Asesor-Pin, nunca en la URL; el mismo PIN que recuerda We Forms). */
@Injectable({ providedIn: 'root' })
export class MaterialService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/material`;
  private readonly publicoUrl = `${environment.apiUrl}/material-publico`;

  // ---------------------------------------------------------------- panel

  listar(): Promise<MaterialItem[]> {
    return firstValueFrom(this.http.get<MaterialItem[]>(this.baseUrl));
  }

  crear(form: MaterialForm): Promise<MaterialItem> {
    return firstValueFrom(this.http.post<MaterialItem>(this.baseUrl, this.cuerpo(form)));
  }

  actualizar(id: number, form: MaterialForm): Promise<MaterialItem> {
    return firstValueFrom(this.http.put<MaterialItem>(`${this.baseUrl}/${id}`, this.cuerpo(form)));
  }

  eliminar(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}`));
  }

  archivo(id: number): Promise<Blob> {
    return firstValueFrom(this.http.get(`${this.baseUrl}/${id}/archivo`, { responseType: 'blob' }));
  }

  /** Imagen reducida para las tarjetas (solo imágenes). Si el servidor no la tiene, se usa el archivo completo:
   * la tarjeta siempre muestra la imagen. */
  async miniatura(id: number): Promise<Blob> {
    try {
      return await firstValueFrom(this.http.get(`${this.baseUrl}/${id}/miniatura`, { responseType: 'blob' }));
    } catch {
      return this.archivo(id);
    }
  }

  private cuerpo(form: MaterialForm): FormData {
    const datos = new FormData();
    datos.append('titulo', form.titulo.trim());
    datos.append('descripcion', form.descripcion.trim());
    datos.append('categoria', form.categoria);
    datos.append('proyecto', form.proyecto);
    datos.append('texto', form.texto);
    if (form.archivo) datos.append('archivo', form.archivo);
    if (form.quitarArchivo) datos.append('quitarArchivo', 'true');
    return datos;
  }

  // ---------------------------------------------------------------- plano público

  listarPublico(proyecto: string, pin: string): Promise<MaterialItem[]> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(this.http.get<MaterialItem[]>(this.publicoUrl, { params, headers: this.cabeceras(pin) }));
  }

  archivoPublico(id: number, proyecto: string, pin: string): Promise<Blob> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(
      this.http.get(`${this.publicoUrl}/${id}/archivo`, { params, headers: this.cabeceras(pin), responseType: 'blob' }),
    );
  }

  async miniaturaPublico(id: number, proyecto: string, pin: string): Promise<Blob> {
    const params = new HttpParams().set('proyecto', proyecto);
    try {
      return await firstValueFrom(
        this.http.get(`${this.publicoUrl}/${id}/miniatura`, { params, headers: this.cabeceras(pin), responseType: 'blob' }),
      );
    } catch {
      return this.archivoPublico(id, proyecto, pin);
    }
  }

  private cabeceras(pin: string): HttpHeaders {
    return new HttpHeaders({ 'X-Asesor-Pin': pin });
  }
}
