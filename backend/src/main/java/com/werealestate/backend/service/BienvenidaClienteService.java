package com.werealestate.backend.service;

import com.werealestate.backend.dto.BienvenidaClienteDto;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.BienvenidaClienteRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.mail.internet.MimeMessage;
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
 * Correo de bienvenida al cliente titular de una venta. Hoy lo dispara a mano el botón "Enviar correo de bienvenida"
 * del detalle de la venta (solo admin y administración); la lógica queda aquí, separada del botón, para que más
 * adelante la llame {@code ContratoVentaService} al firmarse el contrato ({@link #preparar} arma lo que se diría y
 * {@link #enviarCorreo} lo manda; {@link #enviar} es el flujo manual completo).
 *
 * <p>Solo existe la plantilla de SAMAI Campestre. Sale por la misma cuenta de Gmail que el recordatorio de pago, con un
 * solo intento y de forma síncrona: quien da clic debe ver si salió o falló. Solo los enviados con éxito quedan
 * registrados.
 */
@Service
@Transactional(readOnly = true)
public class BienvenidaClienteService {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaClienteService.class);

    static final String SIN_PLANTILLA = "Este desarrollo aún no tiene correo de bienvenida";
    static final String SIN_FIRMA = "El contrato aún no está firmado";
    static final String SIN_CORREO = "El cliente no tiene correo registrado";
    private static final String NOMBRE_REMITENTE = "We Real Estate";

    private final AutorizacionVentas autorizacion;
    private final ConsultasVenta consultas;
    private final VentaLoteRepository ventaLoteRepository;
    private final BienvenidaClienteRepository repositorio;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;

    public BienvenidaClienteService(
            AutorizacionVentas autorizacion,
            ConsultasVenta consultas,
            VentaLoteRepository ventaLoteRepository,
            BienvenidaClienteRepository repositorio,
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.username:}") String remitente) {
        this.autorizacion = autorizacion;
        this.consultas = consultas;
        this.ventaLoteRepository = ventaLoteRepository;
        this.repositorio = repositorio;
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
    }

    /** Lo que se le diría al cliente de una venta, con el motivo si no se puede mandar. */
    record Preparado(
            Venta venta,
            Cliente titular,
            String correo,
            BienvenidaClienteCorreo.Contenido contenido,
            boolean disponible,
            String motivoNoDisponible) {

        boolean puedeEnviar() {
            return motivoNoDisponible == null;
        }
    }

    /** Estado del botón en el detalle de la venta. */
    public BienvenidaClienteDto consultar(Long ventaId) {
        autorizacion.exigirAdminOLider();
        return aDto(preparar(consultas.obtenerEntidad(ventaId)));
    }

    /** Envía la bienvenida ahora y la registra. 400 si el desarrollo no tiene plantilla, el contrato no está firmado o
     * el cliente no tiene correo; 503 si la app no tiene correo configurado; 502 si el servidor de correo falla. */
    @Transactional
    public BienvenidaClienteDto enviar(Long ventaId) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Preparado p = preparar(consultas.obtenerEntidad(ventaId));
        if (!p.puedeEnviar()) throw new ValidationException(p.motivoNoDisponible());
        enviarCorreo(p);
        repositorio.save(new com.werealestate.backend.model.BienvenidaCliente(p.venta(), actual, p.correo()));
        log.info("Bienvenida de la venta {} enviada a {} por {}", p.venta().getEtiqueta(), p.correo(), actual.getEmail());
        return aDto(preparar(p.venta()));
    }

    /** Arma lo que se diría (sin revisar rol: lo comparte el botón manual y, más adelante, el envío al firmar). */
    Preparado preparar(Venta venta) {
        Cliente titular = venta.getClienteRef();
        String correo = titular == null || titular.getCorreo() == null || titular.getCorreo().isBlank()
                ? null
                : titular.getCorreo().trim();
        Set<String> desarrollos = new LinkedHashSet<>();
        for (VentaLote vl : ventaLoteRepository.findByVentaId(venta.getId())) {
            desarrollos.add(vl.getLote().getDesarrollo().getNombre().trim());
        }
        boolean disponible = desarrollos.size() == 1
                && BienvenidaClienteCorreo.SAMAI.equalsIgnoreCase(desarrollos.iterator().next());
        String motivo = null;
        if (!disponible) motivo = SIN_PLANTILLA;
        else if (!venta.isContratoFirmado()) motivo = SIN_FIRMA;
        else if (correo == null) motivo = SIN_CORREO;
        String nombre = titular != null ? titular.nombreCompleto() : "";
        return new Preparado(venta, titular, correo, new BienvenidaClienteCorreo.Contenido(nombre), disponible, motivo);
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
            helper.setSubject(BienvenidaClienteCorreo.ASUNTO);
            helper.setText(BienvenidaClienteCorreo.texto(p.contenido()), BienvenidaClienteCorreo.html(p.contenido()));
            // Las imágenes van dentro del correo (cid:), sin depender de un servidor de imágenes (Mailchimp, etc.).
            for (BienvenidaClienteCorreo.Imagen i : BienvenidaClienteCorreo.IMAGENES) {
                helper.addInline(i.cid(), new ClassPathResource(i.recurso()), i.tipo());
            }
            sender.send(mensaje);
        } catch (Exception e) {
            log.warn("No se pudo enviar la bienvenida de la venta {}: {}", p.venta().getEtiqueta(), e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo enviar el correo. Inténtalo de nuevo en unos minutos");
        }
    }

    private BienvenidaClienteDto aDto(Preparado p) {
        var ultimo = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(p.venta().getId())
                .map(r -> new BienvenidaClienteDto.Ultimo(
                        r.getEnviadoEn(), r.getUsuario() != null ? r.getUsuario().getNombre() : null, r.getCorreoDestino()))
                .orElse(null);
        return new BienvenidaClienteDto(p.disponible(), p.puedeEnviar(), p.motivoNoDisponible(), p.correo(), ultimo);
    }
}
