package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Una fila de la lista de clientes (y del buscador de la venta). */
public record ClienteListaDto(
        Long id,
        String nombreCompleto,
        String telefono,
        String correo,
        boolean activo,
        boolean datosIncompletos,
        int compras,
        List<String> desarrollos,
        BigDecimal saldoPendiente) {
}
