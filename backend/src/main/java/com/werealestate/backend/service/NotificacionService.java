package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.NotificacionMarcarLeidaRequest;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.EventoCalendario;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Tarea;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.WeFormRespuesta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.NotificacionLeidaRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    static final String TIPO_REGISTRO_VENTA = "REGISTRO_VENTA_NUEVO";
    /** Nombre que tenía este aviso antes de llamarse "registro de venta": lo ya leído o enviado con él sigue contando. */
    static final String TIPO_REGISTRO_VENTA_ANTERIOR = "PRECONTRATO_NUEVO";
    private static final Set<EstadoLote> ESTADOS_APARTADO =
            Set.of(EstadoLote.APARTADO, EstadoLote.APARTADO_A_PLAZO, EstadoLote.APARTADO_CON_DINERO);
    private static final List<EstadoLead> CERRADOS = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);

    private final SeguimientoRepository seguimientoRepository;
    private final TareaRepository tareaRepository;
    private final EventoCalendarioRepository eventoCalendarioRepository;
    private final NotificacionLeidaRepository notificacionLeidaRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final com.werealestate.backend.repository.WeFormRespuestaRepository respuestaRepository;
    private final LoteRepository loteRepository;

    public NotificacionService(
            SeguimientoRepository seguimientoRepository,
            TareaRepository tareaRepository,
            EventoCalendarioRepository eventoCalendarioRepository,
            NotificacionLeidaRepository notificacionLeidaRepository,
            CurrentUserProvider currentUserProvider,
            MovimientoLoteRepository movimientoLoteRepository,
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            com.werealestate.backend.repository.WeFormRespuestaRepository respuestaRepository,
            LoteRepository loteRepository) {
        this.seguimientoRepository = seguimientoRepository;
        this.tareaRepository = tareaRepository;
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.notificacionLeidaRepository = notificacionLeidaRepository;
        this.currentUserProvider = currentUserProvider;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.respuestaRepository = respuestaRepository;
        this.loteRepository = loteRepository;
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

        LocalDateTime ahora = LocalDateTime.now();
        List<NotificacionDto> notificaciones = new ArrayList<>();

        // Leads abiertos (todos para el admin, los suyos para los demás) cuyo último seguimiento ya pide el siguiente:
        // la consulta trae solo esos, con su lead, ya en el orden de antes. Antes se cargaban todos los leads y todos
        // sus seguimientos en cada consulta de la campana, y eso crecía con cada seguimiento registrado.
        List<Seguimiento> vencidos = actual.getRol() == Role.ADMIN
                ? seguimientoRepository.ultimosVencidos(CERRADOS, ahora)
                : seguimientoRepository.ultimosVencidosDeAsesor(actual.getId(), CERRADOS, ahora);
        Set<Long> leadsAvisados = new HashSet<>();
        for (Seguimiento ultimo : vencidos) {
            Lead lead = ultimo.getLead();
            // Dos seguimientos con la misma fecha exacta: basta un aviso por lead.
            if (!leadsAvisados.add(lead.getId())) continue;
            String firma = ultimo.getProximoSeguimiento().toString();
            if (!leidas.contains(clave("SEGUIMIENTO_PENDIENTE", lead.getId(), firma))) {
                notificaciones.add(NotificacionDto.seguimientoPendiente(
                        "Seguimiento pendiente con " + lead.getNombreCliente() + ".", lead.getId(), firma));
            }
        }

        LocalDate hoy = LocalDate.now();

        for (Tarea tarea : tareaRepository
                .findByAsignadoAIdAndCompletadaFalseAndFechaLimiteLessThanEqualOrderByFechaLimiteAscFechaCreacionDesc(
                        actual.getId(), hoy)) {
            LocalDate fechaLimite = tarea.getFechaLimite();
            if (fechaLimite != null) {
                String firma = fechaLimite.toString();
                if (!leidas.contains(clave("TAREA_PENDIENTE", tarea.getId(), firma))) {
                    notificaciones.add(NotificacionDto.tareaPendiente(
                            "Tarea pendiente: " + tarea.getTitulo(), tarea.getId(), firma));
                }
            }
        }

        for (EventoCalendario evento : eventoCalendarioRepository
                .findByUsuarioIdAndRecordatorioTrueAndFechaLessThanEqualOrderByFechaAsc(actual.getId(), hoy.plusDays(1))) {
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
        agregarAvisosDeRegistrosDeVenta(actual, leidas, notificaciones);
        if (actual.getRol() == Role.ADMIN || actual.getRol() == Role.LIDER_AREA) {
            agregarAvisosDeLotesDesincronizados(notificaciones);
        }

        return notificaciones;
    }

    /**
     * Lotes que no cuadran con Ventas, uno por aviso, para admin y Administración: un lote Vendido sin ninguna venta
     * registrada y un lote que está en una venta pero no está Vendido ni En proceso de firma (datos de antes de que la
     * venta mandara el estado del lote; hoy ninguna pantalla deja crear ninguno de los dos). Son avisos fijos: no se
     * descartan ni cuentan como leídos; desaparecen solos al corregir el lote. La firma es la fecha del último cambio de
     * estado del lote, para que el push se mande una sola vez por ocurrencia.
     */
    private void agregarAvisosDeLotesDesincronizados(List<NotificacionDto> notificaciones) {
        for (Lote lote : loteRepository.findVendidosSinVenta()) {
            notificaciones.add(NotificacionDto.loteSinVenta(
                    nombreLote(lote) + " está marcado como Vendido sin una venta registrada. Registra su venta o, si la "
                            + "empresa se lo guarda, cámbialo a Reservado por We.",
                    lote.getId(),
                    String.valueOf(lote.getFechaCambioEstado())));
        }
        for (VentaLote vl : ventaLoteRepository.findConLoteFueraDeEstadoDeVenta()) {
            Lote lote = vl.getLote();
            notificaciones.add(NotificacionDto.loteVentaDesalineada(
                    nombreLote(lote) + " está en la venta #" + vl.getVenta().getNumero() + " pero aparece como "
                            + etiqueta(lote.getEstado()) + ". Debe estar Vendido (contrato firmado) o En proceso de firma.",
                    lote.getId(),
                    vl.getVenta().getId(),
                    lote.getFechaCambioEstado() + ":" + vl.getVenta().getId()));
        }
    }

    /** "Mz 1 / Lote 2 de SAMAI Campestre". */
    private static String nombreLote(Lote lote) {
        return "Mz " + lote.getManzana() + " / Lote " + lote.getNumeroLote() + " de " + lote.getDesarrollo().getNombre();
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
            // El aviso del registro de venta (ver agregarAvisosDeRegistrosDeVenta) ya cuenta este cambio de lote.
            if (LoteService.esNotaDeRegistroDeVenta(m.getNota())) continue;
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
            case RESERVADO_WE -> "Reservado por We";
            case VENDIDO -> "Vendido";
        };
    }

    /** Aviso de cada venta registrada en los últimos días por alguien más (solo admin y líder de
     * área con el módulo Ventas). La firma es el momento en que se registró. Precarga en bloque las ventas que
     * vinieron de We Forms y los lotes de cada venta para evitar consultas N+1. */
    private void agregarAvisosDeVentas(Usuario actual, Set<String> leidas, List<NotificacionDto> notificaciones) {
        if (actual.getRol() != Role.ADMIN
                && !ModulosAcceso.efectivos(actual).contains(Modulo.VENTAS)) {
            return;
        }
        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_AVISOS_LOTES);
        List<Venta> recientes = ventaRepository.recientesDeOtros(desde, actual.getId());
        if (recientes.isEmpty()) return;

        List<Long> ventaIds = recientes.stream().map(Venta::getId).toList();
        Set<Long> conRespuesta = new HashSet<>(respuestaRepository.ventaIdsConRespuesta(ventaIds));
        java.util.Map<Long, List<VentaLote>> lotesPorVenta = new java.util.HashMap<>();
        for (VentaLote vl : ventaLoteRepository.findByVentaIdInOrderByIdAsc(ventaIds)) {
            lotesPorVenta.computeIfAbsent(vl.getVenta().getId(), k -> new ArrayList<>()).add(vl);
        }

        for (Venta v : recientes) {
            // Una venta que nació de un registro de venta se avisa como tal (ver agregarAvisosDeRegistrosDeVenta).
            if (conRespuesta.contains(v.getId())) continue;
            String firma = v.getFechaCreacion().toString();
            if (leidas.contains(clave("VENTA_NUEVA", v.getId(), firma))) continue;

            List<VentaLote> lotes = lotesPorVenta.getOrDefault(v.getId(), List.of());
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

    /** Aviso de cada registro de venta de We Forms enviado en los últimos días (admin y quien tenga el módulo We
     * Forms). Reemplaza al aviso de venta nueva y de cambio de lote que ese mismo registro de venta generaría. */
    private void agregarAvisosDeRegistrosDeVenta(Usuario actual, Set<String> leidas, List<NotificacionDto> notificaciones) {
        if (actual.getRol() != Role.ADMIN && !ModulosAcceso.efectivos(actual).contains(Modulo.WE_FORMS)) {
            return;
        }
        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_AVISOS_LOTES);
        List<WeFormRespuesta> recientes = respuestaRepository.recientes(desde);
        if (recientes.isEmpty()) return;

        List<Long> ventaIds = recientes.stream()
                .map(WeFormRespuesta::getVenta)
                .filter(java.util.Objects::nonNull)
                .map(Venta::getId)
                .toList();
        java.util.Map<Long, List<VentaLote>> lotesPorVenta = new java.util.HashMap<>();
        if (!ventaIds.isEmpty()) {
            for (VentaLote vl : ventaLoteRepository.findByVentaIdInOrderByIdAsc(ventaIds)) {
                lotesPorVenta.computeIfAbsent(vl.getVenta().getId(), k -> new ArrayList<>()).add(vl);
            }
        }

        for (WeFormRespuesta r : recientes) {
            String firma = r.getFechaEnvio().toString();
            if (leidas.contains(clave(TIPO_REGISTRO_VENTA, r.getId(), firma))
                    || leidas.contains(clave(TIPO_REGISTRO_VENTA_ANTERIOR, r.getId(), firma))) continue;
            Venta v = r.getVenta();
            String lotes = v == null ? "" : lotesPorVenta.getOrDefault(v.getId(), List.of()).stream()
                    .map(vl -> "Mz " + vl.getLote().getManzana() + " Lote " + vl.getLote().getNumeroLote())
                    .collect(java.util.stream.Collectors.joining(", "));
            String cliente = r.getCliente() != null ? r.getCliente().nombreCompleto() : "un cliente";
            notificaciones.add(NotificacionDto.registroVentaNuevo(
                    "Nueva venta registrada por " + r.getAsesorNombre() + ": " + cliente + (r.isClienteNuevo() ? " (cliente nuevo)" : "")
                            + (lotes.isEmpty() ? "" : " · " + lotes)
                            + (v != null ? " · venta #" + v.getNumero() : "") + ".",
                    r.getId(),
                    v != null ? v.getId() : null,
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
