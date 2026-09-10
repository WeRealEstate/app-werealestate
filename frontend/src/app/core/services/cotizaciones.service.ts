import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CotizacionCreateRequest, CotizacionHistorial } from '../models/cotizacion.model';

@Injectable({ providedIn: 'root' })
export class CotizacionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cotizaciones`;

  registrar(request: CotizacionCreateRequest): Promise<CotizacionHistorial> {
    return firstValueFrom(this.http.post<CotizacionHistorial>(this.baseUrl, request));
  }

  /** Solo el admin puede consultar el historial completo (lo exige el backend). */
  listar(): Promise<CotizacionHistorial[]> {
    return firstValueFrom(this.http.get<CotizacionHistorial[]>(this.baseUrl));
  }
}
