import { describe, expect, it } from 'vitest';
import { EstadoPagoPlan, FilaAmortizacion } from '../models/amortizacion.model';
import { mensualidadesPorCobrar, montoPorMeses } from './meses-por-cobrar';

function fila(numero: number, fecha: string, monto: number, pagado: number, estado: EstadoPagoPlan): FilaAmortizacion {
  return { numero, fecha, concepto: 'Mensualidad', monto, pagado, saldo: 0, estado, arrastre: 0, abonadoMes: pagado,
    pagos: [], tramo: 0, excedente: 0 };
}

const CORTE = '2026-11-25';

describe('meses por cobrar', () => {
  it('lo pagado de más en meses anteriores no da por cubiertos los que siguen', () => {
    // Como la venta del reporte: pagó de más cada mes y noviembre aún no tiene ningún pago.
    const tabla = [
      fila(5, '2026-09-26', 5884.8, 5884.8, 'PAGADO_CON_RETRASO'),
      fila(6, '2026-10-26', 5884.8, 5884.8, 'PAGADO'),
      fila(7, '2026-11-26', 5884.8, 0, 'LE_TOCA'),
      fila(8, '2026-12-26', 5884.8, 0, 'PENDIENTE'),
    ];
    expect(mensualidadesPorCobrar(tabla, CORTE, 293000)).toEqual(['2026-11', '2026-12']);
    expect(montoPorMeses(tabla, CORTE, ['2026-11'])).toBe(5884.8);
    expect(montoPorMeses(tabla, CORTE, ['2026-11', '2026-12'])).toBe(11769.6);
  });

  it('un faltante ya cubierto en un mes posterior no se vuelve a cobrar', () => {
    const tabla = [
      fila(1, '2026-09-10', 1000, 600, 'ABONADO'),
      fila(2, '2026-10-10', 1400, 1400, 'PAGADO'),
      fila(3, '2026-11-10', 1000, 0, 'ATRASADO'),
      fila(4, '2026-12-10', 2000, 0, 'LE_TOCA'),
    ];
    expect(mensualidadesPorCobrar(tabla, CORTE, 5000)).toEqual(['2026-11', '2026-12']);
  });

  it('con atraso no cuenta dos veces lo que ya va sumado en el mes siguiente', () => {
    const tabla = [
      fila(1, '2026-09-10', 1000, 0, 'ATRASADO'),
      fila(2, '2026-10-10', 2000, 0, 'ATRASADO'),
      fila(3, '2026-11-10', 3000, 0, 'ATRASADO'),
      fila(4, '2026-12-10', 4000, 0, 'LE_TOCA'),
      fila(5, '2027-01-10', 1000, 0, 'PENDIENTE'),
    ];
    expect(mensualidadesPorCobrar(tabla, CORTE, 5000)).toEqual(['2026-09', '2026-10', '2026-11', '2026-12', '2027-01']);
    expect(montoPorMeses(tabla, CORTE, ['2026-12'])).toBe(4000);
    expect(montoPorMeses(tabla, CORTE, ['2026-09'])).toBe(1000);
    expect(montoPorMeses(tabla, CORTE, ['2026-09', '2026-10', '2026-11', '2026-12'])).toBe(4000);
    expect(montoPorMeses(tabla, CORTE, ['2026-09', '2026-12'])).toBe(4000);
    expect(montoPorMeses(tabla, CORTE, ['2026-12'], ['2026-09'])).toBe(3000);
    expect(montoPorMeses(tabla, CORTE, ['2026-12', '2027-01'])).toBe(5000);
  });

  it('liquidada o con pagos extemporáneos', () => {
    const tabla = [
      fila(1, '2026-09-10', 1000, 600, 'ABONADO'),
      { ...fila(2, '2026-10-02', 400, 400, 'PAGADO'), numero: null, concepto: 'Pago extemporáneo' },
    ];
    expect(mensualidadesPorCobrar(tabla, CORTE, 0)).toEqual([]);
    expect(mensualidadesPorCobrar(tabla, CORTE, 100)).toEqual(['2026-09']);
  });
});
