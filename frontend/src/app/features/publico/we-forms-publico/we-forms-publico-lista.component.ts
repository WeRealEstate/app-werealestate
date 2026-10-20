import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WeFormPublicoLista } from '../../../core/models/we-form.model';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';
import { DesarrolloLoaderComponent } from '../../../shared/desarrollo-loader/desarrollo-loader.component';
import { PinPublicoComponent } from '../../../shared/pin-publico/pin-publico.component';

/** /we-forms?proyecto=samai: a donde lleva el botón "Registrar venta" del plano. Pide el PIN y, si el desarrollo
 * tiene más de un formulario activo, deja elegir; con uno solo va directo a él. */
@Component({
  selector: 'app-we-forms-publico-lista',
  standalone: true,
  imports: [RouterLink, PinPublicoComponent, DesarrolloLoaderComponent],
  templateUrl: './we-forms-publico-lista.component.html',
})
export class WeFormsPublicoListaComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(WeFormsPublicoService);

  readonly proyecto = this.route.snapshot.queryParamMap.get('proyecto') === 'nanuu' ? 'nanuu' : 'samai';
  readonly nombreProyecto = this.proyecto === 'nanuu' ? 'Aldea Nanuu' : 'SAMAI Campestre';

  readonly formularios = signal<WeFormPublicoLista[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly verificando = signal(false);
  /** true mientras se entra solo con el PIN recordado: así no se ve un instante la pantalla del PIN. */
  readonly entrandoSolo = signal(!!this.service.pin());

  constructor() {
    const pin = this.service.pin();
    if (pin) void this.entrar(pin).finally(() => this.entrandoSolo.set(false));
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
      const lista = await this.service.listar(this.proyecto, pin);
      this.service.guardarPin(pin);
      // Con un solo formulario no hay nada que elegir: se va directo a él (sin dejar esta pantalla en el historial,
      // para que "atrás" regrese al plano).
      if (lista.length === 1) {
        await this.router.navigate(['/we-forms', lista[0].slug], { queryParams: { proyecto: this.proyecto }, replaceUrl: true });
        return;
      }
      this.formularios.set(lista);
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
