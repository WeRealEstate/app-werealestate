import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WeFormEnvioResultado, WeFormPublico, WeFormPublicoLista } from '../models/we-form.model';

/** Link público de We Forms. Cada petición lleva el PIN del asesor en una cabecera (nunca en la URL). El PIN
 * se recuerda en este dispositivo (sin caducidad) hasta que el asesor pulse "Cambiar de asesor" o el
 * servidor lo rechace; el servidor lo revisa en cada petición. */
const CLAVE_PIN = 'we-forms-pin';

function leerPinGuardado(): string | null {
  try {
    return localStorage.getItem(CLAVE_PIN);
  } catch {
    return null;
  }
}
@Injectable({ providedIn: 'root' })
export class WeFormsPublicoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/forms-publico`;

  /** PIN recordado en este dispositivo (null = hay que pedirlo). */
  readonly pin = signal<string | null>(leerPinGuardado());

  /** Recuerda el PIN en este dispositivo, o lo olvida con null. */
  guardarPin(pin: string | null): void {
    this.pin.set(pin);
    try {
      if (pin) localStorage.setItem(CLAVE_PIN, pin);
      else localStorage.removeItem(CLAVE_PIN);
    } catch {
      // sin almacenamiento disponible: el PIN solo vive mientras la pestaña esté abierta
    }
  }

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
