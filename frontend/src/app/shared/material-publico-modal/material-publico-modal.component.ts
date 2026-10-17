import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import {
  CATEGORIAS_MATERIAL,
  CategoriaMaterial,
  MaterialItem,
  etiquetaCategoria,
  tamanoLegible,
} from '../../core/models/material.model';
import { MaterialService } from '../../core/services/material.service';
import { ToastService } from '../../core/services/toast.service';
import { WeFormsPublicoService } from '../../core/services/we-forms-publico.service';
import { descargarBlob } from '../../core/utils/descargar-blob';
import { MaterialMiniaturaComponent } from '../material-miniatura/material-miniatura.component';
import { MaterialVisorComponent } from '../material-visor/material-visor.component';
import { WeLoaderComponent } from '../we-loader/we-loader.component';
import { PinPublicoComponent } from '../pin-publico/pin-publico.component';

/** Material de venta en el plano público: lo que administración sube para el desarrollo del plano. Usa el
 * mismo PIN que recuerda We Forms (si no hay uno guardado, o el servidor lo rechaza, lo pide una vez). */
@Component({
  selector: 'app-material-publico-modal',
  standalone: true,
  imports: [WeLoaderComponent, PinPublicoComponent, MaterialMiniaturaComponent, MaterialVisorComponent],
  templateUrl: './material-publico-modal.component.html',
})
export class MaterialPublicoModalComponent implements OnInit {
  private readonly service = inject(MaterialService);
  private readonly weFormsPublico = inject(WeFormsPublicoService);
  private readonly toast = inject(ToastService);

  /** "samai" o "nanuu" (el de la URL del plano). */
  readonly proyecto = input.required<string>();
  readonly cerrar = output<void>();

  readonly etiquetaCategoria = etiquetaCategoria;
  readonly tamanoLegible = tamanoLegible;

  readonly items = signal<MaterialItem[]>([]);
  readonly cargando = signal(false);
  readonly pedirPin = signal(false);
  readonly error = signal<string | null>(null);
  readonly filtro = signal<CategoriaMaterial | ''>('');
  readonly trabajando = signal<number | null>(null);

  readonly categoriasPresentes = computed(() =>
    CATEGORIAS_MATERIAL.filter((c) => this.items().some((m) => m.categoria === c.valor)),
  );
  readonly visibles = computed(() => this.items().filter((m) => !this.filtro() || m.categoria === this.filtro()));

  ngOnInit(): void {
    const pin = this.weFormsPublico.pin();
    if (pin) void this.cargar(pin);
    else this.pedirPin.set(true);
  }

  enviarPin(pin: string): void {
    void this.cargar(pin);
  }

  private async cargar(pin: string): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.items.set(await this.service.listarPublico(this.proyecto(), pin));
      this.weFormsPublico.guardarPin(pin);
      this.pedirPin.set(false);
    } catch (e) {
      const estado = e instanceof HttpErrorResponse ? e.status : 0;
      const mensaje = e instanceof HttpErrorResponse ? e.error?.message : null;
      if (estado === 404 || estado === 403) {
        // PIN que ya no vale (o sin acceso a este desarrollo): se olvida y se vuelve a pedir.
        this.weFormsPublico.guardarPin(null);
        this.pedirPin.set(true);
        this.error.set(estado === 404 ? 'PIN incorrecto.' : typeof mensaje === 'string' ? mensaje : 'No tienes acceso.');
      } else if (estado === 429) {
        this.pedirPin.set(true);
        this.error.set('Demasiados intentos. Espera unos minutos e inténtalo de nuevo.');
      } else {
        this.error.set('No se pudo cargar el material. Intenta de nuevo.');
      }
    } finally {
      this.cargando.set(false);
    }
  }

  async copiarTexto(m: MaterialItem): Promise<void> {
    try {
      await navigator.clipboard.writeText(m.texto ?? '');
      this.toast.success('Texto copiado.');
    } catch {
      this.toast.error('No se pudo copiar el texto.');
    }
  }

  /** Posición en la lista visible del material que se está viendo en el visor (null = visor cerrado). */
  readonly visorIndice = signal<number | null>(null);

  readonly cargarArchivo = async (m: MaterialItem): Promise<Blob> =>
    this.service.archivoPublico(m.id, this.proyecto(), this.weFormsPublico.pin() ?? '');
  readonly cargarMiniatura = async (m: MaterialItem): Promise<Blob> =>
    this.service.miniaturaPublico(m.id, this.proyecto(), this.weFormsPublico.pin() ?? '');

  abrirVisor(m: MaterialItem): void {
    const i = this.visibles().findIndex((x) => x.id === m.id);
    if (i >= 0) this.visorIndice.set(i);
  }

  async descargar(m: MaterialItem): Promise<void> {
    const blob = await this.obtener(m);
    if (blob) descargarBlob(blob, m.archivoNombre ?? 'material');
  }

  private async obtener(m: MaterialItem): Promise<Blob | null> {
    const pin = this.weFormsPublico.pin();
    if (!pin || this.trabajando() !== null) return null;
    this.trabajando.set(m.id);
    try {
      return await this.service.archivoPublico(m.id, this.proyecto(), pin);
    } catch {
      this.toast.error('No se pudo obtener el archivo. Intenta de nuevo.');
      return null;
    } finally {
      this.trabajando.set(null);
    }
  }
}
