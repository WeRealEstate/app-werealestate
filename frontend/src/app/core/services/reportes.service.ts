import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ReporteDesempeno } from '../models/reporte.model';

@Injectable({ providedIn: 'root' })
export class ReportesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/reportes`;

  /** desde/hasta en formato YYYY-MM-DD; ambos opcionales (sin ninguno = histórico completo). */
  obtenerDesempeno(desde?: string, hasta?: string): Promise<ReporteDesempeno> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return firstValueFrom(this.http.get<ReporteDesempeno>(`${this.baseUrl}/desempeno`, { params }));
  }
}
