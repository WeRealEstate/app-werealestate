package com.werealestate.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/** Avisa por correo a finanzas que llegó un registro de venta de We Forms: "¡Es hora de realizar un contrato!", con el
 * cliente, el asesor, el desarrollo, los lotes, el número de venta y el formulario. Sale por SMTP (Gmail) con la cuenta
 * de MAIL_USERNAME y su contraseña de aplicación en MAIL_PASSWORD; sin ellas no se manda nada (solo queda en el log).
 * Se manda cuando el registro ya quedó guardado (si el envío del formulario falla, no hay correo) y en segundo plano:
 * el asesor no espera al servidor de correo y, si este falla, el registro sigue guardado; se reintenta un par de veces
 * y, si aun así no sale, queda en el log. */
@Component
public class AvisoRegistroVentaCorreo {

    private static final Logger log = LoggerFactory.getLogger(AvisoRegistroVentaCorreo.class);

    /** A quién le llega el aviso: fijo, por decisión de la empresa. */
    static final String DESTINATARIO = "finanzas.weinmobiliaria@gmail.com";
    static final String ASUNTO = "¡Es hora de realizar un contrato!";
    private static final String NOMBRE_REMITENTE = "We Real Estate";
    /** Antes de cada intento: el primero de inmediato y luego a los 30 s y a los 2 min. */
    private static final long[] ESPERAS_MS = {0, 30_000, 120_000};

    /** Lo que publica WeFormPublicoService.enviar al guardar un registro de venta. Lleva ya los textos del correo:
     * en segundo plano no hay transacción abierta para leer la venta. */
    public record RegistroDeVentaRecibido(
            String cliente, String asesor, String desarrollo, String formulario, Long numeroVenta, List<String> lotes) {}

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final long[] esperasMs;

    @Autowired
    public AvisoRegistroVentaCorreo(
            ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.username:}") String remitente) {
        this(mailSender, remitente, ESPERAS_MS);
    }

    /** Para las pruebas: sin esperar entre intentos. */
    AvisoRegistroVentaCorreo(ObjectProvider<JavaMailSender> mailSender, String remitente, long[] esperasMs) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.esperasMs = esperasMs;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRecibirRegistro(RegistroDeVentaRecibido registro) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente.isEmpty()) {
            log.info("Correo sin configurar (MAIL_USERNAME / MAIL_PASSWORD): no se avisó a finanzas de la venta #{}",
                    registro.numeroVenta());
            return;
        }
        for (int intento = 1; intento <= esperasMs.length; intento++) {
            try {
                if (esperasMs[intento - 1] > 0) Thread.sleep(esperasMs[intento - 1]);
                sender.send(mensaje(sender, registro));
                log.info("Aviso de la venta #{} enviado a {}", registro.numeroVenta(), DESTINATARIO);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Se interrumpió el aviso de la venta #{}", registro.numeroVenta());
                return;
            } catch (Exception e) {
                log.warn("No se pudo enviar el aviso de la venta #{} (intento {} de {}): {}",
                        registro.numeroVenta(), intento, esperasMs.length, e.toString());
            }
        }
        log.error("No se envió el aviso de la venta #{} a {}", registro.numeroVenta(), DESTINATARIO);
    }

    private MimeMessage mensaje(JavaMailSender sender, RegistroDeVentaRecibido registro)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mensaje = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        helper.setFrom(remitente, NOMBRE_REMITENTE);
        helper.setTo(DESTINATARIO);
        helper.setSubject(ASUNTO);
        helper.setText(texto(registro), html(registro));
        return mensaje;
    }

    /** Los datos del aviso en orden: [etiqueta, valor]. */
    private static List<String[]> datos(RegistroDeVentaRecibido r) {
        return List.of(
                new String[] {"Cliente", r.cliente()},
                new String[] {"Asesor", r.asesor()},
                new String[] {"Desarrollo", r.desarrollo()},
                new String[] {r.lotes().size() == 1 ? "Lote" : "Lotes", String.join("\n", r.lotes())},
                new String[] {"Venta", "#" + r.numeroVenta()},
                new String[] {"Formulario", r.formulario()});
    }

    /** Versión en texto plano, para los lectores de correo que no muestran HTML. */
    static String texto(RegistroDeVentaRecibido r) {
        StringBuilder sb = new StringBuilder(ASUNTO).append("\n\nLlegó un registro de venta desde We Forms.\n\n");
        for (String[] dato : datos(r)) {
            sb.append(dato[0]).append(": ").append(dato[1].replace("\n", "; ")).append('\n');
        }
        return sb.toString();
    }

    /** Lo que escribió el asesor (nombre del cliente, etc.) se escapa: el correo no debe interpretar su HTML. */
    static String html(RegistroDeVentaRecibido r) {
        StringBuilder filas = new StringBuilder();
        for (String[] dato : datos(r)) {
            filas.append("<tr><td style=\"padding:6px 16px 6px 0;color:#5b6475;vertical-align:top;white-space:nowrap\">")
                    .append(dato[0])
                    .append("</td><td style=\"padding:6px 0;font-weight:bold;color:#0b132b\">")
                    .append(HtmlUtils.htmlEscape(dato[1]).replace("\n", "<br>"))
                    .append("</td></tr>");
        }
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0b132b\">"
                + "<h1 style=\"margin:0 0 6px;font-size:22px;color:#243fb8\">" + ASUNTO + "</h1>"
                + "<p style=\"margin:0 0 18px;color:#5b6475\">Llegó un registro de venta desde We Forms.</p>"
                + "<table style=\"border-collapse:collapse;font-size:15px\">" + filas + "</table>"
                + "</div>";
    }
}
