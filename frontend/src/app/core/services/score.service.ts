import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RankingScore } from '../models/score.model';

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
}
