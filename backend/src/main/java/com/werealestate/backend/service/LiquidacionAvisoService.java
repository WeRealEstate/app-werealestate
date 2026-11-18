package com.werealestate.backend.service;

import com.werealestate.backend.dto.LiquidacionAvisoDto;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.LiquidacionAviso;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.LiquidacionAvisoRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Correos de liquidación: cuando el cliente termina de pagar su terreno se manda una felicitación al titular y un aviso a
 * finanzas ("¡Es hora de realizar una carta finiquito!"). Hoy los dispara a mano el botón "Enviar correos de
 * liquidación" del detalle de la venta (solo admin y administración); la lógica queda aquí, separada del botón, para que
 * más adelante la llame el registro del recibo que liquida la venta ({@link #preparar} arma lo que se diría y
 * {@link #enviarCorreos} lo manda; {@link #enviar} es el flujo manual completo). Aún NO se automatiza.
 *
 * <p>Solo existe para ventas de SAMAI Campestre. Liquidada = saldo pendiente &le; $0.01 (el mismo cálculo de
 * {@link ConsultasVenta}: precio de los lotes menos los recibos vigentes). Sale por la misma cuenta de Gmail que la
 * bienvenida, un intento por correo y de forma síncrona. El de finanzas se manda primero (es el que siempre aplica);
 * si el titular no tiene correo solo se avisa a finanzas. Si el de finanzas falla (502) no se registra nada; si sale y
 * el del cliente falla, se registra lo que salió y se responde 502 explicándolo.
 */
@Service
@Transactional(readOnly = true)
public class LiquidacionAvisoService {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionAvisoService.class);

    static final String SIN_PLANTILLA = "Este desarrollo aún no tiene correos de liquidación";
    static final String NO_LIQUIDADA = "La venta aún no está liquidada: todavía tiene saldo pendiente";
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final String NOMBRE_REMITENTE = "We Real Estate";

    private final AutorizacionVentas autorizacion;
    private final ConsultasVenta consultas;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final LiquidacionAvisoRepository repositorio;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final String urlBase;

    public LiquidacionAvisoService(
            AutorizacionVentas autorizacion,
            ConsultasVenta consultas,
            VentaLoteRepository ventaLoteRepository,
            VentaCopropietarioRepository copropietarioRepository,
            LiquidacionAvisoRepository repositorio,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String remitente,
            @Value("${app.url-base:https://app.weinversiones.com}") String urlBase) {
        this.autorizacion = autorizacion;
        this.consultas = consultas;
        this.ventaLoteRepository = ventaLoteRepository;
        this.copropietarioRepository = copropietarioRepository;
        this.repositorio = repositorio;
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
        String base = urlBase == null ? "" : urlBase.trim();
        this.urlBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Lo que se diría en los dos correos de una venta, con el motivo si no se pueden mandar. */
    record Preparado(
            Venta venta,
            String correoCliente,
            LiquidacionAvisoCorreo.ContenidoCliente cliente,
            LiquidacionAvisoCorreo.ContenidoFinanzas finanzas,
            boolean disponible,
            String motivoNoDisponible) {

        boolean puedeEnviar() {
            return motivoNoDisponible == null;
        }
    }

    /** Qué salió: finanzas siempre (si no, es 502), el cliente solo si tenía correo y no falló. */
    record Resultado(boolean finanzasEnviado, boolean clienteEnviado, ResponseStatusException falloCliente) {
    }

    /** Estado del botón en el detalle de la venta. */
    public LiquidacionAvisoDto consultar(Long ventaId) {
        autorizacion.exigirAdminOLider();
        return aDto(preparar(consultas.obtenerEntidad(ventaId)));
    }

    /**
     * Envía los correos ahora y registra el envío. 400 si el desarrollo no tiene plantilla o la venta aún no está
     * liquidada; 503 si la app no tiene correo configurado; 502 si el servidor de correo falla (si ya había salido el de
     * finanzas y falló el del cliente, el envío queda registrado). {@code noRollbackFor}: ese registro parcial debe
     * guardarse aunque se responda con error.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LiquidacionAvisoDto enviar(Long ventaId) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Preparado p = preparar(consultas.obtenerEntidad(ventaId));
        if (!p.puedeEnviar()) throw new ValidationException(p.motivoNoDisponible());
        Resultado r = enviarCorreos(p);
        repositorio.save(new LiquidacionAviso(p.venta(), actual, p.correoCliente(), r.clienteEnviado(), r.finanzasEnviado()));
        log.info("Correos de liquidación de la venta {}: finanzas {}, cliente {} (por {})",
                p.venta().getEtiqueta(), r.finanzasEnviado(), p.correoCliente() == null ? "sin correo" : r.clienteEnviado(),
                actual.getEmail());
        if (r.falloCliente() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Se avisó a finanzas, pero no se pudo enviar el correo al cliente. Al reenviar, finanzas recibirá "
                            + "el aviso otra vez");
        }
        return aDto(preparar(p.venta()));
    }

    /** Arma lo que se diría (sin revisar rol: lo comparte el botón manual y, más adelante, el registro del recibo que liquida). */
    Preparado preparar(Venta venta) {
        Cliente titular = venta.getClienteRef();
        String correo = titular == null || titular.getCorreo() == null || titular.getCorreo().isBlank()
                ? null
                : titular.getCorreo().trim();
        List<VentaLote> ventaLotes = ventaLoteRepository.findByVentaId(venta.getId());
        Set<String> desarrollos = new LinkedHashSet<>();
        for (VentaLote vl : ventaLotes) desarrollos.add(vl.getLote().getDesarrollo().getNombre().trim());
        boolean disponible = desarrollos.size() == 1
                && BienvenidaClienteCorreo.SAMAI.equalsIgnoreCase(desarrollos.iterator().next());
        BigDecimal precio = consultas.precioVenta(venta.getId());
        List<PagoVenta> pagos = consultas.pagosVigentes(venta.getId());
        BigDecimal pagado = pagos.stream().map(PagoVenta::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        String motivo = null;
        if (!disponible) motivo = SIN_PLANTILLA;
        else if (precio.subtract(pagado).compareTo(TOLERANCIA) > 0) motivo = NO_LIQUIDADA;

        String etiqueta = venta.getEtiqueta();
        List<String> lotes = ventaLotes.stream()
                .map(l -> "Mz " + l.getLote().getManzana() + " / Lote " + l.getLote().getNumeroLote()).toList();
        String nombre = titular != null ? titular.nombreCompleto() : venta.getCliente();
        LocalDate ultimoPago = pagos.stream().map(PagoVenta::getFecha).max(LocalDate::compareTo).orElse(null);
        List<LiquidacionAvisoCorreo.Persona> copropietarios = copropietarioRepository
                .findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                .map(VentaCopropietario::getCliente).map(LiquidacionAvisoService::persona).toList();
        var finanzas = new LiquidacionAvisoCorreo.ContenidoFinanzas(
                etiqueta,
                desarrollos.isEmpty() ? "" : String.join(", ", desarrollos),
                lotes,
                venta.getNumeroContrato(),
                venta.getAsesorNombre(),
                titular != null
                        ? persona(titular)
                        : new LiquidacionAvisoCorreo.Persona(nombre, null, null),
                copropietarios,
                precio,
                pagado,
                ultimoPago,
                urlBase + "/panel/ventas/" + etiqueta);
        var cliente = new LiquidacionAvisoCorreo.ContenidoCliente(nombre, etiqueta, lotes);
        return new Preparado(venta, correo, cliente, finanzas, disponible, motivo);
    }

    private static LiquidacionAvisoCorreo.Persona persona(Cliente c) {
        return new LiquidacionAvisoCorreo.Persona(c.nombreCompleto(), c.getTelefono(), c.getCorreo());
    }

    /**
     * Manda los correos: primero el de finanzas (si falla, 502 y no sale ninguno) y luego, si el titular tiene correo, el
     * del cliente (si falla, queda en el resultado para que el llamador decida). 503 si no hay correo configurado.
     */
    Resultado enviarCorreos(Preparado p) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El correo de la app no está configurado");
        }
        String etiqueta = p.venta().getEtiqueta();
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, NOMBRE_REMITENTE);
            helper.setTo(LiquidacionAvisoCorreo.DESTINATARIO_FINANZAS);
            helper.setSubject(LiquidacionAvisoCorreo.asuntoFinanzas(etiqueta));
            helper.setText(LiquidacionAvisoCorreo.textoFinanzas(p.finanzas()), LiquidacionAvisoCorreo.htmlFinanzas(p.finanzas()));
            sender.send(mensaje);
        } catch (Exception e) {
            log.warn("No se pudo avisar a finanzas de la liquidación de la venta {}: {}", etiqueta, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo. Inténtalo de nuevo en unos minutos");
        }
        if (p.correoCliente() == null) return new Resultado(true, false, null);
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, NOMBRE_REMITENTE);
            helper.setTo(p.correoCliente());
            helper.setSubject(LiquidacionAvisoCorreo.ASUNTO_CLIENTE);
            helper.setText(LiquidacionAvisoCorreo.textoCliente(p.cliente()), LiquidacionAvisoCorreo.htmlCliente(p.cliente()));
            // Las imágenes van dentro del correo (cid:), las mismas de la bienvenida.
            for (BienvenidaClienteCorreo.Imagen i : BienvenidaClienteCorreo.IMAGENES) {
                helper.addInline(i.cid(), new ClassPathResource(i.recurso()), i.tipo());
            }
            sender.send(mensaje);
            return new Resultado(true, true, null);
        } catch (Exception e) {
            log.warn("No se pudo enviar la felicitación de liquidación de la venta {}: {}", etiqueta, e.toString());
            return new Resultado(true, false, new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el correo al cliente"));
        }
    }

    private LiquidacionAvisoDto aDto(Preparado p) {
        var ultimo = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(p.venta().getId())
                .map(r -> new LiquidacionAvisoDto.Ultimo(
                        r.getEnviadoEn(), r.getUsuario() != null ? r.getUsuario().getNombre() : null,
                        r.getCorreoCliente(), r.isClienteEnviado(), r.isFinanzasEnviado()))
                .orElse(null);
        return new LiquidacionAvisoDto(p.disponible(), p.puedeEnviar(), p.motivoNoDisponible(), p.correoCliente(), ultimo);
    }
}
