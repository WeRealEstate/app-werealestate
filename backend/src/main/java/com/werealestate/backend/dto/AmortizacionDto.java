package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoPagoPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tabla de amortización de una venta con su estado de cuenta al día de hoy ({@code corte}); ver
 * service/CalendarioPagos. Lleva lo necesario para el PDF que se le da al cliente: el titular (nombre, su número en
 * el desarrollo de la venta y teléfono), los copropietarios y los términos de la venta.
 */
public record AmortizacionDto(
        Long ventaId,
        Long ventaNumero,
        LocalDate fechaVenta,
        // Titular de la venta. clienteNumeros: su número en el desarrollo de la venta ("S-40"; uno por desarrollo si
        // la venta mezcla lotes de dos), vacío si aún no tiene.
        Long clienteId,
        String cliente,
        List<String> clienteNumeros,
        String clienteTelefono,
        // Nombres de los copropietarios (la deuda es la misma para todos).
        List<String> copropietarios,
        // true si se pidió desde la ficha de un copropietario (no del titular).
        boolean copropietario,
        List<String> desarrollos,
        List<String> lotes,
        String formaPago,
        String numeroContrato,
        BigDecimal precio,
        // Lo que la venta guarda como enganche ("Enganche", "Pago inicial" o, en ventas viejas, "Aportación anual";
        // null si no lleva); plazo, mensualidad y día de pago (null/0 en contado).
        String engancheLabel,
        BigDecimal enganche,
        Integer plazoMeses,
        BigDecimal mensualidad,
        int diaPago,
        LocalDate corte,
        // Lo abonado (recibos vigentes) y lo que falta del precio.
        BigDecimal pagado,
        BigDecimal saldo,
        // Lo que falta de los pagos ya vencidos, cuántos pagos son, cuántos de ellos mensualidades y desde cuándo.
        BigDecimal atrasoMonto,
        int pagosAtrasados,
        int mensualidadesAtrasadas,
        LocalDate atrasadoDesde,
        // El siguiente pago sin cubrir que aún no vence (lo que falta de él); null si ya no hay.
        LocalDate proximaFecha,
        BigDecimal proximoMonto,
        List<Fila> filas) {

    /** Un pago del plan. numero: su lugar en el plazo (null en enganche, contado, aportación fuera del plazo y lo
     * que no tiene fecha); fecha null = parte del precio sin fecha en el plan. pagado: lo que ya cubre lo abonado;
     * saldo: lo que falta del precio después de este pago según el plan. arrastre: faltante de una mensualidad vencida
     * pagada en parte que se pasó al pago siguiente (ver CalendarioPagos): negativo en la fila que lo cedió (su monto
     * baja a lo que se alcanzó a pagar) y positivo en la que lo recibe (su monto lo incluye); cero en las demás.
     * abonadoMes: el dinero que de verdad se recibió en el mes de este pago, según la fecha de cada recibo vigente
     * (no depende de cómo el plan reparte lo abonado); la suma de la columna es lo abonado. */
    public record Fila(
            Integer numero,
            LocalDate fecha,
            String concepto,
            BigDecimal monto,
            BigDecimal pagado,
            BigDecimal saldo,
            EstadoPagoPlan estado,
            BigDecimal arrastre,
            BigDecimal abonadoMes) {

        /** Sin lo abonado por mes (lo llena EstadoCuentaService con las fechas de los recibos). */
        public Fila(Integer numero, LocalDate fecha, String concepto, BigDecimal monto, BigDecimal pagado,
                BigDecimal saldo, EstadoPagoPlan estado, BigDecimal arrastre) {
            this(numero, fecha, concepto, monto, pagado, saldo, estado, arrastre, BigDecimal.ZERO);
        }

        public Fila conAbonadoMes(BigDecimal abonado) {
            return new Fila(numero, fecha, concepto, monto, pagado, saldo, estado, arrastre, abonado);
        }
    }
}
