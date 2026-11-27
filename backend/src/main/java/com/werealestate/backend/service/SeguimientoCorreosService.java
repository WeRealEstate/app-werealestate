package com.werealestate.backend.service;

import com.werealestate.backend.model.*;
import com.werealestate.backend.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Lo enviado y no enviado del mes en curso y los recordatorios de los próximos {@link #DIAS_PREVISTOS} días calculados
 * del plan vigente. Un previsto que hoy no saldría (recordatorios apagados o cliente sin correo) se lista como
 * {@code NO_SALDRA} con el motivo, para corregirlo antes. */
@Service
@Transactional(readOnly = true)
public class SeguimientoCorreosService {
    public record Entrada(LocalDateTime fecha, String tipo, String estado, String cliente, String destino,
                          String venta, String detalle) {}
    public record Resumen(String mes, boolean recordatoriosActivos, List<Entrada> entradas) {}

    static final int DIAS_PREVISTOS = 30;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AutorizacionVentas autorizacion;
    private final BienvenidaClienteRepository bienvenidas;
    private final RecordatorioPagoRepository recordatorios;
    private final LiquidacionAvisoRepository liquidaciones;
    private final CorreoAutomaticoRepository automaticos;
    private final CorreoAutomaticoService envio;
    private final RecordatorioPagoService recordatorio;
    private final JdbcTemplate jdbc;
    private final VentaRepository ventas;
    private final EstadoCuentaService estadoCuenta;

    public SeguimientoCorreosService(AutorizacionVentas autorizacion, BienvenidaClienteRepository bienvenidas,
            RecordatorioPagoRepository recordatorios, LiquidacionAvisoRepository liquidaciones,
            CorreoAutomaticoRepository automaticos, CorreoAutomaticoService envio, RecordatorioPagoService recordatorio,
            JdbcTemplate jdbc, VentaRepository ventas, EstadoCuentaService estadoCuenta) {
        this.autorizacion = autorizacion;
        this.bienvenidas = bienvenidas;
        this.recordatorios = recordatorios;
        this.liquidaciones = liquidaciones;
        this.automaticos = automaticos;
        this.envio = envio;
        this.recordatorio = recordatorio;
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.estadoCuenta = estadoCuenta;
    }

    public Resumen mesActual() {
        autorizacion.exigirAdminOLider();
        LocalDateTime ahora = LocalDateTime.now();
        YearMonth mes = YearMonth.from(ahora);
        LocalDateTime desde = mes.atDay(1).atStartOfDay();
        LocalDateTime hasta = mes.plusMonths(1).atDay(1).atStartOfDay();
        List<Entrada> entradas = new ArrayList<>();
        bienvenidas.findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(desde, hasta).forEach(c -> {
            if (VisibilidadVentas.visible(c.getVenta())) entradas.add(entrada(c.getEnviadoEn(), "BIENVENIDA", "ENVIADO", c.getVenta(), c.getCorreoDestino(), null));
        });
        recordatorios.findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(desde, hasta).forEach(c -> {
            if (VisibilidadVentas.visible(c.getVenta())) entradas.add(entrada(c.getEnviadoEn(), "RECORDATORIO", "ENVIADO", c.getVenta(), c.getCorreoDestino(), null));
        });
        liquidaciones.findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(desde, hasta).forEach(c -> {
            if (VisibilidadVentas.visible(c.getVenta())) {
                if (c.isFinanzasEnviado()) entradas.add(entrada(c.getEnviadoEn(), "LIQUIDACION", "ENVIADO", c.getVenta(), AvisoRegistroVentaCorreo.DESTINATARIO, null));
                if (c.isClienteEnviado() && c.getCorreoCliente() != null) entradas.add(entrada(c.getEnviadoEn(), "LIQUIDACION", "ENVIADO", c.getVenta(), c.getCorreoCliente(), null));
            }
        });
        automaticos.findByResueltoEnGreaterThanEqualAndResueltoEnLessThan(desde, hasta).forEach(c -> {
            if (VisibilidadVentas.visible(c.getVenta()) && (c.getEstado() == EstadoCorreoAutomatico.FALLIDO || c.getEstado() == EstadoCorreoAutomatico.NO_ENVIADO))
                entradas.add(entrada(c.getResueltoEn(), c.getTipo().name(), c.getEstado().name(), c.getVenta(), destino(c.getVenta(), c.getTipo()), c.getDetalle()));
        });
        jdbc.query("SELECT venta_id, fecha, estado, detalle FROM historial_aviso_registro_venta WHERE fecha >= ? AND fecha < ?",
                rs -> {
                    var venta = ventas.findById(rs.getLong("venta_id")).orElse(null);
                    if (venta != null && VisibilidadVentas.visible(venta)) {
                        entradas.add(entrada(rs.getTimestamp("fecha").toLocalDateTime(), "REGISTRO_VENTA",
                                rs.getString("estado"), venta, AvisoRegistroVentaCorreo.DESTINATARIO, rs.getString("detalle")));
                    }
                }, desde, hasta);
        LocalDateTime limite = ahora.plusDays(DIAS_PREVISTOS);
        List<CorreoAutomatico> pendientes = automaticos.findByEstadoOrderById(EstadoCorreoAutomatico.PENDIENTE);
        for (CorreoAutomatico c : pendientes) {
            if (VisibilidadVentas.visible(c.getVenta()) && !c.getEnviarDesde().isBefore(desde) && !c.getEnviarDesde().isAfter(limite))
                entradas.add(entrada(c.getEnviarDesde(), c.getTipo().name(), "PENDIENTE", c.getVenta(), destino(c.getVenta(), c.getTipo()), null));
        }
        boolean activos = envio.activo(TipoCorreoAutomatico.RECORDATORIO);
        for (Venta venta : automaticos.conRecordatorio()) {
            if (!VisibilidadVentas.visible(venta)) continue;
            var fechas = estadoCuenta.paraVenta(venta, ahora.toLocalDate()).detalleMensual().stream()
                    .map(f -> f.fecha()).filter(java.util.Objects::nonNull).distinct().toList();
            for (LocalDate fechaPago : fechas) {
                LocalDateTime fechaEnvio = fechaPago.minusDays(1).atTime(LocalTime.of(18, 0));
                if (fechaEnvio.isBefore(ahora) || fechaEnvio.isAfter(limite)) continue;
                if (pendientes.stream().anyMatch(c -> c.getVenta().getId().equals(venta.getId())
                        && c.getTipo() == TipoCorreoAutomatico.RECORDATORIO && fechaPago.equals(c.getFechaPago()))) continue;
                if (automaticos.existsByVentaIdAndTipoAndFechaPago(venta.getId(), TipoCorreoAutomatico.RECORDATORIO, fechaPago)) continue;
                // Reproduce la decisión que hará el programador el día anterior, con los pagos conocidos hoy.
                var p = recordatorio.preparar(venta, fechaEnvio.toLocalDate());
                var proximo = p.contenido().proximoPago();
                if (proximo == null || !fechaPago.equals(proximo.fecha()) || p.yaAbonado() != null) continue;
                List<String> motivos = new ArrayList<>();
                if (!activos) motivos.add("recordatorios apagados");
                if (p.correo() == null) motivos.add("sin correo registrado");
                String detalle = "Pago del " + FECHA.format(fechaPago)
                        + (motivos.isEmpty() ? "" : " · " + String.join(" y ", motivos));
                entradas.add(entrada(fechaEnvio, "RECORDATORIO", motivos.isEmpty() ? "PREVISTO" : "NO_SALDRA",
                        venta, p.correo(), detalle));
            }
        }
        entradas.sort(Comparator.comparing(Entrada::fecha).reversed());
        return new Resumen(mes.toString(), activos, entradas);
    }

    private static String destino(Venta venta, TipoCorreoAutomatico tipo) {
        if (tipo == TipoCorreoAutomatico.LIQUIDACION) return AvisoRegistroVentaCorreo.DESTINATARIO;
        return venta.getClienteRef() == null ? null : venta.getClienteRef().getCorreo();
    }

    private static Entrada entrada(LocalDateTime fecha, String tipo, String estado, Venta venta, String destino, String detalle) {
        String cliente = venta.getClienteRef() == null ? venta.getCliente() : venta.getClienteRef().nombreCompleto();
        return new Entrada(fecha, tipo, estado, cliente, destino, venta.getEtiqueta(), detalle);
    }
}
