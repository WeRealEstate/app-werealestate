package com.werealestate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Apartar varios lotes a la vez desde el plano interno (estado APARTADO simple). Todos reciben los mismos
 * datos: el cliente es obligatorio; la nota es obligatoria solo para admin y administración (igual que al
 * apartar uno), y ellos además indican a qué asesor pertenece el apartado (exactamente uno de los dos ids). Todo o
 * nada: si algún lote ya no está disponible no se aparta ninguno (ver LoteService.apartarVarios). */
public record ApartarVariosRequest(
        List<Long> loteIds,
        @NotBlank @Size(max = 150) String nombreCliente,
        @Size(max = 500) String nota,
        Long usuarioAsesorId,
        Long asesorExternoId) {
}
