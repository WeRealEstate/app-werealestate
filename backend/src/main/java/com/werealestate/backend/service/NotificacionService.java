package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones "en vivo": no se guardan en base de datos, se calculan a partir de leads,
 * seguimientos y tareas cada vez que se piden (típicamente al entrar al sistema).
 */
@Service
@Transactional
public class NotificacionService {

    private static final List<EstadoLead> CERRADOS = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);

    private final LeadRepository leadRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final TareaRepository tareaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int diasFrio;

    public NotificacionService(
            LeadRepository leadRepository,
            SeguimientoRepository seguimientoRepository,
            TareaRepository tareaRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-frio}") int diasFrio) {
        this.leadRepository = leadRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.tareaRepository = tareaRepository;
        this.currentUserProvider = currentUserProvider;
        this.diasFrio = diasFrio;
    }

    public List<NotificacionDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Lead> leadsVisibles = actual.getRol() == Role.ADMIN
                ? leadRepository.findByArchivadoFalseOrderByFechaUltimoContactoAsc()
                : leadRepository.findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoAsc(actual.getId());

        LocalDateTime ahora = LocalDateTime.now();
        List<NotificacionDto> notificaciones = new ArrayList<>();

        for (Lead lead : leadsVisibles) {
            boolean cerrado = CERRADOS.contains(lead.getEstado());
            if (cerrado) continue;

            long dias = ChronoUnit.DAYS.between(lead.getFechaUltimoContacto(), ahora);
            if (dias >= diasFrio) {
                notificaciones.add(NotificacionDto.leadFrio(
                        lead.getNombreCliente() + " lleva " + dias + " días sin seguimiento.", lead.getId()));
            }

            List<Seguimiento> historial = seguimientoRepository.findByLeadIdOrderByFechaDesc(lead.getId());
            if (!historial.isEmpty()) {
                Seguimiento ultimo = historial.get(0);
                LocalDateTime proximo = ultimo.getProximoSeguimiento();
                if (proximo != null && !proximo.isAfter(ahora)) {
                    notificaciones.add(NotificacionDto.seguimientoPendiente(
                            "Seguimiento pendiente con " + lead.getNombreCliente() + ".", lead.getId()));
                }
            }
        }

        LocalDate hoy = LocalDate.now();
        for (Tarea tarea : tareaRepository.findByAsignadoAIdOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc(actual.getId())) {
            if (tarea.isCompletada()) continue;
            // Solo notifica tareas con fecha límite vencida o para hoy; sin fecha, se ve en "Mis tareas" pero no interrumpe.
            boolean vencidaOParaHoy = tarea.getFechaLimite() != null && !tarea.getFechaLimite().isAfter(hoy);
            if (vencidaOParaHoy) {
                notificaciones.add(NotificacionDto.tareaPendiente("Tarea pendiente: " + tarea.getTitulo(), tarea.getId()));
            }
        }

        return notificaciones;
    }
}
