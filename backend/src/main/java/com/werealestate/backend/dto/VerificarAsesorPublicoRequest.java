package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** proyecto ("samai" / "nanuu") es el plano desde el que se pide el acceso; sin él solo se revisa
 * el PIN y el contrato. */
public record VerificarAsesorPublicoRequest(
        @NotBlank @Size(max = 20) String pin, @Pattern(regexp = "samai|nanuu") String proyecto) {
}
