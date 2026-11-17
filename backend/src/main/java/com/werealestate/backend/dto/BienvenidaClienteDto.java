package com.werealestate.backend.dto;

import java.time.LocalDateTime;

/**
 * Lo que el detalle de la venta necesita para el botón "Enviar correo de bienvenida". Ver BienvenidaClienteService.
 */
public record BienvenidaClienteDto(
        // false si el desarrollo de la venta no tiene plantilla (el botón ni se muestra).
        boolean disponible,
        boolean puedeEnviar,
        // Por qué no se puede enviar (null si se puede).
        String motivoNoDisponible,
        // Correo del cliente titular (null si no tiene).
        String correoDestino,
        // La última bienvenida que salió bien; null si nunca se ha enviado.
        Ultimo ultimo) {

    public record Ultimo(LocalDateTime enviadoEn, String usuario, String correo) {
    }
}
