package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Datos del ticket (comprobante) de pago de una entrega de comisión; el PDF se arma en el frontend.
 * folio = id de la entrega (COM-000412). Los montos de la comisión son los que tenía al entregar. */
public record ComisionTicketDto(
        Long folio,
        LocalDateTime emitido,
        LocalDate fechaEntrega,
        BigDecimal monto,
        boolean conIva,
        boolean conFactura,
        ModalidadPago formaPago,
        // Gasto "Comisiones" que generó la entrega (null si ya no existe).
        Long gastoId,
        LocalDate gastoFecha,
        String gastoTipo,
        String asesorNombre,
        boolean asesorExterno,
        // true si es la comisión del copropietario de la venta: su monto va en "Comisión secundaria".
        boolean secundaria,
        Long ventaNumero,
        boolean ventaEliminada,
        String cliente,
        String desarrollo,
        String lotes,
        BigDecimal porcentaje,
        BigDecimal comisionTotal,
        // Lo entregado de la comisión hasta esta entrega incluida (neto de devoluciones).
        BigDecimal entregadoAcumulado,
        List<Abono> abonos) {

    public record Abono(LocalDate fecha, String concepto, BigDecimal monto) {
    }
}
