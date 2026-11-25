import { Component, computed, inject, signal } from '@angular/core';
import { ConfigCorreos, TipoCorreo } from '../../core/models/venta.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { VentasService } from '../../core/services/ventas.service';

interface Interruptor {
  tipo: TipoCorreo;
  campo: keyof ConfigCorreos;
  nombre: string;
  explicacion: string;
}

const INTERRUPTORES: Interruptor[] = [
  { tipo: 'BIENVENIDA', campo: 'bienvenida', nombre: 'Bienvenida',
    explicacion: '10 minutos después de registrar la firma del contrato. SAMAI Campestre, solo en ventas registradas de ahora en adelante.' },
  { tipo: 'LIQUIDACION', campo: 'liquidacion', nombre: 'Liquidación (solo finanzas)',
    explicacion: '10 minutos después del abono que deja la venta pagada, aviso a finanzas para la carta finiquito. SAMAI y Aldea Nanuu, solo en ventas registradas de ahora en adelante.' },
  { tipo: 'RECORDATORIO', campo: 'recordatorio', nombre: 'Recordatorio de pago',
    explicacion: 'A las 6:00 p. m. del día anterior a la fecha de pago, a todas las ventas con mensualidades. Si lo enciendes después de las 6, los de mañana salen en ese momento.' },
];

/** Los interruptores de los correos automáticos, arriba de la lista de Ventas (admin y administración). */
@Component({
  selector: 'app-correos-automaticos',
  standalone: true,
  templateUrl: './correos-automaticos.component.html',
})
export class CorreosAutomaticosComponent {
  private readonly ventas = inject(VentasService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly interruptores = INTERRUPTORES;
  readonly config = signal<ConfigCorreos | null>(null);
  readonly guardando = signal(false);
  readonly encendidos = computed(() => {
    const c = this.config();
    return c ? INTERRUPTORES.filter((i) => c[i.campo]).length : 0;
  });

  constructor() {
    const rol = this.auth.currentUser()?.rol;
    if (rol !== 'ADMIN' && rol !== 'LIDER_AREA') return;
    this.ventas
      .configCorreos()
      .then((c) => this.config.set(c))
      .catch(() => this.config.set(null));
  }

  async cambiar(i: Interruptor, casilla: HTMLInputElement): Promise<void> {
    this.guardando.set(true);
    try {
      this.config.set(await this.ventas.cambiarConfigCorreo(i.tipo, casilla.checked));
      this.toast.success(`Correo de ${i.nombre.toLowerCase()}: envío automático ${casilla.checked ? 'encendido' : 'apagado'}.`);
    } catch {
      casilla.checked = !casilla.checked;
      this.toast.error('No se pudo guardar.');
    } finally {
      this.guardando.set(false);
    }
  }
}
