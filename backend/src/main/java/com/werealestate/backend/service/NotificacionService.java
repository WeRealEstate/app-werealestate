package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.NotificacionMarcarLeidaRequest;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.EventoCalendario;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
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
    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;

    public NotificacionService(
            LeadRepository leadRepository,
            SeguimientoRepository seguimientoRepository,
            TareaRepository tareaRepository,
            EventoCalendarioRepository eventoCalendarioRepository,
            NotificacionLeidaRepository notificacionLeidaRepository,
            CurrentUserProvider currentUserProvider,
            MovimientoLoteRepository movimientoLoteRepository,
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository) {
        this.leadRepository = leadRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.tareaRepository = tareaRepository;
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.notificacionLeidaRepository = notificacionLeidaRepository;
        this.currentUserProvider = currentUserProvider;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
    }

    public List<NotificacionDto> listar() {
        return listarPara(currentUserProvider.getUsuarioActual());
    }

    /** Las notificaciones pendientes de un usuario concreto (también las usa el envío push, sin sesión). */
    public List<NotificacionDto> listarPara(Usuario actual) {

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
            agregarAvisosDeVentas(actual, leidas, notificaciones);
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
            if (m.getEstadoAnterior() == m.getEstadoNuevo()) continue;
            boolean otroCambio = !apartado && !desapartado;
            // Los cambios "de otro tipo" son avisos nuevos: solo el último día, para no soltar de golpe el historial.
            if (otroCambio && m.getFecha().isBefore(LocalDateTime.now().minusDays(1))) continue;

            String tipo = apartado ? "LOTE_APARTADO" : desapartado ? "LOTE_DESAPARTADO" : "LOTE_ESTADO";
            String firma = m.getFecha().toString();
            if (leidas.contains(clave(tipo, m.getId(), firma))) continue;

            String lote = "Manzana " + m.getLote().getManzana() + ", Lote " + m.getLote().getNumeroLote() + " ("
                    + m.getLote().getDesarrollo().getNombre() + ")";
            String autor = m.getUsuario() != null && m.getNombreAsesor() == null
                    ? m.getUsuario().getNombre()
                    : m.getNombreAsesor() != null ? m.getNombreAsesor() + " (cotizador público)" : null;
            if (otroCambio) {
                notificaciones.add(NotificacionDto.loteEstado(
                        lote + " pasó de " + etiqueta(m.getEstadoAnterior()) + " a " + etiqueta(m.getEstadoNuevo())
                                + (autor != null ? " por " + autor : "") + ".",
                        m.getId(),
                        firma));
            } else if (apartado) {
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

    private static String etiqueta(EstadoLote estado) {
        return switch (estado) {
            case DISPONIBLE -> "Disponible";
            case APARTADO -> "Apartado";
            case APARTADO_A_PLAZO -> "Apartado a plazo";
            case APARTADO_CON_DINERO -> "Apartado con dinero";
            case EN_PROCESO_DE_FIRMA -> "En proceso de firma";
            case VENDIDO -> "Vendido";
        };
    }

    /** Aviso de cada venta registrada en los últimos días por alguien más (solo admin y líder de
     * área con el módulo Ventas). La firma es el momento en que se registró. */
    private void agregarAvisosDeVentas(Usuario actual, Set<String> leidas, List<NotificacionDto> notificaciones) {
        if (actual.getRol() != Role.ADMIN
                && !ModulosAcceso.efectivos(actual).contains(Modulo.VENTAS)) {
            return;
        }
        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_AVISOS_LOTES);
        for (Venta v : ventaRepository.recientesDeOtros(desde, actual.getId())) {
            String firma = v.getFechaCreacion().toString();
            if (leidas.contains(clave("VENTA_NUEVA", v.getId(), firma))) continue;

            List<VentaLote> lotes = ventaLoteRepository.findByVentaId(v.getId());
            String detalle = lotes.stream()
                    .map(vl -> "Mz " + vl.getLote().getManzana() + " Lote " + vl.getLote().getNumeroLote() + " ("
                            + vl.getLote().getDesarrollo().getNombre() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            java.math.BigDecimal total = lotes.stream().map(VentaLote::getPrecio).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            String por = v.getRegistradaPor() != null ? " Registrada por " + v.getRegistradaPor().getNombre() + "." : "";
            notificaciones.add(NotificacionDto.ventaNueva(
                    "Nueva venta #" + v.getNumero() + ": " + v.getCliente() + " · " + detalle + " · $"
                            + String.format("%,.2f", total) + " · asesor " + v.getAsesorNombre() + "." + por,
                    v.getId(),
                    firma));
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

    public static String clave(String tipo, Long entidadId, String firma) {
        return tipo + "|" + entidadId + "|" + firma;
    }
}
