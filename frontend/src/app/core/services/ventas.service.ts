import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../models/pagina.model';
import { Venta, VentaCreateRequest } from '../models/venta.model';

export interface BuscarVentasParams {
  busqueda?: string;
  pagina: number;
  tamano: number;
}

@Injectable({ providedIn: 'root' })
export class VentasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/ventas`;

  buscarPaginado(params: BuscarVentasParams): Promise<Pagina<Venta>> {
    let httpParams = new HttpParams().set('pagina', params.pagina).set('tamano', params.tamano);
    if (params.busqueda) httpParams = httpParams.set('busqueda', params.busqueda);

    return firstValueFrom(this.http.get<Pagina<Venta>>(`${this.baseUrl}/buscar`, { params: httpParams }));
  }

  crear(request: VentaCreateRequest): Promise<Venta> {
    return firstValueFrom(this.http.post<Venta>(this.baseUrl, request));
  }
}
