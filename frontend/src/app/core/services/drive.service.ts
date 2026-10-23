import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DatosSelectorDrive, EstadoDrive } from '../models/drive.model';

/** Conexión con Google Drive (solo admin): los archivos de Registrar venta se guardan en una carpeta por cliente. */
@Injectable({ providedIn: 'root' })
export class DriveService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/drive`;

  estado(): Promise<EstadoDrive> {
    return firstValueFrom(this.http.get<EstadoDrive>(`${this.baseUrl}/estado`));
  }

  /** La dirección de Google para elegir la cuenta; el navegador se va ahí y Google regresa al panel. */
  async urlParaConectar(): Promise<string> {
    const r = await firstValueFrom(this.http.post<{ url: string }>(`${this.baseUrl}/conectar`, {}));
    return r.url;
  }

  datosSelector(): Promise<DatosSelectorDrive> {
    return firstValueFrom(this.http.get<DatosSelectorDrive>(`${this.baseUrl}/selector`));
  }

  /** Guarda la carpeta elegida con el selector (el servidor revisa que exista y se pueda escribir). */
  elegirCarpeta(id: string): Promise<EstadoDrive> {
    return firstValueFrom(this.http.put<EstadoDrive>(`${this.baseUrl}/carpeta`, { id }));
  }

  desconectar(): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.baseUrl}/desconectar`, {}));
  }

  sincronizar(): Promise<EstadoDrive> {
    return firstValueFrom(this.http.post<EstadoDrive>(`${this.baseUrl}/sincronizar`, {}));
  }

  reintentar(): Promise<EstadoDrive> {
    return firstValueFrom(this.http.post<EstadoDrive>(`${this.baseUrl}/reintentar`, {}));
  }
}
