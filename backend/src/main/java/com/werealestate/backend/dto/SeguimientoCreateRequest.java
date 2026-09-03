package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoSeguimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record SeguimientoCreateRequest(
        @NotNull TipoSeguimiento tipo,
        @NotBlank String nota,
        String resultado,
        LocalDateTime proximoSeguimiento,
        Integer duracionMinutos) {
}
