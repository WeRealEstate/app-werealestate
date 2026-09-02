package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Pais;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeadDto(
        Long id,
        String nombreCliente,
        String telefono,
        String email,
        String origen,
        DesarrolloDto desarrollo,
        UsuarioResumenDto asesor,
        EstadoLead estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaUltimoContacto,
        BigDecimal valorEstimado,
        Integer edad,
        Pais pais,
        String estadoRepublica,
        long diasSinContacto,
        boolean frio) {

    public static LeadDto from(Lead lead, long diasSinContacto, boolean frio) {
        return new LeadDto(
                lead.getId(),
                lead.getNombreCliente(),
                lead.getTelefono(),
                lead.getEmail(),
                lead.getOrigen(),
                DesarrolloDto.from(lead.getDesarrollo()),
                UsuarioResumenDto.from(lead.getAsesor()),
                lead.getEstado(),
                lead.getFechaCreacion(),
                lead.getFechaUltimoContacto(),
                lead.getValorEstimado(),
                lead.getEdad(),
                lead.getPais(),
                lead.getEstadoRepublica(),
                diasSinContacto,
                frio);
    }
}
