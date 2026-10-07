package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoCivil;
import com.werealestate.backend.model.FuenteCliente;
import com.werealestate.backend.model.UbicacionDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Obligatorios: nombre, apellido paterno, fecha de nacimiento (mayor de edad) y teléfono. El resto es
 * opcional. captadoPor*: a lo más uno. activo: null = sin cambios al editar (activo al crear). */
public record ClienteRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 120) String apellidoPaterno,
        @Size(max = 120) String apellidoMaterno,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Size(max = 30) String telefono,
        @Size(max = 30) String telefono2,
        @Size(max = 150) String correo,
        @Size(max = 18) String curp,
        @Size(max = 13) String rfc,
        @Size(max = 120) String lugarNacimiento,
        @Size(max = 80) String nacionalidad,
        EstadoCivil estadoCivil,
        @Size(max = 120) String ocupacion,
        @Size(max = 200) String calle,
        @Size(max = 120) String colonia,
        @Size(max = 120) String municipio,
        @Size(max = 80) String estado,
        @Size(max = 10) String codigoPostal,
        @Size(max = 200) String beneficiarioNombre,
        @Size(max = 80) String beneficiarioParentesco,
        FuenteCliente fuente,
        @Size(max = 200) String fuenteDetalle,
        Long captadoPorUsuarioId,
        Long captadoPorAsesorId,
        @Size(max = 1000) String notas,
        UbicacionDocumento expedienteUbicacion,
        @Size(max = 500) String expedienteDriveUrl,
        Boolean activo) {
}
