package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoComision;
import com.werealestate.backend.model.ModalidadComision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ComisionDto(
        Long id,
        // null cuando la venta ya no existe (ventaEliminada): la comisión se queda en el historial.
        Long ventaId,
        Long ventaNumero,
        boolean ventaEliminada,
        String cliente,
        String asesorNombre,
        boolean asesorExterno,
        BigDecimal base,
        BigDecimal porcentaje,
        BigDecimal monto,
        boolean montoManual,
        ModalidadComision modalidad,
        EstadoComision estado,
        boolean cancelada,
        // Lo ya ganado por los abonos de la venta, lo ya entregado y la diferencia por entregar.
        BigDecimal devengado,
        BigDecimal entregado,
        BigDecimal porEntregar,
        // Sábado en que toca entregar lo primero que sigue sin entregarse; null si no hay nada.
        LocalDate proximaEntrega,
        // Retrasada: ya pasó el sábado en que tocaba entregar algo ganado y sigue sin entregarse;
        // montoRetrasado es lo que se debe de ese tiempo (lo ganado con sábado vencido menos lo entregado).
        boolean retrasada,
        BigDecimal montoRetrasado,
        LocalDateTime fechaCreacion) {
}
