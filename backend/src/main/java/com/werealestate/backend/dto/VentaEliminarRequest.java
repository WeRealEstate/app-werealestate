package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Pide eliminar una venta por completo; exige la contraseña del admin que lo pide como
 * confirmación. Sus lotes vuelven siempre a DISPONIBLE (con su historial). Ver VentaService.eliminar. */
public record VentaEliminarRequest(@NotBlank String password) {
}
