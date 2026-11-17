package com.werealestate.backend.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * Texto del correo "Recordatorio de pago" que se le manda al cliente titular de una venta, en tono formal: el próximo
 * pago (fecha, monto y concepto), lo atrasado si lo hay y los datos de la compra. Sin datos bancarios. Solo arma el
 * contenido (HTML con estilos inline, como AvisoRegistroVentaCorreo, y texto plano); el envío lo hace
 * {@link RecordatorioPagoService}. Todo lo que viene de datos se escapa en el HTML.
 */
final class RecordatorioPagoCorreo {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "MX"));

    private RecordatorioPagoCorreo() {
    }

    /** arrastre: la parte del monto que viene de pagos anteriores sin cubrir (la tabla pasa el faltante de un mes
     * vencido a la mensualidad siguiente); 0 si el monto es solo el del mes. */
    record ProximoPago(LocalDate fecha, BigDecimal monto, String concepto, BigDecimal arrastre) {

        boolean incluyeArrastre() {
            return arrastre != null && arrastre.compareTo(new BigDecimal("0.01")) > 0;
        }
    }

    /** Lo que lleva el correo. numeroVenta es la etiqueta ("S-12"), nunca el número global. */
    record Contenido(
            String cliente,
            String numeroVenta,
            List<String> desarrollos,
            List<String> lotes,
            ProximoPago proximoPago,
            BigDecimal atrasoMonto,
            int pagosAtrasados,
            int mensualidadesAtrasadas) {

        boolean hayAtraso() {
            return atrasoMonto != null && atrasoMonto.signum() > 0;
        }
    }

    static String asunto(Contenido c) {
        return "Recordatorio de pago – " + String.join(", ", c.desarrollos());
    }

    static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    /** "$4,000.00" (punto decimal y coma de miles, como se ve en la app). */
    static String dinero(BigDecimal monto) {
        return "$" + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(monto);
    }

    /** "2 mensualidades" si todo lo atrasado son mensualidades; "2 pagos" si no (igual que cuentaAtraso del frontend). */
    static String cuentaAtraso(int pagos, int mensualidades) {
        if (pagos <= 0) return "";
        boolean soloMensualidades = pagos == mensualidades;
        String palabra = soloMensualidades ? (pagos == 1 ? "mensualidad" : "mensualidades") : (pagos == 1 ? "pago" : "pagos");
        return pagos + " " + palabra;
    }

    private static String frasePago(Contenido c) {
        ProximoPago p = c.proximoPago();
        return "Próximo pago: " + fecha(p.fecha()) + " · " + montoPago(p) + " · " + p.concepto();
    }

    /** El monto del próximo pago; si ya trae lo pendiente de pagos anteriores, desglosado para que el cliente no lo
     * sume dos veces con el saldo vencido (decisión del usuario, 2026-11-17). */
    private static String montoPago(ProximoPago p) {
        if (!p.incluyeArrastre()) return dinero(p.monto());
        String delMes = p.concepto().startsWith("Mensualidad") ? "mensualidad" : "pago del mes";
        return dinero(p.monto()) + " (" + delMes + " " + dinero(p.monto().subtract(p.arrastre())) + " + "
                + dinero(p.arrastre()) + " pendientes de pagos anteriores)";
    }

    /** true si todo lo vencido ya va sumado en el monto del próximo pago. */
    private static boolean atrasoIncluido(Contenido c) {
        ProximoPago p = c.proximoPago();
        return p != null && p.incluyeArrastre()
                && p.arrastre().compareTo(c.atrasoMonto().subtract(new BigDecimal("0.01"))) >= 0;
    }

    private static String fraseAtraso(Contenido c) {
        String cuenta = cuentaAtraso(c.pagosAtrasados(), c.mensualidadesAtrasadas());
        if (atrasoIncluido(c)) {
            return "Su cuenta presenta un saldo vencido de " + dinero(c.atrasoMonto())
                    + (cuenta.isEmpty() ? "" : " (" + cuenta + ")")
                    + ", que ya está incluido en el monto de su próximo pago. Le solicitamos atentamente regularizarlo a la brevedad.";
        }
        return "Actualmente su cuenta presenta un saldo vencido de " + dinero(c.atrasoMonto())
                + (cuenta.isEmpty() ? "" : " (" + cuenta + ")")
                + ". Le solicitamos atentamente regularizarlo a la brevedad.";
    }

    /** Los datos de la compra en orden: [etiqueta, valor]. */
    private static List<String[]> datos(Contenido c) {
        return List.of(
                new String[] {c.desarrollos().size() == 1 ? "Desarrollo" : "Desarrollos", String.join(", ", c.desarrollos())},
                new String[] {c.lotes().size() == 1 ? "Lote" : "Lotes", String.join("\n", c.lotes())},
                new String[] {"Venta", c.numeroVenta()});
    }

    static String texto(Contenido c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimado(a) ").append(c.cliente()).append(",\n\n");
        sb.append("Le recordamos que su próximo pago es el siguiente:\n\n");
        if (c.proximoPago() != null) {
            ProximoPago p = c.proximoPago();
            sb.append("Fecha: ").append(fecha(p.fecha())).append('\n');
            sb.append("Monto: ").append(montoPago(p)).append('\n');
            sb.append("Concepto: ").append(p.concepto()).append("\n\n");
        }
        if (c.hayAtraso()) sb.append(fraseAtraso(c)).append("\n\n");
        sb.append("Datos de su compra:\n");
        for (String[] dato : datos(c)) sb.append(dato[0]).append(": ").append(dato[1].replace("\n", "; ")).append('\n');
        sb.append("\nAgradecemos de antemano su atención.\n\nAtentamente,\nWe Real Estate\n");
        return sb.toString();
    }

    private static String e(String valor) {
        return HtmlUtils.htmlEscape(valor);
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr><td style=\"padding:6px 16px 6px 0;color:#5b6475;vertical-align:top;white-space:nowrap\">" + e(etiqueta)
                + "</td><td style=\"padding:6px 0;font-weight:bold;color:#0b132b\">"
                + e(valor).replace("\n", "<br>") + "</td></tr>";
    }

    static String html(Contenido c) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0b132b\">");
        sb.append("<h1 style=\"margin:0 0 14px;font-size:22px;color:#243fb8\">Recordatorio de pago</h1>");
        sb.append("<p style=\"margin:0 0 6px;font-size:15px\">Estimado(a) ").append(e(c.cliente())).append(",</p>");
        sb.append("<p style=\"margin:0 0 16px;font-size:15px;color:#5b6475\">Le recordamos que su próximo pago es el siguiente:</p>");
        if (c.proximoPago() != null) {
            ProximoPago p = c.proximoPago();
            sb.append("<table style=\"border-collapse:collapse;font-size:15px;margin:0 0 16px;background:#f2f5ff;border-radius:8px;padding:12px\">")
                    .append(fila("Fecha", fecha(p.fecha())))
                    .append(fila("Monto", montoPago(p)))
                    .append(fila("Concepto", p.concepto()))
                    .append("</table>");
        }
        if (c.hayAtraso()) {
            sb.append("<p style=\"margin:0 0 16px;font-size:15px;padding:12px;border-left:4px solid #c0392b;background:#fdecea\">")
                    .append(e(fraseAtraso(c))).append("</p>");
        }
        sb.append("<p style=\"margin:0 0 4px;font-size:13px;color:#5b6475\">Datos de su compra</p>");
        sb.append("<table style=\"border-collapse:collapse;font-size:15px\">");
        for (String[] dato : datos(c)) sb.append(fila(dato[0], dato[1]));
        sb.append("</table>");
        sb.append("<p style=\"margin:20px 0 4px;font-size:15px\">Agradecemos de antemano su atención.</p>");
        sb.append("<p style=\"margin:0;font-size:15px\">Atentamente,<br><strong>We Real Estate</strong></p>");
        sb.append("</div>");
        return sb.toString();
    }
}
