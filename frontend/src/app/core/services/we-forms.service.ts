import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Desarrollo } from '../models/lead.model';
import { Pagina } from '../models/pagina.model';
import {
  EstadisticasForm,
  FiltroRespuestas,
  ResumenGeneralForm,
  WeFormActualizarRequest,
  WeFormCrearRequest,
  WeFormDetalle,
  WeFormLista,
  WeFormPregunta,
  WeFormPreguntaRequest,
  WeFormRespuesta,
} from '../models/we-form.model';

/** We Forms en el panel (módulo We Forms): formularios, preguntas y respuestas. */
@Injectable({ providedIn: 'root' })
export class WeFormsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/we-forms`;

  listar(): Promise<WeFormLista[]> {
    return firstValueFrom(this.http.get<WeFormLista[]>(this.baseUrl));
  }

  /** Desarrollos en los que se puede crear un formulario (los que tienen link público con PIN). */
  desarrollos(): Promise<Desarrollo[]> {
    return firstValueFrom(this.http.get<Desarrollo[]>(`${this.baseUrl}/desarrollos`));
  }

  crear(request: WeFormCrearRequest): Promise<WeFormDetalle> {
    return firstValueFrom(this.http.post<WeFormDetalle>(this.baseUrl, request));
  }

  obtener(id: number): Promise<WeFormDetalle> {
    return firstValueFrom(this.http.get<WeFormDetalle>(`${this.baseUrl}/${id}`));
  }

  actualizar(id: number, request: WeFormActualizarRequest): Promise<WeFormDetalle> {
    return firstValueFrom(this.http.put<WeFormDetalle>(`${this.baseUrl}/${id}`, request));
  }

  crearPregunta(id: number, request: WeFormPreguntaRequest): Promise<WeFormPregunta> {
    return firstValueFrom(this.http.post<WeFormPregunta>(`${this.baseUrl}/${id}/preguntas`, request));
  }

  editarPregunta(id: number, preguntaId: number, request: WeFormPreguntaRequest): Promise<WeFormPregunta> {
    return firstValueFrom(this.http.put<WeFormPregunta>(`${this.baseUrl}/${id}/preguntas/${preguntaId}`, request));
  }

  archivarPregunta(id: number, preguntaId: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseUrl}/${id}/preguntas/${preguntaId}`));
  }

  reordenar(id: number, ids: number[]): Promise<WeFormPregunta[]> {
    return firstValueFrom(this.http.put<WeFormPregunta[]>(`${this.baseUrl}/${id}/preguntas/orden`, { ids }));
  }

  private paramsDe(filtro: FiltroRespuestas, params = new HttpParams()): HttpParams {
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    if (filtro.asesor?.trim()) params = params.set('asesor', filtro.asesor.trim());
    return params;
  }

  respuestas(id: number, pagina: number, filtro: FiltroRespuestas = {}, tamano = 20): Promise<Pagina<WeFormRespuesta>> {
    const params = this.paramsDe(filtro, new HttpParams().set('pagina', pagina).set('tamano', tamano));
    return firstValueFrom(this.http.get<Pagina<WeFormRespuesta>>(`${this.baseUrl}/${id}/respuestas`, { params }));
  }

  /** Todas las respuestas del filtro (hasta 5000), para exportar. */
  exportar(id: number, filtro: FiltroRespuestas = {}): Promise<WeFormRespuesta[]> {
    return firstValueFrom(
      this.http.get<WeFormRespuesta[]>(`${this.baseUrl}/${id}/respuestas/exportar`, { params: this.paramsDe(filtro) }),
    );
  }

  estadisticas(id: number, filtro: FiltroRespuestas = {}): Promise<EstadisticasForm> {
    return firstValueFrom(
      this.http.get<EstadisticasForm>(`${this.baseUrl}/${id}/estadisticas`, { params: this.paramsDe(filtro) }),
    );
  }

  resumen(): Promise<ResumenGeneralForm> {
    return firstValueFrom(this.http.get<ResumenGeneralForm>(`${this.baseUrl}/resumen`));
  }

  /** Los archivos viven detrás de autenticación: se traen con el JWT y se abren como blob URL. */
  async verArchivo(respuestaId: number, itemId: number): Promise<void> {
    const blob = await firstValueFrom(
      this.http.get(`${this.baseUrl}/respuestas/${respuestaId}/archivos/${itemId}`, { responseType: 'blob' }),
    );
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
}
