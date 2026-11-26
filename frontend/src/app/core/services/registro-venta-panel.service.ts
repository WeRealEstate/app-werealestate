import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PromocionCodigo, WeFormEnvioResultado, WeFormPublico, WeFormPublicoLista } from '../models/we-form.model';

export interface WeFormPanelLista extends WeFormPublicoLista {
  proyecto: 'samai' | 'nanuu';
}

/**
 * "Registrar venta" en el panel del asesor interno: los mismos formularios que el enlace público, pero con su sesión.
 * Tiene la misma forma que WeFormsPublicoService para que el formulario (WeFormPublicoComponent) use cualquiera de los
 * dos; aquí no hay PIN, así que `pin` es un valor fijo y no se guarda nada.
 */
@Injectable({ providedIn: 'root' })
export class RegistroVentaPanelService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/registro-venta`;

  readonly pin = signal<string | null>('sesion');

  guardarPin(_pin: string | null): void {}

  listar(): Promise<WeFormPanelLista[]> {
    return firstValueFrom(this.http.get<WeFormPanelLista[]>(`${this.baseUrl}/formularios`));
  }

  obtener(slug: string, _pin: string): Promise<WeFormPublico> {
    return firstValueFrom(this.http.get<WeFormPublico>(`${this.baseUrl}/${slug}`));
  }

  codigoPromocion(slug: string, _pin: string, codigo: string): Promise<PromocionCodigo> {
    return firstValueFrom(this.http.post<PromocionCodigo>(`${this.baseUrl}/${slug}/codigo-promocion`, { codigo }));
  }

  enviar(
    slug: string,
    _pin: string,
    respuestas: { preguntaId: number; valor: unknown }[],
    archivos: Map<number, File>,
    codigoPromocion: string | null = null,
  ): Promise<WeFormEnvioResultado> {
    const form = new FormData();
    form.append('datos', JSON.stringify({ respuestas, codigoPromocion }));
    archivos.forEach((archivo, preguntaId) => form.append(`archivo-${preguntaId}`, archivo));
    return firstValueFrom(this.http.post<WeFormEnvioResultado>(`${this.baseUrl}/${slug}/respuestas`, form));
  }
}
