package com.werealestate.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** celular/correo no son obligatorios aquí (a diferencia de AsesorExternoCreateRequest): un
 * asesor externo registrado antes de que existieran estos campos debe poder seguir editándose
 * (renombrarse, activarse/desactivarse) sin que le exijan completarlos primero. */
public record AsesorExternoUpdateRequest(@NotBlank String nombre, String celular, @Email String correo, boolean activo) {
}
