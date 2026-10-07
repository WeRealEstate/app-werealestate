package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.TipoAsesorExterno;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** celular/correo no son obligatorios aquí: un asesor externo registrado antes de que existieran
 * estos campos debe poder seguir editándose
 * (renombrarse, activarse/desactivarse) sin que le exijan completarlos primero.
 *
 * <p>tipo/liderDirectoId son la jerarquía de Teams (ver AsesorExternoService.resolverJerarquia):
 * la pantalla de "Asesores externos" los manda sin cambios (tal como venían) al renombrar/activar,
 * y solo la pantalla de Teams los cambia de verdad. nivelLinea no se manda: lo calcula el servidor
 * a partir de liderDirectoId, nunca se confía en el que mande el cliente.
 *
 * <p>contratoEstado y los accesos son opcionales: null = sin cambios (así Teams y clientes viejos
 * siguen funcionando). Cuando viene contratoEstado, las dos fechas se aplican tal cual — null las
 * borra. */
public record AsesorExternoUpdateRequest(
        @NotBlank String nombre,
        String celular,
        @Email String correo,
        boolean activo,
        @NotNull TipoAsesorExterno tipo,
        Long liderDirectoId,
        EstadoContratoAsesor contratoEstado,
        LocalDate contratoFechaFirma,
        LocalDate contratoFechaVencimiento,
        Boolean accesoSamai,
        Boolean accesoNanuu,
        // null = sin cambios; "" = quitar el PIN; 4 letras/números = asignarlo.
        String pin) {
}
