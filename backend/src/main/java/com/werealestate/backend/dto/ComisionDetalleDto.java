package com.werealestate.backend.dto;

import com.werealestate.backend.model.ModalidadPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ComisionDetalleDto(
        ComisionDto comision,
        List<Devengo> devengos,
        List<Entrega> entregas,
        List<Devolucion> devoluciones,
        List<AbonoCliente> abonosCliente) {

    /** Un abono vigente del cliente, en orden, con lo que aportó a la comisión (12.31.0). */
    public record AbonoCliente(LocalDate fecha, String concepto, BigDecimal monto, BigDecimal aComision) {
    }

    public record Devolucion(
            Long id, LocalDate fecha, BigDecimal monto, String notas, String registradaPor, LocalDateTime fechaCreacion) {
    }

    public record Devengo(LocalDate fechaOrigen, LocalDate fechaEntrega, BigDecimal monto) {
    }

    /** conIva, conFactura y formaPago son null en las entregas anteriores a los tickets (conTicket = false). */
    public record Entrega(
            Long id,
            LocalDate fecha,
            BigDecimal monto,
            String notas,
            Long gastoId,
            String registradaPor,
            LocalDateTime fechaCreacion,
            Boolean conIva,
            Boolean conFactura,
            ModalidadPago formaPago,
            boolean conTicket) {
    }
}
