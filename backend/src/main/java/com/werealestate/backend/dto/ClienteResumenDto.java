package com.werealestate.backend.dto;

import com.werealestate.backend.model.Cliente;

/** Un cliente en breve (nombre y enlace), p. ej. los copropietarios de una venta. */
public record ClienteResumenDto(Long id, String nombreCompleto) {

    public static ClienteResumenDto from(Cliente c) {
        return new ClienteResumenDto(c.getId(), c.nombreCompleto());
    }
}
