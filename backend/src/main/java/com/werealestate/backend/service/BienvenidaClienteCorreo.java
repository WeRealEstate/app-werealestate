package com.werealestate.backend.service;

import java.util.List;
import org.springframework.web.util.HtmlUtils;

/**
 * Correo de bienvenida de SAMAI Campestre que se le manda al cliente titular de una venta cuyo contrato ya está
 * firmado. Solo arma el contenido (HTML con estilos inline y tablas para que Gmail y Outlook lo respeten, y texto
 * plano); el envío lo hace {@link BienvenidaClienteService}. Es la recreación fiel de la plantilla de Mailchimp del
 * usuario: mismos colores, tamaños, interlineados y enlaces de redes. El único dato variable es el nombre del titular,
 * que se escapa en el HTML.
 */
final class BienvenidaClienteCorreo {

    /** Desarrollo cuya plantilla existe (solo se manda si todos los lotes de la venta son de él). */
    static final String SAMAI = "SAMAI Campestre";
    /** El emoji va como escape unicode para no depender de la codificación del archivo fuente. */
    static final String ASUNTO = "Bienvenidos a We Real Estate - Samai Campestre 🌴";
    static final String VISTA_PREVIA = "Gracias por confiar en We Real Estate y formar parte de SAMAI Campestre.";

    /** Una imagen incrustada: su Content-ID (cid:...), el archivo en resources y su tipo. */
    record Imagen(String cid, String recurso, String tipo) {
    }

    static final String CID_FOTO = "bienvenida-foto";
    static final String CID_LOGO = "bienvenida-logo";

    /** Las 6 imágenes que viajan dentro del correo (foto, logo y los 4 íconos de redes). */
    static final List<Imagen> IMAGENES = List.of(
            new Imagen(CID_FOTO, "correo/bienvenida-samai-foto.jpg", "image/jpeg"),
            new Imagen(CID_LOGO, "correo/bienvenida-logo.png", "image/png"),
            new Imagen("bienvenida-facebook", "correo/bienvenida-facebook.png", "image/png"),
            new Imagen("bienvenida-instagram", "correo/bienvenida-instagram.png", "image/png"),
            new Imagen("bienvenida-tiktok", "correo/bienvenida-tiktok.png", "image/png"),
            new Imagen("bienvenida-youtube", "correo/bienvenida-youtube.png", "image/png"));

    /** Lo que lleva el correo: el nombre completo del titular. */
    record Contenido(String cliente) {
    }

    /** Redes: [cid, nombre, enlace]. Los enlaces son los de la plantilla original. */
    private static final String[][] REDES = {
        {"bienvenida-facebook", "Facebook", "https://www.facebook.com/share/1Jak98kFTh/"},
        {"bienvenida-instagram", "Instagram", "https://www.instagram.com/we_real_estate?igsh=ZjNoMXRmeWt0NHFn"},
        {"bienvenida-tiktok", "TikTok", "https://www.tiktok.com/@we.realestate?_r=1&_t=ZS-98OD4lmvTFZ"},
        {"bienvenida-youtube", "YouTube", "https://youtube.com/@werealestatemx?si=itL90TcjCqXcgrnK"},
    };

    private static final String AZUL_FONDO = "#224294";
    private static final String AZUL_TEXTO = "#243FB8";
    private static final String FUENTE = "font-family:'Open Sans','Helvetica Neue',Helvetica,Arial,sans-serif;";

    private BienvenidaClienteCorreo() {
    }

    // Párrafos del cuerpo. Las partes entre ** ** van en negritas (en el texto plano se quitan los asteriscos).
    private static final String[] PARRAFOS = {
        "Hay decisiones que cambian el presente y otras que transforman el futuro. Hoy has tomado una de las más importantes.",
        "Gracias por confiar en **We Real Estate** y por formar parte de **SAMAI CAMPESTRE**, un desarrollo pensado para quienes entienden que las mejores oportunidades no se esperan, se toman.",
        "Tu decisión representa mucho más que la adquisición de un terreno. Es una inversión estratégica, una visión de largo plazo y la construcción de un patrimonio que crecerá contigo. En un mundo donde todo cambia, invertir en bienes raíces sigue siendo una de las decisiones más inteligentes para proteger e incrementar el valor de tu capital.",
        "Nos llena de orgullo acompañarte en este momento. Más que cerrar una operación, hoy comienza una relación basada en la confianza, la cercanía y un objetivo en común: ayudarte a seguir construyendo un futuro con más valor.",
        "A partir de ahora, nuestro compromiso es mantenerte informado sobre los avances de **SAMAI CAMPESTRE**, compartir contigo noticias relevantes del proyecto y acompañarte durante cada etapa de este proceso. Queremos que siempre tengas la certeza de que cuentas con un equipo dispuesto a atender tus dudas y brindarte el respaldo que necesites.",
        "En **We Real Estate** creemos que cada inversión abre la puerta a la siguiente. Por eso queremos seguir siendo tu aliado para identificar nuevas oportunidades que impulsen tu patrimonio y te permitan alcanzar metas aún más grandes.",
        "Y si en el camino alguien cercano a ti comparte esa misma visión de crecimiento, un amigo, un familiar o un colega, será un privilegio recibirlo por recomendación tuya. No existe mejor respaldo que la confianza de quienes ya han vivido la experiencia de invertir con nosotros.",
        "Gracias por elegirnos para ser parte de este nuevo capítulo.",
        "Bienvenido a una comunidad de personas que invierten con visión, construyen con propósito y entienden que el verdadero patrimonio se crea tomando decisiones extraordinarias.",
        "Con aprecio,",
        "**Equipo We Real Estate.**",
    };

    private static String e(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor);
    }

    /** Escapa y convierte **negritas** a <strong>. */
    private static String conNegritas(String texto) {
        String[] partes = texto.split("\\*\\*", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i % 2 == 1) sb.append("<strong>").append(e(partes[i])).append("</strong>");
            else sb.append(e(partes[i]));
        }
        return sb.toString();
    }

    static String texto(Contenido c) {
        StringBuilder sb = new StringBuilder("Bienvenido We Inversionista.\n\n");
        sb.append("Estimado(a) ").append(c.cliente()).append(":\n\n");
        for (String p : PARRAFOS) sb.append(p.replace("**", "")).append("\n\n");
        sb.append("\"Invertir para crecer\"\n\n");
        for (String[] r : REDES) sb.append(r[1]).append(": ").append(r[2]).append('\n');
        sb.append("\nCopyright © 2026\nWe real Estate, Todos los derechos reservados.\n");
        return sb.toString();
    }

    static String html(Contenido c) {
        String parrafo = "margin:10px 0;padding:0;color:" + AZUL_TEXTO + ";" + FUENTE
                + "font-size:13px;line-height:200%;text-align:left;";
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(e(ASUNTO)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;width:100%;background-color:#FAFAFA;\">");
        // Vista previa en la bandeja (oculta al abrir).
        sb.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:#FAFAFA;\">")
                .append(e(VISTA_PREVIA)).append("</div>");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"border-collapse:collapse;background-color:#FAFAFA;\"><tr><td align=\"center\" valign=\"top\">");

        // Cabecera azul con la foto (564 px de ancho dentro del contenedor de 600).
        sb.append(franja(AZUL_FONDO, "9px 0 0"))
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 0;\">")
                .append("<img src=\"cid:").append(CID_FOTO).append("\" width=\"564\" alt=\"SAMAI Campestre\" ")
                .append("style=\"display:block;width:100%;max-width:564px;height:auto;border:0;outline:none;text-decoration:none;\">")
                .append("</td></tr>").append(cierreFranja());

        // Cuerpo blanco: logo y texto.
        sb.append(franja("#FFFFFF", "9px 0"))
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 9px;\">")
                .append("<img src=\"cid:").append(CID_LOGO).append("\" width=\"118\" height=\"118\" alt=\"We Real Estate\" ")
                .append("style=\"display:inline-block;width:118px;height:118px;border:0;outline:none;text-decoration:none;\">")
                .append("</td></tr>")
                .append("<tr><td valign=\"top\" style=\"padding:9px 18px 9px;color:").append(AZUL_TEXTO).append(";").append(FUENTE)
                .append("font-size:13px;line-height:200%;text-align:left;\">")
                .append("<span style=\"font-size:28px\"><strong>Bienvenido We Inversionista.</strong></span>")
                .append("<p style=\"").append(parrafo).append("\">Estimado(a) <strong>").append(e(c.cliente())).append(":</strong></p>");
        for (String p : PARRAFOS) sb.append("<p style=\"").append(parrafo).append("\">").append(conNegritas(p)).append("</p>");
        sb.append("</td></tr>").append(cierreFranja());

        // Pie azul: lema, redes, línea divisoria y derechos.
        String helv = "font-family:Helvetica,Arial,sans-serif;color:#FFFFFF;text-align:center;";
        sb.append(franja(AZUL_FONDO, "12px 0"))
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 9px;").append(helv)
                .append("font-size:16px;font-style:italic;line-height:150%;\">\"Invertir para crecer\"</td></tr>")
                .append("<tr><td align=\"center\" style=\"padding:23px 18px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\" style=\"border-collapse:collapse;\"><tr>");
        for (String[] r : REDES) {
            sb.append("<td align=\"center\" valign=\"middle\" style=\"padding:0 14px;\"><a href=\"").append(e(r[2]))
                    .append("\" target=\"_blank\"><img src=\"cid:").append(r[0]).append("\" width=\"24\" height=\"24\" alt=\"")
                    .append(r[1]).append("\" style=\"display:block;border:0;outline:none;text-decoration:none;\"></a></td>");
        }
        sb.append("</tr></table></td></tr>")
                .append("<tr><td style=\"padding:10px 18px 25px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" ")
                .append("cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;border-top:2px solid #EEEEEE;\"><tr><td style=\"font-size:0;line-height:0;\">&nbsp;</td></tr></table></td></tr>")
                .append("<tr><td align=\"center\" style=\"padding:9px 18px 9px;").append(helv)
                .append("font-size:12px;line-height:150%;\"><em>Copyright &copy; 2026<br>We real Estate, Todos los derechos reservados.</em></td></tr>")
                .append(cierreFranja());

        sb.append("</td></tr></table></body></html>");
        return sb.toString();
    }

    /** Abre una franja de ancho completo con su color de fondo y adentro el contenedor de 600 px. */
    private static String franja(String fondo, String relleno) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;background-color:"
                + fondo + ";\"><tr><td align=\"center\" valign=\"top\" style=\"padding:" + relleno + ";background-color:" + fondo
                + ";\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:600px;\">";
    }

    private static String cierreFranja() {
        return "</table></td></tr></table>";
    }
}
