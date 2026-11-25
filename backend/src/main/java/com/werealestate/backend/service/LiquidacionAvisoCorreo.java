package com.werealestate.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * El aviso de liquidación de una venta pagada por completo: va solo a finanzas, "¡Es hora de realizar una carta
 * finiquito!" (mismo estilo sencillo que {@link AvisoRegistroVentaCorreo}). Al cliente no se le escribe (decisión del
 * usuario, 2026-11-25). Solo arma el contenido; el envío lo hace {@link LiquidacionAvisoService}. Todo lo que viene de
 * datos se escapa en el HTML.
 */
final class LiquidacionAvisoCorreo {

    /** A quién le llega el aviso de la carta finiquito: la misma dirección fija de finanzas del aviso de registros. */
    static final String DESTINATARIO_FINANZAS = AvisoRegistroVentaCorreo.DESTINATARIO;
    private static final String FINANZAS_TITULO = "¡Es hora de realizar una carta finiquito!";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    private static String etiquetaLotes(List<String> lotes) {
        return lotes.size() == 1 ? "Lote" : "Lotes";
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
