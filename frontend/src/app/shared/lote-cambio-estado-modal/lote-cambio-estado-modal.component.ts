import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ESTADO_LOTE_BADGE_CLASSES,
  ESTADO_LOTE_LABELS,
  EstadoLote,
  Lote,
} from '../../core/models/lote.model';
import { LotesService } from '../../core/services/lotes.service';
import { ToastService } from '../../core/services/toast.service';
import {
  HORAS_OPCIONES,
  HORA_POR_DEFECTO,
  MINUTOS_OPCIONES,
  MINUTO_POR_DEFECTO,
  combinarFechaHora,
} from '../../core/utils/fecha-hora';
import { MonedaInputDirective } from '../moneda-input/moneda-input.directive';

/** Al mover un lote a cualquiera de estos estados el nombre del cliente es obligatorio: son los
 * estados donde alguien real está comprometido con el lote, y sin esto ese dato solo quedaba
 * enterrado (si acaso) en la nota libre. */
const ESTADOS_REQUIEREN_CLIENTE: EstadoLote[] = [
  'APARTADO',
  'APARTADO_A_PLAZO',
  'APARTADO_CON_DINERO',
  'EN_PROCESO_DE_FIRMA',
  'VENDIDO',
];

/** Modal que un admin o líder de área confirma al mover un lote a otro estado (un asesor nunca pasa
 * por aquí, su cambio se aplica directo). Pide siempre una nota, y según el estado destino: fecha y
 * hora de vencimiento (Apartado a plazo), monto (Apartado con dinero) y nombre del cliente. Lo usan
 * /panel/lotes y /panel/plano para que ambos se comporten idéntico; el padre solo lo monta con un
 * @if y reacciona a `confirmado` (con el lote ya actualizado) o `cancelado`. */
@Component({
  selector: 'app-lote-cambio-estado-modal',
  standalone: true,
  imports: [MonedaInputDirective, FormsModule],
  templateUrl: './lote-cambio-estado-modal.component.html',
})
export class LoteCambioEstadoModalComponent {
  private readonly lotesService = inject(LotesService);
  private readonly toast = inject(ToastService);

  readonly lote = input.required<Lote>();
  readonly nuevoEstado = input.required<EstadoLote>();
  readonly confirmado = output<Lote>();
  readonly cancelado = output<void>();

  readonly estadoLabels = ESTADO_LOTE_LABELS;
  readonly badgeClases = ESTADO_LOTE_BADGE_CLASSES;
  readonly horasOpciones = HORAS_OPCIONES;
  readonly minutosOpciones = MINUTOS_OPCIONES;

  readonly notaCambioEstado = signal('');
  readonly nombreClienteCambioEstado = signal('');
  readonly montoApartadoCambioEstado = signal<number | null>(null);
  readonly fechaPlazo = signal('');
  readonly horaPlazo = signal(HORA_POR_DEFECTO);
  readonly minutoPlazo = signal(MINUTO_POR_DEFECTO);
  readonly guardandoCambioEstado = signal(false);

  readonly requiereClienteCambioEstado = computed(() => ESTADOS_REQUIEREN_CLIENTE.includes(this.nuevoEstado()));
  readonly requiereMontoCambioEstado = computed(() => this.nuevoEstado() === 'APARTADO_CON_DINERO');

  readonly cambioEstadoInvalido = computed(() => {
    if (!this.notaCambioEstado().trim()) return true;
    if (this.nuevoEstado() === 'APARTADO_A_PLAZO' && !this.fechaPlazo()) return true;
    if (this.requiereClienteCambioEstado() && !this.nombreClienteCambioEstado().trim()) return true;
    return this.requiereMontoCambioEstado() && !this.montoApartadoCambioEstado();
  });

  async confirmarCambioEstado(): Promise<void> {
    if (this.cambioEstadoInvalido() || this.guardandoCambioEstado()) return;

    this.guardandoCambioEstado.set(true);
    try {
      const fechaExpira =
        this.nuevoEstado() === 'APARTADO_A_PLAZO'
          ? combinarFechaHora(this.fechaPlazo(), this.horaPlazo(), this.minutoPlazo())
          : undefined;
      const actualizado = await this.lotesService.cambiarEstado(
        this.lote().id,
        this.nuevoEstado(),
        fechaExpira,
        this.notaCambioEstado().trim(),
        this.requiereClienteCambioEstado() ? this.nombreClienteCambioEstado().trim() : null,
        this.requiereMontoCambioEstado() ? this.montoApartadoCambioEstado() : null,
      );
      this.toast.success('Estado del lote actualizado.');
      this.confirmado.emit(actualizado);
    } catch (error) {
      this.toast.error(
        error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : 'No se pudo cambiar el estado del lote.',
      );
    } finally {
      this.guardandoCambioEstado.set(false);
    }
  }
}
