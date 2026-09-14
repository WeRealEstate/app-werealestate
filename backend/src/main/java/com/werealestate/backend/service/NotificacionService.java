package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.NotificacionMarcarLeidaRequest;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.EventoCalendario;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.NotificacionLeidaRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones "en vivo": no se guardan en base de datos, se recalculan a partir de leads,
 * seguimientos, tareas y eventos de calendario cada vez que se piden (típicamente al entrar al
 * sistema). Lo único que sí se guarda es qué ocurrencias concretas ya vio cada usuario
 * (NotificacionLeida), para no volver a mostrarlas.
 */
@Service
@Transactional
public class NotificacionService {

    private static final List<EstadoLead> CERRADOS = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);

    private final LeadRepository leadRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final TareaRepository tareaRepository;
    private final EventoCalendarioRepository eventoCalendarioRepository;
    private final NotificacionLeidaRepository notificacionLeidaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int diasFrio;
    private final int diasSinContactarNuevo;
    private final int diasEscalarAdmin;

    public NotificacionService(
            LeadRepository leadRepository,
            SeguimientoRepository seguimientoRepository,
            TareaRepository tareaRepository,
            EventoCalendarioRepository eventoCalendarioRepository,
            NotificacionLeidaRepository notificacionLeidaRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-frio}") int diasFrio,
            @Value("${app.lead.dias-sin-contactar-nuevo}") int diasSinContactarNuevo,
            @Value("${app.lead.dias-escalar-admin}") int diasEscalarAdmin) {
        this.leadRepository = leadRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.tareaRepository = tareaRepository;
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.notificacionLeidaRepository = notificacionLeidaRepository;
        this.currentUserProvider = currentUserProvider;
        this.diasFrio = diasFrio;
        this.diasSinContactarNuevo = diasSinContactarNuevo;
        this.diasEscalarAdmin = diasEscalarAdmin;
    }

    public List<NotificacionDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();

        Set<String> leidas = new HashSet<>();
        for (NotificacionLeida l : notificacionLeidaRepository.findByUsuarioId(actual.getId())) {
            leidas.add(clave(l.getTipo(), l.getEntidadId(), l.getFirma()));
        }

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
                String firma = lead.getFechaUltimoContacto().toString();
                if (!leidas.contains(clave("LEAD_FRIO", lead.getId(), firma))) {
                    notificaciones.add(NotificacionDto.leadFrio(
                            lead.getNombreCliente() + " lleva " + dias + " días sin seguimiento.",
                            lead.getId(),
                            firma));
                }
            }

            List<Seguimiento> historial = seguimientoRepository.findByLeadIdOrderByFechaDesc(lead.getId());
            if (!historial.isEmpty()) {
                Seguimiento ultimo = historial.get(0);
                LocalDateTime proximo = ultimo.getProximoSeguimiento();
                if (proximo != null && !proximo.isAfter(ahora)) {
                    String firma = proximo.toString();
                    if (!leidas.contains(clave("SEGUIMIENTO_PENDIENTE", lead.getId(), firma))) {
                        notificaciones.add(NotificacionDto.seguimientoPendiente(
                                "Seguimiento pendiente con " + lead.getNombreCliente() + ".", lead.getId(), firma));
                    }
                }
            } else {
                // Regla 1/2: nunca se le ha registrado ningún seguimiento. El asesor lo ve pasado
                // el umbral normal; el admin solo lo ve si ya escaló (umbral mayor), para no
                // saturarle la campana con cada lead nuevo de cada asesor.
                long diasSinContactar = ChronoUnit.DAYS.between(lead.getFechaCreacion(), ahora);
                boolean esAdmin = actual.getRol() == Role.ADMIN;
                int umbral = esAdmin ? diasEscalarAdmin : diasSinContactarNuevo;
                if (diasSinContactar >= umbral) {
                    String firma = lead.getFechaCreacion().toString();
                    String mensaje = esAdmin
                            ? lead.getNombreCliente() + " (asesor: " + lead.getAsesor().getNombre() + ") lleva "
                                    + diasSinContactar + " días sin ser contactado por primera vez."
                            : lead.getNombreCliente() + " lleva " + diasSinContactar
                                    + " días sin ser contactado por primera vez.";
                    if (!leidas.contains(clave("LEAD_SIN_CONTACTAR", lead.getId(), firma))) {
                        notificaciones.add(NotificacionDto.leadSinContactar(mensaje, lead.getId(), firma));
                    }
                }
            }
        }

        LocalDate hoy = LocalDate.now();

        for (Tarea tarea : tareaRepository.findByAsignadoAIdOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc(actual.getId())) {
            if (tarea.isCompletada()) continue;
            // Solo notifica tareas con fecha límite vencida o para hoy; sin fecha, se ve en "Mis tareas" pero no interrumpe.
            LocalDate fechaLimite = tarea.getFechaLimite();
            boolean vencidaOParaHoy = fechaLimite != null && !fechaLimite.isAfter(hoy);
            if (vencidaOParaHoy) {
                String firma = fechaLimite.toString();
                if (!leidas.contains(clave("TAREA_PENDIENTE", tarea.getId(), firma))) {
                    notificaciones.add(NotificacionDto.tareaPendiente(
                            "Tarea pendiente: " + tarea.getTitulo(), tarea.getId(), firma));
                }
            }
        }

        for (EventoCalendario evento : eventoCalendarioRepository.findByUsuarioIdOrderByFechaAsc(actual.getId())) {
            if (!evento.isRecordatorio()) continue;

            LocalDate fecha = evento.getFecha();
            String firma;
            String mensaje;
            if (fecha.equals(hoy.plusDays(1))) {
                firma = "ANTES";
                mensaje = "Mañana: \"" + evento.getTitulo() + "\".";
            } else if (!fecha.isAfter(hoy)) {
                firma = "HOY";
                mensaje = "Hoy: \"" + evento.getTitulo() + "\".";
            } else {
                continue;
            }

            if (!leidas.contains(clave("EVENTO_PENDIENTE", evento.getId(), firma))) {
                notificaciones.add(NotificacionDto.eventoPendiente(mensaje, evento.getId(), firma));
            }
        }

        return notificaciones;
    }

    public void marcarLeida(NotificacionMarcarLeidaRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        boolean yaExiste = notificacionLeidaRepository.existsByUsuarioIdAndTipoAndEntidadIdAndFirma(
                actual.getId(), request.tipo(), request.entidadId(), request.firma());
        if (yaExiste) return;

        notificacionLeidaRepository.save(
                new NotificacionLeida(actual, request.tipo(), request.entidadId(), request.firma()));
    }

    private static String clave(String tipo, Long entidadId, String firma) {
        return tipo + "|" + entidadId + "|" + firma;
    }
}
