package com.werealestate.backend.dto;

/** Respuesta de /api/clientes/numeracion: el siguiente número automático y, si el número consultado ya lo
 * tiene otro cliente, cuál es y cuántos clientes se recorrerían +1 (ese y los consecutivos que le siguen). */
public record NumeracionClienteDto(long siguiente, Long ocupanteId, String ocupanteNombre, int clientesARecorrer) {
}
