import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import { ResumenGeneralForm, WeFormLista } from '../../../core/models/we-form.model';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { WeFormsService } from '../../../core/services/we-forms.service';
import { Comercializadora, enlaceDeComercializadora } from '../../../core/models/comercializadora.model';
import { ComercializadorasService } from '../../../core/services/comercializadoras.service';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';
import { DriveConexionComponent } from '../drive-conexion/drive-conexion.component';

/** We Forms: lista de formularios (registros de venta), cada uno con su link público. */
@Component({
  selector: 'app-we-forms-list',
  standalone: true,
  imports: [FormsModule, RouterLink, WeLoaderComponent, DriveConexionComponent],
  templateUrl: './we-forms-list.component.html',
})
export class WeFormsListComponent {
  private readonly service = inject(WeFormsService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly comercializadorasService = inject(ComercializadorasService);
  private readonly auth = inject(AuthService);

  /** La conexión con Google Drive (dónde se guardan los archivos de los registros) solo la maneja el admin. */
  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly formularios = signal<WeFormLista[]>([]);
  readonly resumen = signal<ResumenGeneralForm | null>(null);
  /** Altas de equipos de comercializadoras: solo se llena si quien mira también tiene Asesores externos. */
  readonly comercializadoras = signal<Comercializadora[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly mostrarNuevo = signal(false);
  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly nuevoNombre = signal('');
  readonly nuevoDescripcion = signal('');
  readonly nuevoDesarrolloId = signal<number | null>(null);
  readonly creando = signal(false);
  readonly errorNuevo = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.formularios.set(await this.service.listar());
      this.resumen.set(await this.service.resumen());
      // Sin el módulo Asesores externos el servidor responde 403: simplemente no se muestra la sección.
      this.comercializadoras.set(await this.comercializadorasService.listar().catch(() => []));
    } catch {
      this.errorMessage.set('No se pudieron cargar los formularios. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** El link público que se comparte con los asesores (pide su PIN al abrirse). */
  linkDe(slug: string): string {
    return `${window.location.origin}/we-forms/${slug}`;
  }

  async copiarEnlaceComercializadora(c: Comercializadora): Promise<void> {
    try {
      await navigator.clipboard.writeText(enlaceDeComercializadora(c.token));
      this.toast.success('Enlace copiado. Compártelo con la líder de la comercializadora.');
    } catch {
      this.toast.error('No se pudo copiar el enlace.');
    }
  }

  async copiarLink(slug: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.linkDe(slug));
      this.toast.success('Link copiado.');
    } catch {
      this.toast.error('No se pudo copiar. Cópialo a mano: ' + this.linkDe(slug));
    }
  }

  // --- Duplicar un formulario (mismas preguntas, otro desarrollo) ---
  readonly duplicando = signal<WeFormLista | null>(null);
  readonly dupNombre = signal('');
  readonly dupDescripcion = signal('');
  readonly dupDesarrolloId = signal<number | null>(null);
  readonly guardandoDup = signal(false);
  readonly cargandoDup = signal(false);
  readonly errorDup = signal<string | null>(null);

  /** Abre el diálogo con el nombre y la descripción del original; el desarrollo viene elegido en el OTRO desarrollo
   * (para copiar el formulario de SAMAI a Aldea Nanuu y al revés). */
  async abrirDuplicar(f: WeFormLista): Promise<void> {
    this.duplicando.set(f);
    this.dupNombre.set(f.nombre);
    this.dupDescripcion.set('');
    this.dupDesarrolloId.set(null);
    this.errorDup.set(null);
    // El diálogo se abre al instante; "Duplicar formulario" queda bloqueado hasta que lleguen los datos, así no se
    // puede confirmar antes de que esté la descripción del original.
    this.cargandoDup.set(true);
    try {
      if (this.desarrollos().length === 0) this.desarrollos.set(await this.service.desarrollos());
      const otro = this.desarrollos().find((d) => d.id !== f.desarrolloId);
      this.dupDesarrolloId.set((otro ?? this.desarrollos()[0])?.id ?? null);
      // La lista no trae la descripción: se pide el detalle para que la copia parta de la del original.
      this.dupDescripcion.set((await this.service.obtener(f.id)).descripcion ?? '');
    } catch {
      this.errorDup.set('No se pudieron cargar todos los datos del formulario. Revisa el desarrollo y la descripción.');
    } finally {
      this.cargandoDup.set(false);
    }
  }

  async duplicar(): Promise<void> {
    const original = this.duplicando();
    const nombre = this.dupNombre().trim();
    const desarrolloId = this.dupDesarrolloId();
    if (!original || this.guardandoDup() || this.cargandoDup()) return;
    if (!nombre || desarrolloId === null) {
      this.errorDup.set('Escribe un nombre y elige el desarrollo.');
      return;
    }
    this.guardandoDup.set(true);
    this.errorDup.set(null);
    try {
      const copia = await this.service.duplicar(original.id, {
        nombre,
        descripcion: this.dupDescripcion().trim() || null,
        desarrolloId,
      });
      this.toast.success('Formulario duplicado. Revisa sus preguntas y comparte su link.');
      this.duplicando.set(null);
      await this.router.navigate(['/panel/we-forms', copia.id]);
    } catch (error) {
      this.errorDup.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo duplicar el formulario.',
      );
    } finally {
      this.guardandoDup.set(false);
    }
  }

  async abrirNuevo(): Promise<void> {
    this.nuevoNombre.set('');
    this.nuevoDescripcion.set('');
    this.errorNuevo.set(null);
    this.mostrarNuevo.set(true);
    if (this.desarrollos().length === 0) {
      try {
        const lista = await this.service.desarrollos();
        this.desarrollos.set(lista);
        if (lista.length === 1) this.nuevoDesarrolloId.set(lista[0].id);
      } catch {
        this.errorNuevo.set('No se pudieron cargar los desarrollos.');
      }
    }
  }

  async crear(): Promise<void> {
    const nombre = this.nuevoNombre().trim();
    const desarrolloId = this.nuevoDesarrolloId();
    if (!nombre || desarrolloId === null || this.creando()) {
      this.errorNuevo.set('Escribe un nombre y elige el desarrollo.');
      return;
    }
    this.creando.set(true);
    this.errorNuevo.set(null);
    try {
      const form = await this.service.crear({ nombre, descripcion: this.nuevoDescripcion().trim() || null, desarrolloId });
      await this.router.navigate(['/panel/we-forms', form.id]);
    } catch (error) {
      this.errorNuevo.set(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo crear el formulario.',
      );
    } finally {
      this.creando.set(false);
    }
  }
}
