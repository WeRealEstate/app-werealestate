import { HttpClient, HttpEventType, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { filter, firstValueFrom, map, tap } from 'rxjs';
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

  // ---------------------------------------------------------------- galería (panel)

  /** Sube una imagen al final de la galería; `alAvanzar` recibe el avance de la subida (0 a 100). */
  agregarImagen(id: number, archivo: File, alAvanzar?: (porcentaje: number) => void): Promise<MaterialItem> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return firstValueFrom(
      this.http.post<MaterialItem>(`${this.baseUrl}/${id}/imagenes`, datos, { reportProgress: true, observe: 'events' }).pipe(
        tap((e) => {
          if (e.type === HttpEventType.UploadProgress && e.total) alAvanzar?.(Math.round((e.loaded / e.total) * 100));
        }),
        filter((e) => e.type === HttpEventType.Response),
        map((e) => e.body as MaterialItem),
      ),
    );
  }

  quitarImagen(id: number, imagenId: number): Promise<MaterialItem> {
    return firstValueFrom(this.http.delete<MaterialItem>(`${this.baseUrl}/${id}/imagenes/${imagenId}`));
  }

  ordenarImagenes(id: number, ids: number[]): Promise<MaterialItem> {
    return firstValueFrom(this.http.put<MaterialItem>(`${this.baseUrl}/${id}/imagenes/orden`, { ids }));
  }

  imagen(id: number, imagenId: number): Promise<Blob> {
    return firstValueFrom(this.http.get(`${this.baseUrl}/${id}/imagenes/${imagenId}`, { responseType: 'blob' }));
  }

  miniaturaImagen(id: number, imagenId: number): Promise<Blob> {
    return firstValueFrom(this.http.get(`${this.baseUrl}/${id}/imagenes/${imagenId}/miniatura`, { responseType: 'blob' }));
  }

  zip(id: number): Promise<Blob> {
    return firstValueFrom(this.http.get(`${this.baseUrl}/${id}/zip`, { responseType: 'blob' }));
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

  imagenPublico(id: number, imagenId: number, proyecto: string, pin: string): Promise<Blob> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(
      this.http.get(`${this.publicoUrl}/${id}/imagenes/${imagenId}`, { params, headers: this.cabeceras(pin), responseType: 'blob' }),
    );
  }

  miniaturaImagenPublico(id: number, imagenId: number, proyecto: string, pin: string): Promise<Blob> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(
      this.http.get(`${this.publicoUrl}/${id}/imagenes/${imagenId}/miniatura`, {
        params,
        headers: this.cabeceras(pin),
        responseType: 'blob',
      }),
    );
  }

  zipPublico(id: number, proyecto: string, pin: string): Promise<Blob> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(
      this.http.get(`${this.publicoUrl}/${id}/zip`, { params, headers: this.cabeceras(pin), responseType: 'blob' }),
    );
  }

  private cabeceras(pin: string): HttpHeaders {
    return new HttpHeaders({ 'X-Asesor-Pin': pin });
  }
}
