package com.werealestate.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * Los dos correos de liquidación de una venta de SAMAI Campestre pagada por completo: la felicitación al cliente titular
 * (mismo diseño y mismas imágenes incrustadas que la bienvenida, ver {@link BienvenidaClienteCorreo}) y el aviso a
 * finanzas "¡Es hora de realizar una carta finiquito!" (mismo estilo sencillo que {@link AvisoRegistroVentaCorreo}).
 * Solo arma el contenido; el envío lo hace {@link LiquidacionAvisoService}. Todo lo que viene de datos se escapa en el
 * HTML.
 */
final class LiquidacionAvisoCorreo {

    /** A quién le llega el aviso de la carta finiquito: la misma dirección fija de finanzas del aviso de registros. */
    static final String DESTINATARIO_FINANZAS = AvisoRegistroVentaCorreo.DESTINATARIO;
    /** El emoji va como escape unicode para no depender de la codificación del archivo fuente. */
    static final String ASUNTO_CLIENTE = "¡Felicidades! Ha liquidado su terreno en SAMAI Campestre 🌴";
    static final String VISTA_PREVIA_CLIENTE =
            "Gracias por su confianza: su terreno en SAMAI Campestre está liquidado por completo.";
    private static final String FINANZAS_TITULO = "¡Es hora de realizar una carta finiquito!";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Lo que lleva el correo al cliente. */
    record ContenidoCliente(String cliente, String numeroVenta, List<String> lotes) {
    }

    /** Una persona de la venta (titular o copropietario) con sus datos de contacto; null si no los tiene. */
    record Persona(String nombre, String telefono, String correo) {
    }

    /** Lo que lleva el aviso a finanzas. */
    record ContenidoFinanzas(
            String numeroVenta,
            String desarrollo,
            List<String> lotes,
            String numeroContrato,
            String asesor,
            Persona titular,
            List<Persona> copropietarios,
            BigDecimal precioTotal,
            BigDecimal totalPagado,
            LocalDate ultimoPago,
            String enlace) {
    }

    private LiquidacionAvisoCorreo() {
    }

    static String asuntoFinanzas(String numeroVenta) {
        return FINANZAS_TITULO + " – Venta " + numeroVenta;
    }

    private static String e(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor, "UTF-8");
    }

    private static String dinero(BigDecimal monto) {
        return String.format(Locale.US, "$%,.2f", monto == null ? BigDecimal.ZERO : monto);
    }

    private static String vacio(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    // ----------------------------------------------------------------------------------- cliente

    // Párrafos del cuerpo (entre ** ** van en negritas; en el texto plano se quitan los asteriscos).
    private static final String[] PARRAFOS_CLIENTE = {
        "Es un gusto comunicarle que ha liquidado por completo su terreno en **SAMAI CAMPESTRE**. ¡Muchas felicidades por este importante logro!",
        "Queremos agradecerle su confianza y su puntualidad en cada uno de sus pagos. Que haya elegido a **We Real Estate** para construir su patrimonio es un orgullo para todo nuestro equipo.",
        "El equipo de finanzas ya está preparando su **carta finiquito**, el documento que acredita que su terreno está pagado en su totalidad, y se pondrá en contacto con usted para entregársela.",
        "Si tiene cualquier duda, estamos a sus órdenes.",
        "Atentamente,",
        "**We Real Estate.**",
    };

    private static String conNegritas(String texto) {
        String[] partes = texto.split("\\*\\*", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i % 2 == 1) sb.append("<strong>").append(e(partes[i])).append("</strong>");
            else sb.append(e(partes[i]));
        }
        return sb.toString();
    }

    private static String etiquetaLotes(List<String> lotes) {
        return lotes.size() == 1 ? "Lote" : "Lotes";
    }

    static String textoCliente(ContenidoCliente c) {
        StringBuilder sb = new StringBuilder("¡Felicidades por liquidar su terreno!\n\n");
        sb.append("Estimado(a) ").append(c.cliente()).append(":\n\n");
        sb.append(PARRAFOS_CLIENTE[0].replace("**", "")).append("\n\n");
        sb.append("Su compra\n").append("Venta: ").append(c.numeroVenta()).append('\n')
                .append(etiquetaLotes(c.lotes())).append(": ").append(String.join("; ", c.lotes())).append("\n\n");
        for (int i = 1; i < PARRAFOS_CLIENTE.length; i++) sb.append(PARRAFOS_CLIENTE[i].replace("**", "")).append("\n\n");
        sb.append("\"Invertir para crecer\"\n\n");
        for (String[] r : BienvenidaClienteCorreo.REDES) sb.append(r[1]).append(": ").append(r[2]).append('\n');
        sb.append("\nCopyright © 2026\nWe real Estate, Todos los derechos reservados.\n");
        return sb.toString();
    }

    static String htmlCliente(ContenidoCliente c) {
        String azul = BienvenidaClienteCorreo.AZUL_TEXTO;
        String fuente = BienvenidaClienteCorreo.FUENTE;
        String parrafo = "margin:10px 0;padding:0;color:" + azul + ";" + fuente
                + "font-size:13px;line-height:200%;text-align:left;";
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(e(ASUNTO_CLIENTE)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;width:100%;background-color:#FAFAFA;\">");
        // Vista previa en la bandeja (oculta al abrir).
        sb.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:#FAFAFA;\">")
                .append(e(VISTA_PREVIA_CLIENTE)).append("</div>");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"border-collapse:collapse;background-color:#FAFAFA;\"><tr><td align=\"center\" valign=\"top\">");

        // Cabecera azul con la foto.
        sb.append(BienvenidaClienteCorreo.franja(BienvenidaClienteCorreo.AZUL_FONDO, "9px 0 0"))
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 0;\">")
                .append("<img src=\"cid:").append(BienvenidaClienteCorreo.CID_FOTO).append("\" width=\"564\" alt=\"SAMAI Campestre\" ")
                .append("style=\"display:block;width:100%;max-width:564px;height:auto;border:0;outline:none;text-decoration:none;\">")
                .append("</td></tr>").append(BienvenidaClienteCorreo.cierreFranja());

        // Cuerpo blanco: logo, felicitación, datos de la compra y cierre.
        sb.append(BienvenidaClienteCorreo.franja("#FFFFFF", "9px 0"))
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 9px;\">")
                .append("<img src=\"cid:").append(BienvenidaClienteCorreo.CID_LOGO).append("\" width=\"118\" height=\"118\" alt=\"We Real Estate\" ")
                .append("style=\"display:inline-block;width:118px;height:118px;border:0;outline:none;text-decoration:none;\">")
                .append("</td></tr>")
                .append("<tr><td valign=\"top\" style=\"padding:9px 18px 9px;color:").append(azul).append(";").append(fuente)
                .append("font-size:13px;line-height:200%;text-align:left;\">")
                .append("<span style=\"font-size:28px\"><strong>¡Felicidades!</strong></span>")
                .append("<p style=\"").append(parrafo).append("\">Estimado(a) <strong>").append(e(c.cliente())).append(":</strong></p>")
                .append("<p style=\"").append(parrafo).append("\">").append(conNegritas(PARRAFOS_CLIENTE[0])).append("</p>");
        // Recuadro con los datos de la compra.
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" ")
                .append("style=\"border-collapse:collapse;margin:8px 0;background-color:#F2F5FC;border-left:4px solid ")
                .append(BienvenidaClienteCorreo.AZUL_FONDO).append(";\"><tr><td style=\"padding:10px 16px;color:").append(azul)
                .append(";").append(fuente).append("font-size:13px;line-height:180%;\">")
                .append("<strong>Su compra</strong><br>Venta: <strong>").append(e(c.numeroVenta())).append("</strong><br>")
                .append(etiquetaLotes(c.lotes())).append(": <strong>")
                .append(c.lotes().stream().map(LiquidacionAvisoCorreo::e).reduce((a, b) -> a + "<br>" + b).orElse(""))
                .append("</strong></td></tr></table>");
        for (int i = 1; i < PARRAFOS_CLIENTE.length; i++) {
            sb.append("<p style=\"").append(parrafo).append("\">").append(conNegritas(PARRAFOS_CLIENTE[i])).append("</p>");
        }
        sb.append("</td></tr>").append(BienvenidaClienteCorreo.cierreFranja());

        sb.append(BienvenidaClienteCorreo.pie());
        sb.append("</td></tr></table></body></html>");
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------- finanzas

    private static String persona(Persona p) {
        return p.nombre() + " · Tel. " + vacio(p.telefono()) + " · " + vacio(p.correo());
    }

    /** Datos del aviso en orden: [etiqueta, valor] (los saltos de línea separan varios renglones). */
    private static List<String[]> datosFinanzas(ContenidoFinanzas c) {
        java.util.ArrayList<String[]> d = new java.util.ArrayList<>();
        d.add(new String[] {"Cliente titular", persona(c.titular())});
        if (!c.copropietarios().isEmpty()) {
            d.add(new String[] {c.copropietarios().size() == 1 ? "Copropietario" : "Copropietarios",
                String.join("\n", c.copropietarios().stream().map(LiquidacionAvisoCorreo::persona).toList())});
        }
        d.add(new String[] {"Venta", c.numeroVenta()});
        d.add(new String[] {"Desarrollo", c.desarrollo()});
        d.add(new String[] {etiquetaLotes(c.lotes()), String.join("\n", c.lotes())});
        d.add(new String[] {"Contrato", vacio(c.numeroContrato())});
        d.add(new String[] {"Asesor", vacio(c.asesor())});
        d.add(new String[] {"Precio total", dinero(c.precioTotal())});
        d.add(new String[] {"Total pagado", dinero(c.totalPagado())});
        d.add(new String[] {"Último pago", c.ultimoPago() == null ? "—" : c.ultimoPago().format(FECHA)});
        d.add(new String[] {"Ver la venta", c.enlace()});
        return d;
    }

    static String textoFinanzas(ContenidoFinanzas c) {
        StringBuilder sb = new StringBuilder(FINANZAS_TITULO)
                .append("\n\nEl cliente liquidó por completo su terreno. Corresponde preparar su carta finiquito.\n\n");
        for (String[] dato : datosFinanzas(c)) {
            sb.append(dato[0]).append(": ").append(dato[1].replace("\n", "; ")).append('\n');
        }
        return sb.toString();
    }

    static String htmlFinanzas(ContenidoFinanzas c) {
        StringBuilder filas = new StringBuilder();
        for (String[] dato : datosFinanzas(c)) {
            String valor = "Ver la venta".equals(dato[0])
                    ? "<a href=\"" + e(dato[1]) + "\" style=\"color:#243fb8\">" + e(dato[1]) + "</a>"
                    : e(dato[1]).replace("\n", "<br>");
            filas.append("<tr><td style=\"padding:6px 16px 6px 0;color:#5b6475;vertical-align:top;white-space:nowrap\">")
                    .append(e(dato[0]))
                    .append("</td><td style=\"padding:6px 0;font-weight:bold;color:#0b132b\">")
                    .append(valor).append("</td></tr>");
        }
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0b132b\">"
                + "<h1 style=\"margin:0 0 6px;font-size:22px;color:#243fb8\">" + e(FINANZAS_TITULO) + "</h1>"
                + "<p style=\"margin:0 0 18px;color:#5b6475\">El cliente liquidó por completo su terreno. Corresponde preparar su carta finiquito.</p>"
                + "<table style=\"border-collapse:collapse;font-size:15px\">" + filas + "</table>"
                + "</div>";
    }
}
