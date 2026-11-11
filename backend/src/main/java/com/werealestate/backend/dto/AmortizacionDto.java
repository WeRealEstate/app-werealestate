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
        String ventaNumero,
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
        // Reparto del dinero del más antiguo al más nuevo (lo que usan el formulario de abonos y los demás módulos).
        List<Fila> filas,
        // La misma tabla vista mes con mes: cada renglón con lo que se esperaba pagar ese mes (más lo atrasado, o menos
        // lo pagado de más) y los abonos que de verdad se dieron en él, cada uno con su fecha. Ver TablaMensual.
        List<Fila> detalleMensual,
        // Cambios del plan de pagos, del más antiguo al más reciente; tramo = el de las filas del plan que creó.
        List<Reestructuracion> reestructuraciones,
        // Para reestructurar: primer mes del plan original ("2026-06"; null en contado) y lo que se reparte en
        // mensualidades y aportaciones (precio menos enganche).
        String primerMesPlan,
        BigDecimal montoAReestructurar) {

    /** Una reestructuración del plan: a partir de las filas con este {@code tramo} corre el plan nuevo. */
    public record Reestructuracion(
            int tramo,
            LocalDate fechaCorte,
            String motivo,
            String usuario,
            BigDecimal abonadoAlCorte,
            BigDecimal saldoAlCorte,
            String resumenAnterior,
            String resumenNuevo) {
    }

    /** Un abono vigente (una línea de recibo) con su fecha; aCuentaComision si es del tipo «A cuenta de comisión»;
     * folio: el del recibo (null en abonos muy viejos sin folio); parteDe: el monto completo del abono cuando este
     * renglón solo lleva una parte (un abono que cubre varios meses se reparte entre ellos), null si va completo. */
    public record PagoMes(LocalDate fecha, BigDecimal monto, boolean aCuentaComision, String folio, BigDecimal parteDe) {
    }

    /** Un pago del plan. numero: su lugar en el plazo (null en enganche, contado, aportación fuera del plazo y lo
     * que no tiene fecha); fecha null = parte del precio sin fecha en el plan. pagado: lo que ya cubre lo abonado;
     * saldo: lo que falta del precio después de este pago según el plan. arrastre: faltante de una mensualidad vencida
     * pagada en parte que se pasó al pago siguiente (ver CalendarioPagos): negativo en la fila que lo cedió (su monto
     * baja a lo que se alcanzó a pagar) y positivo en la que lo recibe (su monto lo incluye); cero en las demás.
     * abonadoMes y pagos: solo en detalleMensual; el dinero que de verdad se recibió en el mes de este pago, según la
     * fecha de cada recibo vigente (abonadoMes es la suma y pagos el desglose, uno por abono). */
    public record Fila(
            Integer numero,
            LocalDate fecha,
            String concepto,
            BigDecimal monto,
            BigDecimal pagado,
            BigDecimal saldo,
            EstadoPagoPlan estado,
            BigDecimal arrastre,
            BigDecimal abonadoMes,
            List<PagoMes> pagos,
            int tramo,
            // Solo en detalleMensual: lo que se pagó en ese mes por encima de lo que le tocaba. No adelanta la
            // mensualidad siguiente: abona a la deuda total (ver TablaMensual).
            BigDecimal excedente) {

        public Fila(Integer numero, LocalDate fecha, String concepto, BigDecimal monto, BigDecimal pagado,
                BigDecimal saldo, EstadoPagoPlan estado, BigDecimal arrastre, BigDecimal abonadoMes, List<PagoMes> pagos,
                int tramo) {
            this(numero, fecha, concepto, monto, pagado, saldo, estado, arrastre, abonadoMes, pagos, tramo, BigDecimal.ZERO);
        }

        /** Fila del motor de cálculo (sin el detalle mes con mes), del plan original. */
        public Fila(Integer numero, LocalDate fecha, String concepto, BigDecimal monto, BigDecimal pagado,
                BigDecimal saldo, EstadoPagoPlan estado, BigDecimal arrastre) {
            this(numero, fecha, concepto, monto, pagado, saldo, estado, arrastre, BigDecimal.ZERO, List.of(), 0);
        }
    }
}
