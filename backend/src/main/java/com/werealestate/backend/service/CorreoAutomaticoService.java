package com.werealestate.backend.service;

import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.BienvenidaCliente;
import com.werealestate.backend.model.CorreoAutomatico;
import com.werealestate.backend.model.CorreoAutomaticoConfig;
import com.werealestate.backend.model.EstadoCorreoAutomatico;
import com.werealestate.backend.model.LiquidacionAviso;
import com.werealestate.backend.model.RecordatorioPago;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.BienvenidaClienteRepository;
import com.werealestate.backend.repository.CorreoAutomaticoConfigRepository;
import com.werealestate.backend.repository.CorreoAutomaticoRepository;
import com.werealestate.backend.repository.LiquidacionAvisoRepository;
import com.werealestate.backend.repository.RecordatorioPagoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los correos que salen solos (lo llama {@link CorreoAutomaticoScheduler} cada minuto):
 * <ul>
 *   <li>Bienvenida (contrato firmado) y liquidación (venta pagada por completo): solo ventas registradas desde V94 (la liquidación solo avisa a finanzas),
 *       {@link #MARGEN_MINUTOS} minutos después; si en ese rato se quita la firma o se anula el abono, no salen.</li>
 *   <li>Recordatorio de pago: a las 6:00 p. m. del día anterior a la fecha de pago, a todas las ventas con
 *       mensualidades (también las anteriores) salvo las excluidas. Si ese pago ya tiene abono y le falta poco, no sale y
 *       queda anotado (ver {@link RecordatorioPagoService}).</li>
 * </ul>
 * Cada correo tiene su interruptor (apagado de origen). Lo que se decide queda en {@code correo_automatico}, así nunca
 * se manda dos veces; lo que sí sale se guarda además donde se guardan los envíos a mano, sin usuario. Lo que no sale
 * se puede mandar desde el registro de la venta ({@link #reintentar}).
 */
@Service
public class CorreoAutomaticoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoAutomaticoService.class);

    static final int MARGEN_MINUTOS = 10;
    static final int MAX_INTENTOS = 3;
    private static final int MINUTOS_ENTRE_INTENTOS = 5;
    static final LocalTime HORA_RECORDATORIO = LocalTime.of(18, 0);

    static final String APAGADO = "El envío automático estaba apagado";
    static final String SIN_CORREO = "El cliente no tiene correo registrado";

    private final CorreoAutomaticoRepository repositorio;
    private final CorreoAutomaticoConfigRepository configRepositorio;
    private final BienvenidaClienteService bienvenida;
    private final LiquidacionAvisoService liquidacion;
    private final RecordatorioPagoService recordatorio;
    private final BienvenidaClienteRepository bienvenidas;
    private final LiquidacionAvisoRepository liquidaciones;
    private final RecordatorioPagoRepository recordatorios;
    private final ConsultasVenta consultas;
    private final TransactionTemplate tx;

    /** El último día cuyos recordatorios ya se prepararon (para no recorrer todas las ventas cada minuto). Al
     * reiniciar se vuelve a recorrer y las filas ya guardadas evitan repetir. */
    private LocalDate diaPreparado;

    public CorreoAutomaticoService(
            CorreoAutomaticoRepository repositorio,
            CorreoAutomaticoConfigRepository configRepositorio,
            BienvenidaClienteService bienvenida,
            LiquidacionAvisoService liquidacion,
            RecordatorioPagoService recordatorio,
            BienvenidaClienteRepository bienvenidas,
            LiquidacionAvisoRepository liquidaciones,
            RecordatorioPagoRepository recordatorios,
            ConsultasVenta consultas,
            PlatformTransactionManager transactionManager) {
        this.repositorio = repositorio;
        this.configRepositorio = configRepositorio;
        this.bienvenida = bienvenida;
        this.liquidacion = liquidacion;
        this.recordatorio = recordatorio;
        this.bienvenidas = bienvenidas;
        this.liquidaciones = liquidaciones;
        this.recordatorios = recordatorios;
        this.consultas = consultas;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public boolean activo(TipoCorreoAutomatico tipo) {
        return configRepositorio.findById(tipo).map(CorreoAutomaticoConfig::isActivo).orElse(false);
    }

    /** Una vuelta del programador. */
    public void ciclo(LocalDateTime ahora) {
        tx.executeWithoutResult(s -> detectar(ahora));
        prepararRecordatorios(ahora);
        List<Long> pendientes = tx.execute(s -> repositorio.findByEstadoOrderById(EstadoCorreoAutomatico.PENDIENTE)
                .stream().map(CorreoAutomatico::getId).toList());
        for (Long id : pendientes) {
            try {
                atender(id, ahora);
            } catch (Exception e) {
                // Uno que falle no debe detener a los demás.
                log.error("Correo automático {}: {}", id, e.toString(), e);
            }
        }
    }

    // ------------------------------------------------------------------ reintento desde la pantalla

    /** Si un correo que no salió todavía se puede mandar con «Reintentar»: mientras no se haya enviado después y, en
     * los recordatorios, solo el más reciente (los anteriores ya quedaron superados) y mientras ese pago no se haya
     * abonado (si no, saldría el recordatorio del mes siguiente). */
    public boolean reintentable(CorreoAutomatico c) {
        if (c.getEstado() != EstadoCorreoAutomatico.FALLIDO && c.getEstado() != EstadoCorreoAutomatico.NO_ENVIADO) {
            return false;
        }
        Long ventaId = c.getVenta().getId();
        return switch (c.getTipo()) {
            case BIENVENIDA -> bienvenidas.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(ventaId).isEmpty();
            case LIQUIDACION -> liquidaciones.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(ventaId).isEmpty();
            case RECORDATORIO -> repositorio.findByVentaIdOrderByCreadoEnDescIdDesc(ventaId).stream()
                    .filter(o -> o.getTipo() == TipoCorreoAutomatico.RECORDATORIO)
                    .findFirst().map(o -> o.getId().equals(c.getId())).orElse(false)
                    && recordatorios.findByVentaId(ventaId).stream()
                            .noneMatch(r -> !r.getEnviadoEn().isBefore(c.getCreadoEn()))
                    && !recordatorio.pagoAbonado(c.getVenta(), c.getFechaPago(), LocalDate.now());
        };
    }

    /** Manda ahora, a nombre de quien da clic, un correo automático que no salió. Usa el mismo envío que los botones a
     * mano: revisa el rol, que todavía aplique (400), y avisa si el correo falla (502/503). */
    public void reintentar(Long ventaId, Long correoId) {
        TipoCorreoAutomatico tipo = tx.execute(s -> {
            consultas.obtenerEntidad(ventaId); // 404 también para quien no puede ver esa venta
            CorreoAutomatico c = repositorio.findById(correoId)
                    .filter(o -> o.getVenta().getId().equals(ventaId))
                    .orElseThrow(() -> new ResourceNotFoundException("Correo no encontrado"));
            if (!reintentable(c)) throw new ConflictException("Ese correo ya no se puede reintentar");
            return c.getTipo();
        });
        switch (tipo) {
            case BIENVENIDA -> bienvenida.enviar(ventaId);
            case LIQUIDACION -> liquidacion.enviar(ventaId);
            case RECORDATORIO -> recordatorio.enviar(ventaId);
        }
    }

    // ------------------------------------------------------------------ qué hay que mandar

    private void detectar(LocalDateTime ahora) {
        for (Venta v : repositorio.firmadasSinBienvenida()) {
            var p = bienvenida.preparar(v);
            CorreoAutomatico c = nuevo(v, TipoCorreoAutomatico.BIENVENIDA, ahora);
            if (!p.disponible()) c.resolver(EstadoCorreoAutomatico.OMITIDO, BienvenidaClienteService.SIN_PLANTILLA, ahora);
            else if (bienvenidas.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.getId()).isPresent()) {
                c.resolver(EstadoCorreoAutomatico.OMITIDO, "Ya se había enviado a mano", ahora);
            } else if (!activo(c.getTipo())) c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, APAGADO, ahora);
            repositorio.save(c);
        }
        for (Venta v : repositorio.liquidadasSinAviso()) {
            CorreoAutomatico c = nuevo(v, TipoCorreoAutomatico.LIQUIDACION, ahora);
            if (liquidaciones.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.getId()).isPresent()) {
                c.resolver(EstadoCorreoAutomatico.OMITIDO, "Ya se había enviado a mano", ahora);
            } else if (!activo(c.getTipo())) c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, APAGADO, ahora);
            repositorio.save(c);
        }
    }

    private CorreoAutomatico nuevo(Venta v, TipoCorreoAutomatico tipo, LocalDateTime ahora) {
        return new CorreoAutomatico(v, tipo, null, ahora, ahora.plusMinutes(MARGEN_MINUTOS));
    }

    /** A partir de las 6:00 p. m., deja en cola un recordatorio por cada venta con un pago para mañana. Apagado no
     * marca el día como hecho: si se enciende esa misma noche, salen entonces. */
    private void prepararRecordatorios(LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        if (ahora.toLocalTime().isBefore(HORA_RECORDATORIO) || hoy.equals(diaPreparado)) return;
        if (!activo(TipoCorreoAutomatico.RECORDATORIO)) return;
        LocalDate manana = hoy.plusDays(1);
        List<Long> ventas = tx.execute(s -> repositorio.conRecordatorio().stream().map(Venta::getId).toList());
        for (Long ventaId : ventas) {
            try {
                tx.executeWithoutResult(s -> prepararRecordatorio(ventaId, manana, ahora));
            } catch (Exception e) {
                log.error("Recordatorio automático de la venta {}: {}", ventaId, e.toString(), e);
            }
        }
        diaPreparado = hoy;
    }

    private void prepararRecordatorio(Long ventaId, LocalDate manana, LocalDateTime ahora) {
        if (repositorio.existsByVentaIdAndTipoAndFechaPago(ventaId, TipoCorreoAutomatico.RECORDATORIO, manana)) return;
        Venta venta = consultas.obtenerEntidad(ventaId);
        var p = recordatorio.preparar(venta, ahora.toLocalDate());
        var proximo = p.contenido().proximoPago();
        if (proximo == null || !manana.equals(proximo.fecha())) return;
        CorreoAutomatico c = new CorreoAutomatico(venta, TipoCorreoAutomatico.RECORDATORIO, manana, ahora, ahora);
        boolean yaAMano = recordatorios.findByVentaId(ventaId).stream().anyMatch(r -> manana.equals(r.getProximoPagoFecha())
                && r.getEnviadoEn().toLocalDate().equals(ahora.toLocalDate()));
        if (yaAMano) c.resolver(EstadoCorreoAutomatico.OMITIDO, "Ya se había enviado a mano hoy", ahora);
        else if (p.yaAbonado() != null) c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, p.yaAbonado(), ahora);
        else if (p.correo() == null) c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, SIN_CORREO, ahora);
        repositorio.save(c);
    }

    // ------------------------------------------------------------------ envío

    private enum Paso { ESPERAR, ENVIAR, TERMINADO }

    private void atender(Long id, LocalDateTime ahora) {
        // 1) Se revisa que siga aplicando y se cuenta el intento ANTES de mandar: así un fallo al registrar no repite
        // el correo sin fin.
        Paso paso = tx.execute(s -> {
            CorreoAutomatico c = repositorio.findById(id).orElse(null);
            if (c == null || c.getEstado() != EstadoCorreoAutomatico.PENDIENTE) return Paso.TERMINADO;
            if (!sigueAplicando(c, ahora)) {
                repositorio.delete(c); // se deshizo la firma o el abono: si vuelve a pasar, se decide de nuevo
                return Paso.TERMINADO;
            }
            if (yaSeMandoAMano(c)) {
                c.resolver(EstadoCorreoAutomatico.OMITIDO, "Ya se había enviado a mano", ahora);
                return Paso.TERMINADO;
            }
            if (c.getTipo() == TipoCorreoAutomatico.RECORDATORIO) {
                String yaAbonado = recordatorio.preparar(c.getVenta(), ahora.toLocalDate()).yaAbonado();
                if (yaAbonado != null) {
                    c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, yaAbonado, ahora);
                    return Paso.TERMINADO;
                }
            }
            if (c.getEnviarDesde().isAfter(ahora)) return Paso.ESPERAR;
            if (!activo(c.getTipo())) {
                c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, APAGADO, ahora);
                return Paso.TERMINADO;
            }
            if (c.getIntentos() >= MAX_INTENTOS) {
                c.resolver(EstadoCorreoAutomatico.FALLIDO, "No se pudo enviar después de " + MAX_INTENTOS + " intentos", ahora);
                return Paso.TERMINADO;
            }
            c.intentar(ahora.plusMinutes(MINUTOS_ENTRE_INTENTOS));
            return Paso.ENVIAR;
        });
        if (paso != Paso.ENVIAR) return;
        // 2) Mandar y registrar.
        try {
            tx.executeWithoutResult(s -> enviar(repositorio.findById(id).orElseThrow(), ahora));
        } catch (Exception e) {
            String motivo = e instanceof ResponseStatusException r && r.getReason() != null ? r.getReason() : "Error al enviar";
            log.warn("Correo automático {} sin enviar: {}", id, e.toString());
            tx.executeWithoutResult(s -> repositorio.findById(id).ifPresent(c -> {
                if (c.getIntentos() >= MAX_INTENTOS) c.resolver(EstadoCorreoAutomatico.FALLIDO, motivo, ahora);
            }));
        }
    }

    private boolean sigueAplicando(CorreoAutomatico c, LocalDateTime ahora) {
        Venta v = c.getVenta();
        return switch (c.getTipo()) {
            case BIENVENIDA -> v.isContratoFirmado();
            case LIQUIDACION -> liquidacion.preparar(v).puedeEnviar();
            case RECORDATORIO -> {
                if (v.isSinRecordatorioAutomatico()) yield false;
                var proximo = recordatorio.preparar(v, ahora.toLocalDate()).contenido().proximoPago();
                yield proximo != null && proximo.fecha().equals(c.getFechaPago());
            }
        };
    }

    /** Alguien lo mandó a mano mientras estaba en cola. */
    private boolean yaSeMandoAMano(CorreoAutomatico c) {
        Long ventaId = c.getVenta().getId();
        return switch (c.getTipo()) {
            case BIENVENIDA -> bienvenidas.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(ventaId).isPresent();
            case LIQUIDACION -> liquidaciones.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(ventaId).isPresent();
            case RECORDATORIO -> recordatorios.findByVentaId(ventaId).stream()
                    .anyMatch(r -> c.getFechaPago().equals(r.getProximoPagoFecha())
                            && !r.getEnviadoEn().isBefore(c.getCreadoEn()));
        };
    }

    private void enviar(CorreoAutomatico c, LocalDateTime ahora) {
        Venta v = c.getVenta();
        switch (c.getTipo()) {
            case BIENVENIDA -> {
                var p = bienvenida.preparar(v);
                if (p.correo() == null) {
                    c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, SIN_CORREO, ahora);
                    return;
                }
                bienvenida.enviarCorreo(p);
                bienvenidas.save(new BienvenidaCliente(v, null, p.correo()));
                c.resolver(EstadoCorreoAutomatico.ENVIADO, null, ahora);
            }
            case LIQUIDACION -> {
                liquidacion.enviarCorreo(liquidacion.preparar(v));
                liquidaciones.save(new LiquidacionAviso(v, null, null, false, true));
                c.resolver(EstadoCorreoAutomatico.ENVIADO, null, ahora);
            }
            case RECORDATORIO -> {
                var p = recordatorio.preparar(v, ahora.toLocalDate());
                if (p.correo() == null) {
                    c.resolver(EstadoCorreoAutomatico.NO_ENVIADO, SIN_CORREO, ahora);
                    return;
                }
                recordatorio.enviarCorreo(p);
                var proximo = p.contenido().proximoPago();
                recordatorios.save(new RecordatorioPago(v, null, p.correo(), proximo.fecha(), proximo.monto(),
                        p.contenido().atrasoMonto()));
                c.resolver(EstadoCorreoAutomatico.ENVIADO, null, ahora);
            }
        }
        log.info("Correo automático {} de la venta {}: {}", c.getTipo(), v.getEtiquetaReal(), c.getEstado());
    }
}
