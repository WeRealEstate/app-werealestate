package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoContratoAsesor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Contrato y accesos son opcionales: sin ellos queda VIGENTE con acceso a ambos planos. */
public record AsesorExternoCreateRequest(
        @NotBlank String nombre,
        @NotBlank String celular,
        @Email String correo,
        EstadoContratoAsesor contratoEstado,
        LocalDate contratoFechaFirma,
        LocalDate contratoFechaVencimiento,
        Boolean accesoSamai,
        Boolean accesoNanuu,
        // 4 letras/números; opcional (sin PIN no puede entrar al botón Asesor de los planos públicos).
        String pin) {
}
