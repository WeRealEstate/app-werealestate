package com.werealestate.backend.service;

import com.werealestate.backend.dto.SeguimientoCreateRequest;
import com.werealestate.backend.dto.SeguimientoDto;
import com.werealestate.backend.dto.SeguimientoProximoDto;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SeguimientoService {

    private final SeguimientoRepository seguimientoRepository;
    private final LeadRepository leadRepository;
    private final LeadService leadService;
    private final CurrentUserProvider currentUserProvider;
    private final int diasSinContactarNuevo;

    public SeguimientoService(
            SeguimientoRepository seguimientoRepository,
            LeadRepository leadRepository,
            LeadService leadService,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-sin-contactar-nuevo}") int diasSinContactarNuevo) {
        this.seguimientoRepository = seguimientoRepository;
        this.leadRepository = leadRepository;
        this.leadService = leadService;
        this.currentUserProvider = currentUserProvider;
        this.diasSinContactarNuevo = diasSinContactarNuevo;
    }

    public List<SeguimientoDto> listarPorLead(Long leadId) {
        // Reutiliza la validación de acceso (dueño o admin) de LeadService.
        leadService.buscarLeadPermitido(leadId);
        return seguimientoRepository.findByLeadIdOrderByFechaDesc(leadId).stream()
                .map(SeguimientoDto::from)
                .toList();
    }

    public SeguimientoDto crear(Long leadId, SeguimientoCreateRequest request) {
        Lead lead = leadService.buscarLeadPermitido(leadId);
        Usuario actual = currentUserProvider.getUsuarioActual();

        Seguimiento seguimiento = new Seguimiento(
                lead,
                actual,
                request.tipo(),
                request.nota(),
                request.resultado(),
                proximoSeguimientoOPorDefecto(request.proximoSeguimiento()),
                request.duracionMinutos());
        seguimientoRepository.save(seguimiento);

        lead.setFechaUltimoContacto(LocalDateTime.now());
        leadRepository.save(lead);

        return SeguimientoDto.from(seguimiento);
    }

    /**
     * Próximos seguimientos agendados, para la agenda: admin ve los de todos, asesor y líder los
     * de sus propios leads, equipo interno no tiene leads así que no ve ninguno.
     */
    public List<SeguimientoProximoDto> proximos() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Seguimiento> seguimientos =
                switch (actual.getRol()) {
                    case ADMIN -> seguimientoRepository.findByProximoSeguimientoIsNotNullOrderByProximoSeguimientoAsc();
                    case ASESOR, LIDER_AREA -> seguimientoRepository
                            .findByProximoSeguimientoIsNotNullAndLeadAsesorIdOrderByProximoSeguimientoAsc(actual.getId());
                    case EQUIPO_INTERNO -> List.of();
                };
        return seguimientos.stream().map(SeguimientoProximoDto::from).toList();
    }

    /** Regla 3 de automatización de seguimiento: si al cerrar un seguimiento no se agenda el
     * próximo contacto, se agenda uno por defecto en vez de dejar al lead sin ninguna fecha futura
     * (lo que lo dejaría invisible tanto en la agenda como en "seguimiento pendiente"). */
    private LocalDateTime proximoSeguimientoOPorDefecto(LocalDateTime proximoSeguimiento) {
        return proximoSeguimiento != null ? proximoSeguimiento : LocalDateTime.now().plusDays(diasSinContactarNuevo);
    }
}
