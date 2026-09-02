import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Comision, ComisionConfig } from '../models/comision.model';

@Injectable({ providedIn: 'root' })
export class ComisionesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/comisiones`;

  listar(): Promise<Comision[]> {
    return firstValueFrom(this.http.get<Comision[]>(this.baseUrl));
  }

  configuracion(): Promise<ComisionConfig> {
    return firstValueFrom(this.http.get<ComisionConfig>(`${this.baseUrl}/configuracion`));
  }

  actualizarConfiguracion(porcentaje: number): Promise<ComisionConfig> {
    return firstValueFrom(this.http.put<ComisionConfig>(`${this.baseUrl}/configuracion`, { porcentaje }));
  }

  marcarPagada(id: number, pagada: boolean): Promise<Comision> {
    return firstValueFrom(this.http.put<Comision>(`${this.baseUrl}/${id}/pagar`, { pagada }));
  }
}
