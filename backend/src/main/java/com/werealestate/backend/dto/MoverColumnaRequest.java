package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoSeguimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Mover una tarjeta a una columna personalizada desde el tablero registra, con estos mismos datos, un seguimiento real en la bitácora. */
public record MoverColumnaRequest(
        @NotNull Long columnaPersonalizadaId,
        @NotNull TipoSeguimiento tipo,
        @NotBlank String nota,
        String resultado,
        LocalDateTime proximoSeguimiento) {
}
