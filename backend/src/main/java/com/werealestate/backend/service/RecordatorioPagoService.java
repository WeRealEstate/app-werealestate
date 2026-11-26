package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.dto.RecordatorioPagoDto;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.RecordatorioPago;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.RecordatorioPagoRepository;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recordatorio de pago por correo al cliente titular de una venta. Hoy lo dispara a mano el botón "Enviar recordatorio
 * de pago" del detalle de la venta (solo admin y administración); la lógica queda aquí, separada del botón, para que
 * más adelante la pueda llamar un proceso automático ({@link #preparar} arma lo que se diría y {@link #enviarCorreo}
 * lo manda; {@link #enviar} es el flujo manual completo).
 *
 * <p>El próximo pago y el atraso salen del mismo cálculo que la tabla de amortización
 * ({@link EstadoCuentaService#paraVenta}, que usa CalendarioPagos), así el correo dice lo mismo que ve el equipo. Sale
 * por la misma cuenta de Gmail que el aviso a finanzas (MAIL_USERNAME / MAIL_PASSWORD). El envío es síncrono, con un
 * solo intento: quien da clic debe ver si salió o falló. Solo los enviados con éxito quedan registrados.
 *
 * <p>No se recuerda un pago ya abonado: si la tabla lo muestra cubierto, el próximo pago es otro; y si ya tiene un
 * abono y le faltan menos de {@link #FALTANTE_SIN_AVISO}, tampoco sale (decisión del usuario, 2026-11-26).
 */
@Service
@Transactional(readOnly = true)
public class RecordatorioPagoService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioPagoService.class);

    static final String SIN_CORREO = "El cliente no tiene correo registrado";
    static final String SIN_PENDIENTES = "No hay pagos pendientes";
    private static final String NOMBRE_REMITENTE = "We Real Estate";
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    /** Con un abono registrado en ese pago, un faltante menor a esto ya no se recuerda. */
    static final BigDecimal FALTANTE_SIN_AVISO = new BigDecimal("100");

    private final AutorizacionVentas autorizacion;
    private final ConsultasVenta consultas;
    private final EstadoCuentaService estadoCuenta;
    private final RecordatorioPagoRepository repositorio;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;

    public RecordatorioPagoService(
            AutorizacionVentas autorizacion,
            ConsultasVenta consultas,
            EstadoCuentaService estadoCuenta,
            RecordatorioPagoRepository repositorio,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String remitente) {
        this.autorizacion = autorizacion;
        this.consultas = consultas;
        this.estadoCuenta = estadoCuenta;
        this.repositorio = repositorio;
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
    }

    /** Lo que se le diría al cliente de una venta, con el motivo si no se puede mandar.
     *
     * @param yaAbonado el motivo cuando el próximo pago ya tiene abono y le falta poco; null si no es el caso */
    record Preparado(
            Venta venta,
            Cliente titular,
            String correo,
            RecordatorioPagoCorreo.Contenido contenido,
            String motivoNoDisponible,
            String yaAbonado) {

        boolean puedeEnviar() {
            return motivoNoDisponible == null;
        }
    }

    /** Estado del botón en el detalle de la venta. */
    public RecordatorioPagoDto consultar(Long ventaId) {
        autorizacion.exigirAdminOLider();
        return aDto(preparar(consultas.obtenerEntidad(ventaId), LocalDate.now()));
    }

    /** Envía el recordatorio ahora y lo registra. 400 si el cliente no tiene correo, no hay pagos pendientes o el próximo
     * ya está abonado; 503 si la
     * app no tiene correo configurado; 502 si el servidor de correo rechaza el envío. */
    @Transactional
    public RecordatorioPagoDto enviar(Long ventaId) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Preparado p = preparar(consultas.obtenerEntidad(ventaId), LocalDate.now());
        if (!p.puedeEnviar()) throw new ValidationException(p.motivoNoDisponible());
        enviarCorreo(p);
        var proximo = p.contenido().proximoPago();
        repositorio.save(new RecordatorioPago(p.venta(), actual, p.correo(),
                proximo == null ? null : proximo.fecha(), proximo == null ? null : proximo.monto(),
                p.contenido().atrasoMonto()));
        log.info("Recordatorio de pago de la venta {} enviado a {} por {}", p.venta().getEtiqueta(), p.correo(), actual.getEmail());
        return aDto(preparar(p.venta(), LocalDate.now()));
    }

    /** Arma lo que se diría (sin revisar rol: lo comparte el botón manual y, más adelante, el envío automático). */
    Preparado preparar(Venta venta, LocalDate hoy) {
        AmortizacionDto a = estadoCuenta.paraVenta(venta, hoy);
        Cliente titular = venta.getClienteRef();
        String correo = titular == null || titular.getCorreo() == null || titular.getCorreo().isBlank()
                ? null
                : titular.getCorreo().trim();
        boolean hayAtraso = a.atrasoMonto() != null && a.atrasoMonto().compareTo(TOLERANCIA) > 0;
        RecordatorioPagoCorreo.ProximoPago proximo = a.proximaFecha() == null || a.proximoMonto() == null
                ? null
                : new RecordatorioPagoCorreo.ProximoPago(a.proximaFecha(), a.proximoMonto(), conceptoDelPago(a), arrastreDelPago(a));
        RecordatorioPagoCorreo.Contenido contenido = new RecordatorioPagoCorreo.Contenido(
                titular != null ? titular.nombreCompleto() : a.cliente(),
                a.clienteNumeros() == null || a.clienteNumeros().isEmpty() ? null : String.join(", ", a.clienteNumeros()),
                a.ventaNumero(),
                a.desarrollos(),
                a.lotes(),
                proximo,
                hayAtraso ? a.atrasoMonto() : BigDecimal.ZERO,
                hayAtraso ? a.pagosAtrasados() : 0,
                hayAtraso ? a.mensualidadesAtrasadas() : 0);
        String yaAbonado = null;
        if (proximo != null) {
            BigDecimal falta = faltanteConAbono(a, proximo.fecha());
            if (falta != null) yaAbonado = "Ya abonó, faltan " + RecordatorioPagoCorreo.dinero(falta);
        }
        String motivo = null;
        if (yaAbonado != null) motivo = yaAbonado;
        else if (correo == null) motivo = SIN_CORREO;
        else if (proximo == null && !hayAtraso) motivo = SIN_PENDIENTES;
        return new Preparado(venta, titular, correo, contenido, motivo, yaAbonado);
    }

    /** Si el pago de esa fecha ya no se debe recordar: la tabla lo muestra cubierto, o tiene abono y le falta poco. */
    boolean pagoAbonado(Venta venta, LocalDate fecha, LocalDate hoy) {
        AmortizacionDto a = estadoCuenta.paraVenta(venta, hoy);
        List<AmortizacionDto.Fila> filas = filasDe(a, fecha);
        return !filas.isEmpty() && (falta(filas).compareTo(TOLERANCIA) <= 0 || faltanteConAbono(a, fecha) != null);
    }

    /** Lo que le falta al pago de esa fecha cuando ya tiene algún abono y es poco; null en cualquier otro caso. */
    private static BigDecimal faltanteConAbono(AmortizacionDto a, LocalDate fecha) {
        List<AmortizacionDto.Fila> filas = filasDe(a, fecha);
        // El abono debe ser del renglón que aún debe: el enganche pagado el mismo día no cuenta para la mensualidad.
        if (filas.stream().noneMatch(f -> f.abonadoMes().signum() > 0
                && f.monto().subtract(f.pagado()).compareTo(TOLERANCIA) > 0)) {
            return null;
        }
        BigDecimal falta = falta(filas);
        return falta.compareTo(TOLERANCIA) > 0 && falta.compareTo(FALTANTE_SIN_AVISO) < 0 ? falta : null;
    }

    /** Los renglones del plan en esa fecha (los pagos extemporáneos no son un pago programado). */
    private static List<AmortizacionDto.Fila> filasDe(AmortizacionDto a, LocalDate fecha) {
        return a.detalleMensual().stream()
                .filter(f -> fecha.equals(f.fecha()) && !"Pago extemporáneo".equals(f.concepto()))
                .toList();
    }

    private static BigDecimal falta(List<AmortizacionDto.Fila> filas) {
        return filas.stream().map(f -> f.monto().subtract(f.pagado()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** El concepto del próximo pago como lo muestra la tabla ("Mensualidad 7 de 60"): los renglones de la tabla mes con
     * mes de esa fecha que aún les falta algo (puede haber más de uno, p. ej. enganche y mensualidad el mismo día). */
    private static String conceptoDelPago(AmortizacionDto a) {
        List<String> conceptos = new ArrayList<>();
        for (AmortizacionDto.Fila f : a.detalleMensual()) {
            if (f.fecha() == null || !f.fecha().equals(a.proximaFecha())) continue;
            if (f.monto().subtract(f.pagado()).compareTo(TOLERANCIA) <= 0) continue;
            String texto = f.concepto();
            if (f.numero() != null) {
                texto += " " + f.numero();
                if ("Mensualidad".equals(f.concepto()) && a.plazoMeses() != null) texto += " de " + a.plazoMeses();
            }
            conceptos.add(texto);
        }
        return conceptos.isEmpty() ? "Pago pendiente" : String.join(" + ", conceptos);
    }

    /** Lo que el próximo pago trae de pagos anteriores sin cubrir (columna arrastre de la tabla mes con mes en esa
     * fecha), sin pasar del monto del pago. */
    private static BigDecimal arrastreDelPago(AmortizacionDto a) {
        BigDecimal arrastre = BigDecimal.ZERO;
        for (AmortizacionDto.Fila f : a.detalleMensual()) {
            if (f.fecha() != null && f.fecha().equals(a.proximaFecha()) && f.arrastre() != null && f.arrastre().signum() > 0) {
                arrastre = arrastre.add(f.arrastre());
            }
        }
        return arrastre.min(a.proximoMonto());
    }

    /** Un solo intento, síncrono. Errores claros para quien dio clic. */
    void enviarCorreo(Preparado p) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El correo de la app no está configurado");
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, NOMBRE_REMITENTE);
            helper.setTo(p.correo());
            helper.setSubject(RecordatorioPagoCorreo.asunto(p.contenido()));
            helper.setText(RecordatorioPagoCorreo.texto(p.contenido()), RecordatorioPagoCorreo.html(p.contenido()));
            // Logo y círculo van dentro del correo (cid:), sin depender de un servidor de imágenes.
            for (RecordatorioPagoCorreo.Imagen img : RecordatorioPagoCorreo.IMAGENES) {
                helper.addInline(img.cid(), new org.springframework.core.io.ClassPathResource(img.archivo()), img.tipo());
            }
            sender.send(mensaje);
        } catch (Exception e) {
            log.warn("No se pudo enviar el recordatorio de pago de la venta {}: {}", p.venta().getEtiqueta(), e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo. Inténtalo de nuevo en unos minutos");
        }
    }

    private RecordatorioPagoDto aDto(Preparado p) {
        var c = p.contenido();
        var ultimo = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(p.venta().getId())
                .map(r -> new RecordatorioPagoDto.Ultimo(
                        r.getEnviadoEn(), r.getUsuario() != null ? r.getUsuario().getNombre() : null, r.getCorreoDestino()))
                .orElse(null);
        return new RecordatorioPagoDto(
                p.puedeEnviar(),
                p.motivoNoDisponible(),
                p.correo(),
                c.proximoPago() == null ? null
                        : new RecordatorioPagoDto.ProximoPago(
                                c.proximoPago().fecha(), c.proximoPago().monto(), c.proximoPago().concepto(), c.proximoPago().arrastre()),
                c.atrasoMonto(),
                c.pagosAtrasados(),
                c.mensualidadesAtrasadas(),
                ultimo);
    }
}
