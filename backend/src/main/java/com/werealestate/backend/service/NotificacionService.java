package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.NotificacionMarcarLeidaRequest;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.EventoCalendario;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.NotificacionLeidaRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final int DIAS_AVISOS_LOTES = 7;
    private static final Set<EstadoLote> ESTADOS_APARTADO =
            Set.of(EstadoLote.APARTADO, EstadoLote.APARTADO_A_PLAZO, EstadoLote.APARTADO_CON_DINERO);
    private static final List<EstadoLead> CERRADOS = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);

    private final LeadRepository leadRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final TareaRepository tareaRepository;
    private final EventoCalendarioRepository eventoCalendarioRepository;
    private final NotificacionLeidaRepository notificacionLeidaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MovimientoLoteRepository movimientoLoteRepository;

    public NotificacionService(
            LeadRepository leadRepository,
            SeguimientoRepository seguimientoRepository,
            TareaRepository tareaRepository,
            EventoCalendarioRepository eventoCalendarioRepository,
            NotificacionLeidaRepository notificacionLeidaRepository,
            CurrentUserProvider currentUserProvider,
            MovimientoLoteRepository movimientoLoteRepository) {
        this.leadRepository = leadRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.tareaRepository = tareaRepository;
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.notificacionLeidaRepository = notificacionLeidaRepository;
        this.currentUserProvider = currentUserProvider;
        this.movimientoLoteRepository = movimientoLoteRepository;
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

        // Último seguimiento de cada lead, resuelto en una sola consulta (antes era una por lead
        // dentro del for de abajo). Ordenado por lead y luego por fecha desc: la primera fila de
        // cada lead que encontramos es su seguimiento más reciente, así que putIfAbsent basta.
        List<Long> leadIds = leadsVisibles.stream().map(Lead::getId).toList();
        Map<Long, Seguimiento> ultimoSeguimientoPorLead = new HashMap<>();
        if (!leadIds.isEmpty()) {
            for (Seguimiento s : seguimientoRepository.findByLeadIdInOrderByLeadIdAscFechaDesc(leadIds)) {
                ultimoSeguimientoPorLead.putIfAbsent(s.getLead().getId(), s);
            }
        }

        LocalDateTime ahora = LocalDateTime.now();
        List<NotificacionDto> notificaciones = new ArrayList<>();

        for (Lead lead : leadsVisibles) {
            boolean cerrado = CERRADOS.contains(lead.getEstado());
            if (cerrado) continue;

            Seguimiento ultimo = ultimoSeguimientoPorLead.get(lead.getId());

            if (ultimo != null) {
                LocalDateTime proximo = ultimo.getProximoSeguimiento();
                if (proximo != null && !proximo.isAfter(ahora)) {
                    String firma = proximo.toString();
                    if (!leidas.contains(clave("SEGUIMIENTO_PENDIENTE", lead.getId(), firma))) {
                        notificaciones.add(NotificacionDto.seguimientoPendiente(
                                "Seguimiento pendiente con " + lead.getNombreCliente() + ".", lead.getId(), firma));
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

        if (actual.getRol() == Role.ADMIN || actual.getRol() == Role.LIDER_AREA) {
            agregarAvisosDeLotes(actual, leidas, notificaciones);
        }

        return notificaciones;
    }

    /** Avisos de que un lote se apartó o se liberó (también por vencimiento automático), solo para
     * admin y líder de área. Salen de la bitácora de cambios de estado (MovimientoLote), no de una
     * tabla propia: la firma es la hora exacta del movimiento, así que cada cambio es un aviso
     * distinto y descartarlo no afecta a los demás. No se le avisa a quien hizo el cambio, y solo se
     * miran los últimos DIAS_AVISOS_LOTES días para que no se acumulen sin fin. */
    private void agregarAvisosDeLotes(Usuario actual, Set<String> leidas, List<NotificacionDto> notificaciones) {
        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_AVISOS_LOTES);
        for (MovimientoLote m : movimientoLoteRepository.recientesDeOtros(desde, actual.getId())) {
            boolean apartado = m.getEstadoAnterior() == EstadoLote.DISPONIBLE && ESTADOS_APARTADO.contains(m.getEstadoNuevo());
            boolean desapartado = ESTADOS_APARTADO.contains(m.getEstadoAnterior()) && m.getEstadoNuevo() == EstadoLote.DISPONIBLE;
            if (!apartado && !desapartado) continue;

            String tipo = apartado ? "LOTE_APARTADO" : "LOTE_DESAPARTADO";
            String firma = m.getFecha().toString();
            if (leidas.contains(clave(tipo, m.getId(), firma))) continue;

            String lote = "Manzana " + m.getLote().getManzana() + ", Lote " + m.getLote().getNumeroLote() + " ("
                    + m.getLote().getDesarrollo().getNombre() + ")";
            String autor = m.getUsuario() != null && m.getNombreAsesor() == null
                    ? m.getUsuario().getNombre()
                    : m.getNombreAsesor() != null ? m.getNombreAsesor() + " (cotizador público)" : null;
            if (apartado) {
                String conCliente = m.getNombreCliente() != null ? " para " + m.getNombreCliente() : "";
                String conMonto = m.getMonto() != null ? " con $" + m.getMonto().toPlainString() : "";
                notificaciones.add(NotificacionDto.loteApartado(
                        lote + " fue apartado" + conMonto + conCliente + (autor != null ? " por " + autor : "") + ".",
                        m.getId(),
                        firma));
            } else {
                notificaciones.add(NotificacionDto.loteDesapartado(
                        lote + (autor != null ? " fue liberado por " + autor + "." : " se liberó automáticamente por vencimiento del apartado."),
                        m.getId(),
                        firma));
            }
        }
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
