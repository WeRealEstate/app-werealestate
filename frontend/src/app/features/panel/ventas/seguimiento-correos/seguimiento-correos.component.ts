import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeguimientoCorreo, SeguimientoCorreos, VentasService } from '../../../../core/services/ventas.service';
import { WeLoaderComponent } from '../../../../shared/we-loader/we-loader.component';

type Filtro = 'todos' | 'enviados' | 'pendientes' | 'fallidos';

@Component({
  selector: 'app-seguimiento-correos',
  standalone: true,
  imports: [DatePipe, RouterLink, WeLoaderComponent],
  templateUrl: './seguimiento-correos.component.html',
})
export class SeguimientoCorreosComponent {
  private readonly ventas = inject(VentasService);
  readonly datos = signal<SeguimientoCorreos | null>(null);
  readonly error = signal(false);
  readonly cargando = signal(true);
  readonly filtro = signal<Filtro>('todos');
  readonly filtros: { valor: Filtro; etiqueta: string }[] = [
    { valor: 'todos', etiqueta: 'Todos' }, { valor: 'enviados', etiqueta: 'Enviados' },
    { valor: 'pendientes', etiqueta: 'Por enviar' }, { valor: 'fallidos', etiqueta: 'No enviados' },
  ];
  readonly visibles = computed(() => (this.datos()?.entradas ?? []).filter((e) => {
    switch (this.filtro()) {
      case 'enviados': return e.estado === 'ENVIADO';
      case 'pendientes': return e.estado === 'PENDIENTE' || e.estado === 'PREVISTO';
      case 'fallidos': return e.estado === 'FALLIDO' || e.estado === 'NO_ENVIADO';
      default: return true;
    }
  }));
  readonly mes = computed(() => {
    const valor = this.datos()?.mes;
    if (!valor) return '';
    const [anio, mes] = valor.split('-').map(Number);
    return new Intl.DateTimeFormat('es-MX', { month: 'long', year: 'numeric' }).format(new Date(anio, mes - 1, 1));
  });

  constructor() { void this.cargar(); }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(false);
    try { this.datos.set(await this.ventas.seguimientoCorreos()); }
    catch { this.error.set(true); }
    finally { this.cargando.set(false); }
  }

  tipo(e: SeguimientoCorreo): string {
    return ({ BIENVENIDA: 'Bienvenida', RECORDATORIO: 'Recordatorio de pago', LIQUIDACION: 'Liquidación', REGISTRO_VENTA: 'Registro de venta' } as Record<string, string>)[e.tipo] ?? e.tipo;
  }

  estado(e: SeguimientoCorreo): string {
    return ({ ENVIADO: 'Enviado', PENDIENTE: 'En cola', PREVISTO: 'Previsto', FALLIDO: 'Fallido', NO_ENVIADO: 'No enviado' })[e.estado];
  }
}
