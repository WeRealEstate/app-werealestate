import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Comercializadora,
  IntegranteComercializadora,
  enlaceDeComercializadora,
} from '../../core/models/comercializadora.model';
import { AsesorExterno } from '../../core/models/asesor-externo.model';
import { AsesoresExternosService } from '../../core/services/asesores-externos.service';
import { ComercializadorasService } from '../../core/services/comercializadoras.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { WeLoaderComponent } from '../../shared/we-loader/we-loader.component';

/** Comercializadoras (menú, debajo de Asesores externos): cada una tiene una líder (asesor externo) y un enlace
 * que la líder comparte para que cada integrante de su equipo se dé de alta solo y aparezca en su equipo de
 * Comunidades We. */
@Component({
  selector: 'app-comercializadoras-list',
  standalone: true,
  imports: [FormsModule, WeLoaderComponent],
  templateUrl: './comercializadoras-list.component.html',
})
export class ComercializadorasListComponent {
  private readonly service = inject(ComercializadorasService);
  private readonly asesoresService = inject(AsesoresExternosService);
  private readonly toast = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  readonly items = signal<Comercializadora[]>([]);
  readonly asesores = signal<AsesorExterno[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  // Formulario (crear o editar)
  readonly mostrarForm = signal(false);
  readonly editando = signal<Comercializadora | null>(null);
  readonly nombre = signal('');
  readonly liderId = signal<number | null>(null);
  readonly notas = signal('');
  readonly activa = signal(true);
  readonly guardando = signal(false);
  readonly errorForm = signal<string | null>(null);

  // Integrantes (modal)
  readonly verIntegrantes = signal<Comercializadora | null>(null);
  readonly integrantes = signal<IntegranteComercializadora[]>([]);
  readonly cargandoIntegrantes = signal(false);

  // Edición de un integrante (modal sobre el de integrantes)
  readonly integranteEnEdicion = signal<IntegranteComercializadora | null>(null);
  readonly intNombre = signal('');
  readonly intCelular = signal('');
  readonly intCorreo = signal('');
  readonly intPin = signal('');
  readonly intPinNuevo = signal(false);
  readonly guardandoIntegrante = signal(false);
  readonly errorIntegrante = signal<string | null>(null);

  /** Quién puede ser líder: asesores externos activos que no reportan a nadie (independientes o líderes), y que no
   * lo sean ya de otra comercializadora. */
  readonly lideresPosibles = computed(() => {
    const ocupados = new Set(
      this.items()
        .filter((c) => c.id !== this.editando()?.id)
        .map((c) => c.liderId),
    );
    return this.asesores().filter(
      (a) => a.activo && a.usuarioId === null && a.tipo !== 'LINEA' && !ocupados.has(a.id),
    );
  });

  readonly enlaceDe = enlaceDeComercializadora;

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      const [items, asesores] = await Promise.all([this.service.listar(), this.asesoresService.listar()]);
      this.items.set(items);
      this.asesores.set(asesores);
    } catch {
      this.errorMessage.set('No se pudieron cargar las comercializadoras. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }

  abrirNueva(): void {
    this.editando.set(null);
    this.nombre.set('');
    this.liderId.set(null);
    this.notas.set('');
    this.activa.set(true);
    this.errorForm.set(null);
    this.mostrarForm.set(true);
  }

  abrirEditar(c: Comercializadora): void {
    this.editando.set(c);
    this.nombre.set(c.nombre);
    this.liderId.set(c.liderId);
    this.notas.set(c.notas ?? '');
    this.activa.set(c.activa);
    this.errorForm.set(null);
    this.mostrarForm.set(true);
  }

  async guardar(): Promise<void> {
    if (this.guardando()) return;
    const liderId = this.liderId();
    if (!this.nombre().trim() || liderId === null) {
      this.errorForm.set('El nombre y la líder son obligatorios.');
      return;
    }
    this.guardando.set(true);
    this.errorForm.set(null);
    const base = { nombre: this.nombre().trim(), liderId, notas: this.notas().trim() || null };
    try {
      const editando = this.editando();
      const guardada = editando
        ? await this.service.actualizar(editando.id, { ...base, activa: this.activa() })
        : await this.service.crear(base);
      this.items.update((lista) =>
        editando ? lista.map((c) => (c.id === guardada.id ? guardada : c)) : [guardada, ...lista],
      );
      // Si la líder era independiente ahora es Líder: se refresca la lista de asesores para el selector.
      this.asesores.set(await this.asesoresService.listar());
      this.mostrarForm.set(false);
      this.toast.success(editando ? 'Comercializadora actualizada.' : 'Comercializadora creada. Ya puedes copiar su enlace.');
    } catch (e) {
      this.errorForm.set(mensajeDe(e, 'No se pudo guardar. Intenta de nuevo.'));
    } finally {
      this.guardando.set(false);
    }
  }

  async copiarEnlace(c: Comercializadora): Promise<void> {
    try {
      await navigator.clipboard.writeText(enlaceDeComercializadora(c.token));
      this.toast.success('Enlace copiado. Compártelo con la líder de la comercializadora.');
    } catch {
      this.toast.error('No se pudo copiar el enlace.');
    }
  }

  async regenerar(c: Comercializadora): Promise<void> {
    const ok = await this.confirmService.confirm({
      titulo: 'Generar un enlace nuevo',
      mensaje: `El enlace actual de ${c.nombre} dejará de funcionar y tendrás que compartir el nuevo. Los integrantes que ya se unieron no se afectan.`,
      textoConfirmar: 'Generar enlace nuevo',
    });
    if (!ok) return;
    try {
      const nueva = await this.service.regenerarEnlace(c.id);
      this.items.update((lista) => lista.map((x) => (x.id === nueva.id ? nueva : x)));
      this.toast.success('Enlace nuevo generado. Ya puedes copiarlo.');
    } catch (e) {
      this.toast.error(mensajeDe(e, 'No se pudo generar el enlace nuevo.'));
    }
  }

  async eliminar(c: Comercializadora): Promise<void> {
    const ok = await this.confirmService.confirm({
      titulo: 'Eliminar comercializadora',
      mensaje: `¿Eliminar a ${c.nombre}? Su enlace dejará de funcionar. Esta acción no se puede deshacer.`,
      textoConfirmar: 'Eliminar',
      peligroso: true,
    });
    if (!ok) return;
    try {
      await this.service.eliminar(c.id);
      this.items.update((lista) => lista.filter((x) => x.id !== c.id));
      this.toast.success('Comercializadora eliminada.');
    } catch (e) {
      this.toast.error(mensajeDe(e, 'No se pudo eliminar.'));
    }
  }

  // ---- Editar / quitar un integrante ----

  abrirEditarIntegrante(i: IntegranteComercializadora): void {
    this.integranteEnEdicion.set(i);
    this.intNombre.set(i.nombre);
    this.intCelular.set(i.celular ?? '');
    this.intCorreo.set(i.correo ?? '');
    this.intPin.set(i.pin ?? '');
    this.intPinNuevo.set(false);
    this.errorIntegrante.set(null);
  }

  /** Alterna entre dejar el PIN actual y pedir uno nuevo al azar (se genera en el servidor al guardar). */
  alternarPinNuevo(i: IntegranteComercializadora): void {
    if (this.intPinNuevo()) {
      this.intPinNuevo.set(false);
      this.intPin.set(i.pin ?? '');
    } else {
      this.intPinNuevo.set(true);
      this.intPin.set('');
    }
  }

  async guardarIntegrante(): Promise<void> {
    const c = this.verIntegrantes();
    const i = this.integranteEnEdicion();
    if (!c || !i || this.guardandoIntegrante()) return;
    if (this.intNombre().trim().length < 3) {
      this.errorIntegrante.set('Escribe el nombre completo.');
      return;
    }
    if (this.intCelular().replace(/\D/g, '').length !== 10) {
      this.errorIntegrante.set('El celular debe tener 10 dígitos.');
      return;
    }
    const pinEscrito = this.intPin().trim();
    // Sin cambios en el PIN no se manda; "nuevo" lo genera el servidor; si lo escribieron, se manda tal cual.
    const pin = this.intPinNuevo() ? '' : pinEscrito.toUpperCase() === (i.pin ?? '') ? null : pinEscrito;
    this.guardandoIntegrante.set(true);
    this.errorIntegrante.set(null);
    try {
      const guardado = await this.service.actualizarIntegrante(c.id, i.id, {
        nombre: this.intNombre().trim(),
        celular: this.intCelular().trim(),
        correo: this.intCorreo().trim(),
        pin,
      });
      this.integrantes.update((lista) => lista.map((x) => (x.id === guardado.id ? guardado : x)));
      this.integranteEnEdicion.set(null);
      this.toast.success(this.intPinNuevo() ? `Datos guardados. Su PIN nuevo es ${guardado.pin}.` : 'Integrante actualizado.');
    } catch (e) {
      this.errorIntegrante.set(mensajeDe(e, 'No se pudo guardar. Intenta de nuevo.'));
    } finally {
      this.guardandoIntegrante.set(false);
    }
  }

  async quitarIntegrante(i: IntegranteComercializadora): Promise<void> {
    const c = this.verIntegrantes();
    if (!c) return;
    const ok = await this.confirmService.confirm({
      titulo: 'Quitar integrante',
      mensaje: `¿Quitar a ${i.nombre} del equipo de ${c.liderNombre}? Si no tiene ventas ni comisiones se elimina por completo (sale de Comunidades We); si ya tiene historial solo se desactiva y su PIN deja de servir.`,
      textoConfirmar: 'Quitar',
      peligroso: true,
    });
    if (!ok) return;
    try {
      const r = await this.service.quitarIntegrante(c.id, i.id);
      this.toast.success(
        r.resultado === 'ELIMINADO'
          ? `${r.nombre} se eliminó del sistema.`
          : `${r.nombre} tiene historial (ventas, comisiones…): se desactivó y su PIN ya no sirve.`,
      );
      this.integrantes.set(await this.service.integrantes(c.id));
      const items = await this.service.listar();
      this.items.set(items);
      this.verIntegrantes.set(items.find((x) => x.id === c.id) ?? c);
    } catch (e) {
      this.toast.error(mensajeDe(e, 'No se pudo quitar al integrante.'));
    }
  }

  async abrirIntegrantes(c: Comercializadora): Promise<void> {
    this.verIntegrantes.set(c);
    this.integrantes.set([]);
    this.cargandoIntegrantes.set(true);
    try {
      this.integrantes.set(await this.service.integrantes(c.id));
    } catch {
      this.toast.error('No se pudieron cargar los integrantes.');
    } finally {
      this.cargandoIntegrantes.set(false);
    }
  }
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : porDefecto;
}
