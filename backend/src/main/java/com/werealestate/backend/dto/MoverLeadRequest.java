package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.TipoSeguimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Mover una tarjeta a otro estado desde el tablero registra, con estos mismos datos, un seguimiento real en la bitácora. */
public record MoverLeadRequest(
        @NotNull EstadoLead estado,
        @NotNull TipoSeguimiento tipo,
        @NotBlank String nota,
        String resultado,
        LocalDateTime proximoSeguimiento) {
}
