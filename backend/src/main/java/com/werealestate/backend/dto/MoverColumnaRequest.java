package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoSeguimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Mover un lead a otra tarjeta (o a "Sin asignar" cuando columnaPersonalizadaId es nulo) desde el
 * tablero registra, con estos mismos datos, un seguimiento real en la bitácora.
 */
public record MoverColumnaRequest(
        Long columnaPersonalizadaId,
        @NotNull TipoSeguimiento tipo,
        @NotBlank String nota,
        String resultado,
        LocalDateTime proximoSeguimiento) {
}
