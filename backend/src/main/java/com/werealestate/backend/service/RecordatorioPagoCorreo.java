package com.werealestate.backend.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Texto del correo "Recordatorio de pago" que se le manda al cliente titular de una venta, en tono formal (usted): el
 * próximo pago (concepto, monto y fecha límite), lo atrasado si lo hay y los datos de la compra. Sin datos bancarios.
 * Solo arma el contenido (HTML con estilos inline y texto plano); el envío lo hace {@link RecordatorioPagoService}.
 * Todo lo que viene de datos se escapa en el HTML.
 *
 * <p>Plantilla única para todos los desarrollos (diseño del usuario "WE Recordatorio de Pago", 2026-11-18): encabezado
 * con el logo, banda azul con el saludo y un círculo decorativo, tarjeta con número de cliente, mensualidad, monto y
 * fecha límite, recuadro de pagos atrasados (solo si debe), una línea discreta con los datos de la compra y pie gris.
 * Cambios pedidos sobre el diseño: "usted" en vez de "tú", sin recargo por atraso (la app no maneja recargos), los
 * números de la tarjeta en azul fuerte para que se lean sobre el gris, y "Tiene pagos pendientes." cuando ya no hay un
 * próximo pago pero sí atraso. Hecha con tablas y estilos inline (Gmail y Outlook no respetan flex, grid ni posiciones
 * absolutas): el círculo es una imagen de fondo y la tarjeta "monta" la banda con un degradado que, donde no se
 * soporta, simplemente queda debajo de ella.
 */
final class RecordatorioPagoCorreo {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "MX"));

    // Colores del diseño.
    private static final String AZUL = "#243FB8";
    private static final String AZUL_CLARO = "#81B3F0";
    private static final String GRIS = "#D6D6D6";
    private static final String FUENTE = "font-family:Montserrat,'Helvetica Neue',Helvetica,Arial,sans-serif;";

    private RecordatorioPagoCorreo() {
    }

    /** arrastre: la parte del monto que viene de pagos anteriores sin cubrir (la tabla pasa el faltante de un mes
     * vencido a la mensualidad siguiente); 0 si el monto es solo el del mes. */
    record ProximoPago(LocalDate fecha, BigDecimal monto, String concepto, BigDecimal arrastre) {

        boolean incluyeArrastre() {
            return arrastre != null && arrastre.compareTo(new BigDecimal("0.01")) > 0;
        }
    }

    /** Lo que lleva el correo. numeroVenta es la etiqueta ("S-12"), nunca el número global; numeroCliente, el número del
     * titular en el desarrollo de la venta ("S-40"), null si aún no tiene. */
    record Contenido(
            String cliente,
            String numeroCliente,
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

    /** Una imagen incrustada en el correo (cid:...): su Content-ID, el archivo en resources y su tipo. */
    record Imagen(String cid, String archivo, String tipo) {
    }

    static final String CID_LOGO = "logo";
    static final String CID_CIRCULO = "circulo";
    static final List<Imagen> IMAGENES = List.of(
            new Imagen(CID_LOGO, "correo/recordatorio-logo.png", "image/png"),
            new Imagen(CID_CIRCULO, "correo/recordatorio-circulo.png", "image/png"));

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

    /** true si todo lo vencido ya va sumado en el monto del próximo pago. */
    private static boolean atrasoIncluido(Contenido c) {
        ProximoPago p = c.proximoPago();
        return p != null && p.incluyeArrastre()
                && p.arrastre().compareTo(c.atrasoMonto().subtract(new BigDecimal("0.01"))) >= 0;
    }

    /** El recuadro de pagos atrasados: cuántos, por cuánto y si ya van incluidos en el monto a pagar. */
    static String fraseAtraso(Contenido c) {
        String cuenta = cuentaAtraso(c.pagosAtrasados(), c.mensualidadesAtrasadas());
        boolean una = c.pagosAtrasados() == 1;
        String inicio = cuenta.isEmpty()
                ? "Su cuenta presenta un saldo vencido"
                : cuenta + (una ? " vencida" : " vencidas");
        if (!cuenta.isEmpty() && c.pagosAtrasados() != c.mensualidadesAtrasadas()) {
            inicio = cuenta + (una ? " vencido" : " vencidos");
        }
        String frase = inicio + " por " + dinero(c.atrasoMonto()) + " MXN.";
        if (atrasoIncluido(c)) return frase + " El monto a pagar ya incluye este saldo.";
        return frase + " Le solicitamos atentamente regularizarlo a la brevedad.";
    }

    /** El concepto del pago partido para la tarjeta: "Mensualidad" / "7 de 36"; otro concepto va completo. */
    private static String[] conceptoTarjeta(Contenido c) {
        ProximoPago p = c.proximoPago();
        if (p == null) {
            String cuenta = cuentaAtraso(c.pagosAtrasados(), c.mensualidadesAtrasadas());
            return new String[] {"Pagos vencidos", cuenta.isEmpty() ? "—" : cuenta};
        }
        Matcher m = Pattern.compile("^Mensualidad (\\d+ de \\d+)$").matcher(p.concepto());
        if (m.matches()) return new String[] {"Mensualidad", m.group(1)};
        return new String[] {"Concepto", p.concepto()};
    }

    private static String titulo(Contenido c) {
        ProximoPago p = c.proximoPago();
        if (p == null) return "Tiene pagos pendientes.";
        return p.concepto().startsWith("Mensualidad") ? "Es momento de su pago mensual." : "Es momento de su próximo pago.";
    }

    private static String entrada(Contenido c) {
        if (c.proximoPago() == null) {
            return "Le recordamos que su cuenta presenta pagos vencidos. Gracias por seguir invirtiendo para crecer con nosotros.";
        }
        return "Le recordamos su próximo pago. Gracias por seguir invirtiendo para crecer con nosotros.";
    }

    /** "SAMAI Campestre · Mz 1 / Lote 1 · Venta S-12". */
    private static String lineaCompra(Contenido c) {
        List<String> partes = new ArrayList<>();
        if (!c.desarrollos().isEmpty()) partes.add(String.join(", ", c.desarrollos()));
        if (!c.lotes().isEmpty()) partes.add(String.join(", ", c.lotes()));
        if (c.numeroVenta() != null) partes.add("Venta " + c.numeroVenta());
        return String.join(" · ", partes);
    }

    /** Lo que Gmail y otros muestran junto al asunto en la bandeja de entrada (texto oculto al abrir el correo). */
    private static String vistaPrevia(Contenido c) {
        ProximoPago p = c.proximoPago();
        if (p == null) return "Su cuenta presenta un saldo vencido de " + dinero(c.atrasoMonto()) + ".";
        return "Su próximo pago es el " + fecha(p.fecha()) + " por " + dinero(p.monto()) + ".";
    }

    private static BigDecimal montoAPagar(Contenido c) {
        return c.proximoPago() != null ? c.proximoPago().monto() : c.atrasoMonto();
    }

    static String texto(Contenido c) {
        StringBuilder sb = new StringBuilder("Recordatorio de pago\n\n");
        sb.append("Estimada(o) ").append(c.cliente()).append("\n\n");
        sb.append(titulo(c)).append('\n').append(entrada(c)).append("\n\n");
        if (c.numeroCliente() != null) sb.append("Número de cliente: ").append(c.numeroCliente()).append('\n');
        String[] concepto = conceptoTarjeta(c);
        sb.append(concepto[0]).append(": ").append(concepto[1]).append('\n');
        sb.append("Monto a pagar: ").append(dinero(montoAPagar(c))).append(" MXN\n");
        ProximoPago p = c.proximoPago();
        if (p != null && p.incluyeArrastre()) {
            sb.append("(incluye ").append(dinero(p.arrastre())).append(" de pagos anteriores)\n");
        }
        if (p != null) sb.append("Fecha límite: ").append(fecha(p.fecha())).append('\n');
        if (c.hayAtraso()) sb.append("\nPagos atrasados: ").append(fraseAtraso(c)).append('\n');
        sb.append("\nSi ya realizó su pago, ignore este mensaje.\n\n");
        sb.append(lineaCompra(c)).append("\n\n");
        sb.append("We Real Estate · Oaxaca de Juárez, Oaxaca\n");
        return sb.toString();
    }

    /** Escapa lo que viene de datos sin convertir los acentos en entidades (el correo va en UTF-8). */
    private static String e(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor, "UTF-8");
    }

    private static String etiqueta(String texto) {
        return "<div style=\"" + FUENTE + "font-size:12px;font-weight:600;color:" + AZUL_CLARO
                + ";text-transform:uppercase;letter-spacing:1.5px;line-height:1.4;\">" + e(texto) + "</div>";
    }

    private static String valor(String texto, int tamano, String clase) {
        return "<div" + (clase == null ? "" : " class=\"" + clase + "\"") + " style=\"" + FUENTE + "font-size:" + tamano
                + "px;font-weight:800;color:" + AZUL + ";line-height:1.25;\">" + e(texto) + "</div>";
    }

    private static final String DIVISOR = "<tr><td colspan=\"2\" style=\"padding:20px 0 0;\"><div style=\"height:1px;"
            + "line-height:1px;font-size:1px;background:#FFFFFF;\">&nbsp;</div></td></tr>";

    static String html(Contenido c) {
        ProximoPago p = c.proximoPago();
        String[] concepto = conceptoTarjeta(c);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Recordatorio de pago</title>")
                .append("<link href=\"https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;800;900&display=swap\" rel=\"stylesheet\">")
                // En celular el monto grande no cabe a 48 px.
                .append("<style>@media only screen and (max-width:520px){.monto{font-size:34px!important;letter-spacing:-0.5px!important}")
                .append(".banda{padding:32px 24px 24px!important;background-size:110px 110px!important}.lado{padding-left:16px!important;padding-right:16px!important}")
                .append(".titulo{font-size:30px!important;max-width:320px!important}}</style></head>")
                .append("<body style=\"margin:0;padding:0;width:100%;background-color:").append(GRIS).append(";\">");
        // Vista previa en la bandeja (oculta al abrir).
        sb.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:").append(GRIS).append(";\">")
                .append(e(vistaPrevia(c))).append("</div>");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"")
                .append(GRIS).append("\" style=\"border-collapse:collapse;background-color:").append(GRIS).append(";\">")
                .append("<tr><td align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#FFFFFF\" ")
                .append("style=\"border-collapse:separate;width:100%;max-width:600px;background-color:#FFFFFF;border-radius:20px;overflow:hidden;\">");

        // Encabezado: logo y "Recordatorio de pago".
        sb.append("<tr><td class=\"lado\" style=\"padding:28px 36px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" ")
                .append("cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;\"><tr>")
                .append("<td valign=\"middle\"><img src=\"cid:").append(CID_LOGO).append("\" width=\"134\" height=\"90\" alt=\"WE Real Estate\" ")
                .append("style=\"display:block;width:134px;height:90px;border:0;outline:none;text-decoration:none;\"></td>")
                .append("<td valign=\"middle\" align=\"right\" style=\"").append(FUENTE).append("font-size:12px;font-weight:600;color:")
                .append(AZUL).append(";text-align:right;line-height:1.5;text-transform:uppercase;letter-spacing:1.5px;\">")
                .append("Recordatorio<br>de pago</td></tr></table></td></tr>");

        // Banda azul con el círculo decorativo (imagen de fondo arriba a la derecha).
        sb.append("<tr><td class=\"banda\" bgcolor=\"").append(AZUL).append("\" style=\"background-color:").append(AZUL)
                .append(";background-image:url('cid:").append(CID_CIRCULO).append("');background-repeat:no-repeat;")
                .append("background-position:right top;background-size:170px 170px;padding:44px 36px 32px;\">")
                .append("<div style=\"").append(FUENTE).append("font-size:16px;font-weight:600;color:#FFFFFF;margin:0 0 12px;\">")
                .append("Estimada(o) ").append(e(c.cliente())).append("</div>")
                .append("<div class=\"titulo\" style=\"").append(FUENTE).append("font-size:38px;line-height:1.08;font-weight:800;")
                .append("color:#FFFFFF;letter-spacing:-1px;max-width:420px;\">").append(e(titulo(c))).append("</div>")
                .append("<div style=\"").append(FUENTE).append("font-size:15px;line-height:1.6;color:#FFFFFF;margin-top:16px;max-width:420px;\">")
                .append(e(entrada(c))).append("</div></td></tr>");

        // Tarjeta: "monta" 40 px sobre la banda con un degradado (donde no se soporta, queda debajo de ella).
        sb.append("<tr><td class=\"lado\" style=\"padding:0 36px;background-color:#FFFFFF;")
                .append("background-image:linear-gradient(").append(AZUL).append(" 40px,#FFFFFF 40px);\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"")
                .append(GRIS).append("\" style=\"border-collapse:separate;background-color:").append(GRIS).append(";border-radius:16px;\">")
                .append("<tr><td style=\"padding:28px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("border=\"0\" style=\"border-collapse:collapse;\">");
        sb.append("<tr><td width=\"50%\" valign=\"top\" style=\"padding-right:8px;\">")
                .append(etiqueta("Número de cliente")).append(valor(c.numeroCliente() != null ? c.numeroCliente() : "—", 20, null))
                .append("</td><td width=\"50%\" valign=\"top\" style=\"padding-left:8px;\">")
                .append(etiqueta(concepto[0])).append(valor(concepto[1], 20, null)).append("</td></tr>");
        sb.append(DIVISOR);
        sb.append("<tr><td colspan=\"2\" style=\"padding-top:20px;\">").append(etiqueta("Monto a pagar"))
                .append("<div class=\"monto\" style=\"").append(FUENTE).append("font-size:48px;font-weight:900;letter-spacing:-1.5px;")
                .append("line-height:1.1;color:").append(AZUL).append(";\">").append(e(dinero(montoAPagar(c)) + " MXN")).append("</div>");
        if (p != null && p.incluyeArrastre()) {
            sb.append("<div style=\"").append(FUENTE).append("font-size:13px;color:").append(AZUL).append(";margin-top:6px;\">")
                    .append(e("Incluye " + dinero(p.arrastre()) + " de pagos anteriores")).append("</div>");
        }
        sb.append("</td></tr>");
        if (p != null) {
            sb.append(DIVISOR);
            sb.append("<tr><td colspan=\"2\" style=\"padding-top:20px;\">").append(etiqueta("Fecha límite"))
                    .append(valor(fecha(p.fecha()), 24, null)).append("</td></tr>");
        }
        sb.append("</table></td></tr></table></td></tr>");

        // Pagos atrasados (solo si debe).
        if (c.hayAtraso()) {
            sb.append("<tr><td class=\"lado\" style=\"padding:16px 36px 0;\">")
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                    .append("style=\"border-collapse:separate;background-color:#FFFFFF;border:2px solid ").append(AZUL)
                    .append(";border-radius:16px;\"><tr><td style=\"padding:20px 24px;\">")
                    .append("<div style=\"").append(FUENTE).append("font-size:12px;font-weight:600;color:").append(AZUL)
                    .append(";text-transform:uppercase;letter-spacing:1.5px;\">Pagos atrasados</div>")
                    .append("<div style=\"").append(FUENTE).append("font-size:15px;line-height:1.6;color:").append(AZUL)
                    .append(";margin-top:6px;\">").append(e(fraseAtraso(c))).append("</div>")
                    .append("</td></tr></table></td></tr>");
        }

        // Cierre y datos de la compra.
        sb.append("<tr><td class=\"lado\" style=\"padding:32px 36px 36px;").append(FUENTE).append("font-size:15px;line-height:1.6;color:")
                .append(AZUL).append(";\">Si ya realizó su pago, ignore este mensaje.")
                .append("<div style=\"font-size:12px;line-height:1.5;color:").append(AZUL).append(";opacity:0.8;margin-top:12px;\">")
                .append(e(lineaCompra(c))).append("</div></td></tr>");

        // Pie gris.
        sb.append("<tr><td class=\"lado\" bgcolor=\"").append(GRIS).append("\" style=\"background-color:").append(GRIS)
                .append(";padding:20px 36px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"border-collapse:collapse;\"><tr>")
                .append("<td style=\"").append(FUENTE).append("font-size:13px;font-weight:600;color:").append(AZUL).append(";\">We Real Estate</td>")
                .append("<td align=\"right\" style=\"").append(FUENTE).append("font-size:13px;color:").append(AZUL)
                .append(";text-align:right;\">Oaxaca de Juárez, Oaxaca</td></tr></table></td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }
}
