package com.werealestate.backend.dto;

/** El número de un cliente en un desarrollo: {@code etiqueta} es como se muestra ("S-40", "N-12"). */
public record NumeroClienteDto(Long desarrolloId, String desarrollo, String prefijo, long numero, String etiqueta) {
}
