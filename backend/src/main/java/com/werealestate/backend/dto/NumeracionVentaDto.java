package com.werealestate.backend.dto;

import java.time.LocalDate;

/** Respuesta de /api/ventas/numeracion: el siguiente número automático y, si el número consultado ya
 * lo tiene otra venta, cuál es y cuántas ventas se recorrerían +1 (esa y las consecutivas que le siguen). */
public record NumeracionVentaDto(
        long siguiente, Long ocupanteId, String ocupanteCliente, LocalDate ocupanteFecha, int ventasARecorrer) {
}
