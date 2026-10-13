import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WeFormEnvioResultado, WeFormPublico, WeFormPublicoLista } from '../models/we-form.model';

/** Link público de We Forms. Cada petición lleva el PIN del asesor en una cabecera (nunca en la URL); el PIN
 * solo vive en memoria de esta pestaña: al cerrarla o recargar hay que escribirlo de nuevo. */
@Injectable({ providedIn: 'root' })
export class WeFormsPublicoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/forms-publico`;

  /** PIN ya escrito en esta pestaña (null = hay que pedirlo). */
  readonly pin = signal<string | null>(null);

  private cabeceras(pin: string): HttpHeaders {
    return new HttpHeaders({ 'X-Asesor-Pin': pin });
  }

  listar(proyecto: string, pin: string): Promise<WeFormPublicoLista[]> {
    const params = new HttpParams().set('proyecto', proyecto);
    return firstValueFrom(
      this.http.get<WeFormPublicoLista[]>(`${this.baseUrl}/formularios`, { params, headers: this.cabeceras(pin) }),
    );
  }

  obtener(slug: string, pin: string): Promise<WeFormPublico> {
    return firstValueFrom(this.http.get<WeFormPublico>(`${this.baseUrl}/${slug}`, { headers: this.cabeceras(pin) }));
  }

  enviar(slug: string, pin: string, respuestas: { preguntaId: number; valor: unknown }[], archivos: Map<number, File>): Promise<WeFormEnvioResultado> {
    const form = new FormData();
    form.append('datos', JSON.stringify({ respuestas }));
    archivos.forEach((archivo, preguntaId) => form.append(`archivo-${preguntaId}`, archivo));
    return firstValueFrom(
      this.http.post<WeFormEnvioResultado>(`${this.baseUrl}/${slug}/respuestas`, form, { headers: this.cabeceras(pin) }),
    );
  }
}
