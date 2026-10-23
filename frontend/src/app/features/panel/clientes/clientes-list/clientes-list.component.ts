import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideArrowDown, LucideArrowUp } from '@lucide/angular';
import { ClienteLista, etiquetaNumeros } from '../../../../core/models/cliente.model';
import { Cliente } from '../../../../core/models/cliente.model';
import { ClientesService } from '../../../../core/services/clientes.service';
import { ClienteFormModalComponent } from '../../../../shared/cliente-form-modal/cliente-form-modal.component';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

type FiltroEstado = 'TODOS' | 'ACTIVOS' | 'INACTIVOS' | 'INCOMPLETOS' | 'CON_SALDO';

@Component({
  selector: 'app-clientes-list',
  imports: [WeLoaderComponent, FormsModule, RouterLink, ClienteFormModalComponent, LucideArrowDown, LucideArrowUp],
  templateUrl: './clientes-list.component.html',
})
export class ClientesListComponent {
  private readonly clientesService = inject(ClientesService);
  private readonly router = inject(Router);

  readonly clientes = signal<ClienteLista[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly busqueda = signal('');
  readonly filtroEstado = signal<FiltroEstado>('ACTIVOS');
  readonly filtroDesarrollo = signal('');
  readonly registrando = signal(false);
  /** Orden por número de cliente (SAMAI primero, luego Nanuu): desc = el número mayor arriba dentro de cada uno. */
  readonly orden = signal<'asc' | 'desc'>('desc');
  readonly etiquetaNumeros = etiquetaNumeros;

  readonly desarrollos = computed(() => [...new Set(this.clientes().flatMap((c) => c.desarrollos))].sort());

  readonly filtrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const estado = this.filtroEstado();
    const desarrollo = this.filtroDesarrollo();
    const signo = this.orden() === 'asc' ? 1 : -1;
    return this.clientes().filter((c) => {
      // Se busca por "S-40", "s40", "40", nombre, teléfono o correo.
      const numeros = c.numeros.map((n) => `${n.etiqueta} ${n.prefijo}${n.numero}`).join(' ');
      if (texto && !`${numeros} ${c.nombreCompleto} ${c.telefono ?? ''} ${c.correo ?? ''}`.toLowerCase().includes(texto)) return false;
      if (estado === 'ACTIVOS' && !c.activo) return false;
      if (estado === 'INACTIVOS' && c.activo) return false;
      if (estado === 'INCOMPLETOS' && !c.datosIncompletos) return false;
      if (estado === 'CON_SALDO' && c.saldoPendiente <= 0) return false;
      if (desarrollo && !c.desarrollos.includes(desarrollo)) return false;
      return true;
    }).sort((a, b) => {
      const [ga, na] = this.claveOrden(a, desarrollo);
      const [gb, nb] = this.claveOrden(b, desarrollo);
      // El grupo (SAMAI, Nanuu, sin número) siempre en ese orden; dentro, según la flecha.
      return ga - gb || signo * (na - nb) || a.nombreCompleto.localeCompare(b.nombreCompleto, 'es');
    });
  });

  /** [grupo, número]: SAMAI (0) primero, luego Nanuu (1) y al final quienes aún no compran (2). Con un desarrollo
   * filtrado, solo cuenta su numeración. */
  private claveOrden(c: ClienteLista, desarrollo: string): [number, number] {
    if (desarrollo) {
      const n = c.numeros.find((x) => x.desarrollo === desarrollo);
      return n ? [0, n.numero] : [2, 0];
    }
    const s = c.numeros.find((x) => x.prefijo === 'S');
    if (s) return [0, s.numero];
    const n = c.numeros.find((x) => x.prefijo === 'N');
    return n ? [1, n.numero] : [2, 0];
  }

  alternarOrden(): void {
    this.orden.update((o) => (o === 'desc' ? 'asc' : 'desc'));
  }

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      this.clientes.set(await this.clientesService.listar());
    } catch {
      this.error.set('No se pudieron cargar los clientes.');
    } finally {
      this.cargando.set(false);
    }
  }

  alGuardar(c: Cliente): void {
    this.registrando.set(false);
    void this.router.navigate(['/panel/clientes', c.id]);
  }

  irAExistente(id: number): void {
    this.registrando.set(false);
    void this.router.navigate(['/panel/clientes', id]);
  }

  money(valor: number): string {
    return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
