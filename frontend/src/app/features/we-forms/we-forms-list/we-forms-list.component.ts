import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Desarrollo } from '../../../core/models/lead.model';
import { ResumenGeneralForm, WeFormLista } from '../../../core/models/we-form.model';
import { ToastService } from '../../../core/services/toast.service';
import { WeFormsService } from '../../../core/services/we-forms.service';
import { WeLoaderComponent } from '../../../shared/we-loader/we-loader.component';

/** We Forms: lista de formularios (registros de venta), cada uno con su link público. */
@Component({
  selector: 'app-we-forms-list',
  standalone: true,
  imports: [FormsModule, RouterLink, WeLoaderComponent],
  templateUrl: './we-forms-list.component.html',
})
export class WeFormsListComponent {
  private readonly service = inject(WeFormsService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly formularios = signal<WeFormLista[]>([]);
  readonly resumen = signal<ResumenGeneralForm | null>(null);
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

  async copiarLink(slug: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.linkDe(slug));
      this.toast.success('Link copiado.');
    } catch {
      this.toast.error('No se pudo copiar. Cópialo a mano: ' + this.linkDe(slug));
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
