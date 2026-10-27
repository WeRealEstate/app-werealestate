import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RankingScore, ScorePublico } from '../models/score.model';

@Injectable({ providedIn: 'root' })
export class ScoreService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/score`;

  /** Fechas de venta yyyy-MM-dd, ambas inclusivas; sin ellas, todo el historial. */
  ranking(desde?: string, hasta?: string): Promise<RankingScore> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return firstValueFrom(this.http.get<RankingScore>(this.baseUrl, { params }));
  }

  /** El mismo ranking sin sesión, para /cotizador-publico/score. El PIN guardado en el dispositivo (si hay) va en una
   * cabecera, nunca en la URL, y solo sirve para marcar la fila de quien lo ve. */
  rankingPublico(desde?: string, hasta?: string, pin?: string | null): Promise<ScorePublico> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    const headers = pin ? new HttpHeaders({ 'X-Asesor-Pin': pin }) : undefined;
    return firstValueFrom(this.http.get<ScorePublico>(`${environment.apiUrl}/score-publico`, { params, headers }));
  }
}
