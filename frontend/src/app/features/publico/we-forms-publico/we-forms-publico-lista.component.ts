import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WeFormPublicoLista } from '../../../core/models/we-form.model';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';
import { DesarrolloLoaderComponent } from '../../../shared/desarrollo-loader/desarrollo-loader.component';
import { PinPublicoComponent } from '../../../shared/pin-publico/pin-publico.component';

type Proyecto = 'samai' | 'nanuu';
const NOMBRES: Record<Proyecto, string> = { samai: 'SAMAI Campestre', nanuu: 'Aldea Nanuu' };

/** /we-forms?proyecto=samai: a donde lleva "Registrar venta" del plano. Sin ?proyecto (desde el cotizador público)
 * muestra los formularios de los dos desarrollos a los que el PIN tenga acceso. Pide el PIN y, si hay más de un
 * formulario activo, deja elegir; con uno solo va directo a él. */
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

  /** null = sin desarrollo en el enlace: se muestran los dos. */
  readonly proyecto: Proyecto | null = leerProyecto(this.route.snapshot.queryParamMap.get('proyecto'));

  readonly formularios = signal<(WeFormPublicoLista & { proyecto: Proyecto })[] | null>(null);
  readonly grupos = computed(() => {
    const lista = this.formularios() ?? [];
    return (['samai', 'nanuu'] as const)
      .map((p) => ({ proyecto: p, nombre: NOMBRES[p], formularios: lista.filter((f) => f.proyecto === p) }))
      .filter((g) => g.formularios.length > 0);
  });
  readonly titulo = this.proyecto ? `Registrar venta · ${NOMBRES[this.proyecto]}` : 'Registrar venta';
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

  /** Los formularios del desarrollo del enlace o de los dos. Un asesor externo puede tener acceso a solo uno: el otro
   * responde 403 y se omite; si el PIN no existe (404) o no sirve en ninguno, se muestra ese error. */
  private async listar(pin: string): Promise<(WeFormPublicoLista & { proyecto: Proyecto })[]> {
    const pedir = async (p: Proyecto) => (await this.service.listar(p, pin)).map((f) => ({ ...f, proyecto: p }));
    if (this.proyecto) return pedir(this.proyecto);
    const resultado: (WeFormPublicoLista & { proyecto: Proyecto })[] = [];
    let sinAcceso: unknown = null;
    let conAcceso = false;
    for (const p of ['samai', 'nanuu'] as const) {
      try {
        resultado.push(...(await pedir(p)));
        conAcceso = true;
      } catch (e) {
        if (!(e instanceof HttpErrorResponse && e.status === 403)) throw e;
        sinAcceso = e;
      }
    }
    if (!conAcceso) throw sinAcceso;
    return resultado;
  }

  async entrar(pin: string): Promise<void> {
    this.verificando.set(true);
    this.error.set(null);
    try {
      const lista = await this.listar(pin);
      this.service.guardarPin(pin);
      // Con un solo formulario no hay nada que elegir: se va directo a él (sin dejar esta pantalla en el historial,
      // para que "atrás" regrese al plano).
      if (lista.length === 1) {
        await this.router.navigate(['/we-forms', lista[0].slug], { queryParams: { proyecto: lista[0].proyecto }, replaceUrl: true });
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

function leerProyecto(valor: string | null): Proyecto | null {
  return valor === 'samai' || valor === 'nanuu' ? valor : null;
}
