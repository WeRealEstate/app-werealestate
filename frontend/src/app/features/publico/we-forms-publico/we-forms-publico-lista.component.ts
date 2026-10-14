import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WeFormPublicoLista } from '../../../core/models/we-form.model';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';
import { PinPublicoComponent } from '../../../shared/pin-publico/pin-publico.component';

/** /we-forms?proyecto=samai: a donde lleva el botón "Precontrato" del plano. Pide el PIN y muestra los
 * formularios activos de ese desarrollo para elegir. */
@Component({
  selector: 'app-we-forms-publico-lista',
  standalone: true,
  imports: [RouterLink, PinPublicoComponent],
  templateUrl: './we-forms-publico-lista.component.html',
})
export class WeFormsPublicoListaComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(WeFormsPublicoService);

  readonly proyecto = this.route.snapshot.queryParamMap.get('proyecto') === 'nanuu' ? 'nanuu' : 'samai';
  readonly nombreProyecto = this.proyecto === 'nanuu' ? 'Aldea Nanuu' : 'SAMAI Campestre';

  readonly formularios = signal<WeFormPublicoLista[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly verificando = signal(false);

  constructor() {
    const pin = this.service.pin();
    if (pin) void this.entrar(pin);
  }

  /** Olvida el PIN de este dispositivo para que otro asesor pueda entrar con el suyo. */
  salir(): void {
    this.service.guardarPin(null);
    this.formularios.set(null);
    this.error.set(null);
  }

  async entrar(pin: string): Promise<void> {
    this.verificando.set(true);
    this.error.set(null);
    try {
      this.formularios.set(await this.service.listar(this.proyecto, pin));
      this.service.guardarPin(pin);
    } catch (e) {
      // Solo se olvida el PIN si el servidor lo rechazó; un fallo de red no debe borrarlo.
      if (e instanceof HttpErrorResponse && (e.status === 403 || e.status === 404)) this.service.guardarPin(null);
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo verificar el PIN.',
      );
    } finally {
      this.verificando.set(false);
    }
  }
}
