package com.werealestate.backend.dto;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.TipoAsesorExterno;
import java.time.LocalDate;

public record AsesorExternoDto(
        Long id,
        String nombre,
        String celular,
        String correo,
        boolean activo,
        TipoAsesorExterno tipo,
        Long liderDirectoId,
        // Para pintar el árbol de equipos sin tener que cruzar contra la lista completa en el
        // frontend (ver TeamsComponent).
        String liderDirectoNombre,
        Integer nivelLinea,
        EstadoContratoAsesor contratoEstado,
        // Lo que de verdad cuenta: un VIGENTE con vencimiento ya pasado aparece como VENCIDO.
        EstadoContratoAsesor contratoEstadoEfectivo,
        LocalDate contratoFechaFirma,
        LocalDate contratoFechaVencimiento,
        boolean accesoSamai,
        boolean accesoNanuu) {

    public static AsesorExternoDto from(AsesorExterno asesor) {
        return new AsesorExternoDto(
                asesor.getId(),
                asesor.getNombre(),
                asesor.getCelular(),
                asesor.getCorreo(),
                asesor.isActivo(),
                asesor.getTipo(),
                asesor.getLiderDirecto() != null ? asesor.getLiderDirecto().getId() : null,
                asesor.getLiderDirecto() != null ? asesor.getLiderDirecto().getNombre() : null,
                asesor.getNivelLinea(),
                asesor.getContratoEstado(),
                asesor.getContratoEstadoEfectivo(),
                asesor.getContratoFechaFirma(),
                asesor.getContratoFechaVencimiento(),
                asesor.isAccesoSamai(),
                asesor.isAccesoNanuu());
    }
}
