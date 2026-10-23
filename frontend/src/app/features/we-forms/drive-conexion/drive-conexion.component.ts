import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideExternalLink, LucideHardDrive } from '@lucide/angular';
import { EstadoDrive } from '../../../core/models/drive.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { DriveService } from '../../../core/services/drive.service';
import { ToastService } from '../../../core/services/toast.service';
import { elegirCarpetaDeDrive } from '../../../core/utils/google-picker';

/** Mensajes al volver de Google (?drive=… lo pone el servidor al terminar la conexión). */
const AL_VOLVER: Record<string, { tipo: 'success' | 'aviso' | 'error'; texto: string }> = {
  conectado: { tipo: 'success', texto: 'Google Drive conectado. Los archivos empiezan a subirse en un minuto.' },
  cancelado: { tipo: 'aviso', texto: 'No se conectó Google Drive: se canceló en la pantalla de Google.' },
  vencido: { tipo: 'error', texto: 'La conexión con Google tardó demasiado. Vuelve a pulsar "Conectar Google Drive".' },
  error: { tipo: 'error', texto: 'Google no completó la conexión. Vuelve a intentarlo.' },
  'otra-cuenta': {
    tipo: 'error',
    texto: 'Esa no es la cuenta donde ya están guardados los archivos. Conecta la misma cuenta de Google de antes.',
  },
};

/**
 * Recuadro de We Forms (solo admin): conecta la cuenta de Google Drive, elige con el selector de Google la carpeta
 * existente donde van los expedientes (una subcarpeta por cliente) y muestra cuántos archivos van subidos y cuántos
 * faltan.
 */
@Component({
  selector: 'app-drive-conexion',
  standalone: true,
  imports: [DatePipe, LucideExternalLink, LucideHardDrive],
  templateUrl: './drive-conexion.component.html',
})
export class DriveConexionComponent implements OnInit {
  private readonly drive = inject(DriveService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly estado = signal<EstadoDrive | null>(null);
  readonly cargando = signal(true);
  readonly trabajando = signal(false);
  readonly errorCarga = signal(false);

  ngOnInit(): void {
    const regreso = this.route.snapshot.queryParamMap.get('drive');
    const aviso = regreso ? AL_VOLVER[regreso] : undefined;
    if (aviso) {
      this.toast[aviso.tipo](aviso.texto);
      // Quita ?drive= para que recargar la página no repita el aviso.
      void this.router.navigate([], { queryParams: { drive: null }, queryParamsHandling: 'merge', replaceUrl: true });
    }
    void this.cargar();
  }

  async cargar(): Promise<void> {
    try {
      this.estado.set(await this.drive.estado());
      this.errorCarga.set(false);
    } catch {
      this.errorCarga.set(true);
    } finally {
      this.cargando.set(false);
    }
  }

  async conectar(): Promise<void> {
    this.trabajando.set(true);
    try {
      window.location.href = await this.drive.urlParaConectar();
    } catch (e) {
      this.toast.error(this.mensajeDe(e, 'No se pudo iniciar la conexión con Google.'));
      this.trabajando.set(false);
    }
  }

  /** Abre el selector de Google; la carpeta elegida queda autorizada para la app y el servidor la revisa. */
  async elegirCarpeta(): Promise<void> {
    this.trabajando.set(true);
    try {
      const elegida = await elegirCarpetaDeDrive(await this.drive.datosSelector());
      if (!elegida) return;
      this.estado.set(await this.drive.elegirCarpeta(elegida.id));
      this.toast.success(`Los expedientes se guardarán en "${elegida.nombre}".`);
    } catch (e) {
      this.toast.error(this.mensajeDe(e, 'No se pudo abrir el selector de Google Drive. Intenta de nuevo.'));
    } finally {
      this.trabajando.set(false);
    }
  }

  async desconectar(): Promise<void> {
    const ok = await this.confirm.confirm({
      titulo: 'Desconectar Google Drive',
      mensaje:
        'Los archivos que ya están en Drive se quedan ahí, pero el panel no podrá abrirlos hasta que vuelvas a conectar la misma cuenta. Los nuevos se guardarán en el servidor mientras tanto.',
      textoConfirmar: 'Desconectar',
      peligroso: true,
    });
    if (!ok) return;
    await this.accion(async () => {
      await this.drive.desconectar();
      this.estado.set(await this.drive.estado());
      this.toast.success('Google Drive desconectado.');
    });
  }

  async subirAhora(): Promise<void> {
    await this.accion(async () => this.estado.set(await this.drive.sincronizar()));
  }

  async reintentar(): Promise<void> {
    await this.accion(async () => this.estado.set(await this.drive.reintentar()));
  }

  private async accion(fn: () => Promise<void>): Promise<void> {
    this.trabajando.set(true);
    try {
      await fn();
    } catch (e) {
      this.toast.error(this.mensajeDe(e, 'Google Drive no respondió. Intenta de nuevo.'));
    } finally {
      this.trabajando.set(false);
    }
  }

  private mensajeDe(e: unknown, porDefecto: string): string {
    return e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : porDefecto;
  }
}
