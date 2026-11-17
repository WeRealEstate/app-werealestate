package com.werealestate.backend.service;

import com.werealestate.backend.dto.PoligonoMapaJson;
import com.werealestate.backend.dto.PuntoMapaDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.VisibilidadLote;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

/**
 * PDF de descarga del plano, armado en el servidor con el PDF ORIGINAL que se subió (vectorial: se ve nítido a cualquier
 * zoom, a diferencia del raster de ~150 MP que usa el mapa interactivo). Importa la página original como Form XObject
 * (conserva vectores e imágenes) y dibuja encima, como vectores, los polígonos de los lotes con el color de su estado,
 * más el encabezado y la leyenda. El diseño replica el de {@code core/services/pdf-cotizacion/pdf-plano.ts} del
 * frontend (hoja de 420 mm de ancho, encabezado arriba, leyenda abajo); si cambia uno, cambia el otro.
 *
 * <p>Los polígonos están guardados en porcentaje (0-100) de la imagen rasterizada al subir el plano, y esa imagen es
 * la página renderizada por PDFBox, que usa la CropBox y aplica /Rotate. Aquí se mapean a esa misma caja visible.
 */
@Service
public class PlanoPdfService {

    private static final double MM = 72d / 25.4d;
    private static final double ANCHO_HOJA = 420; // mm, A3 horizontal
    /** Los tamaños se escalan respecto de la carta horizontal (279.4 mm) con la que se diseñó (igual que en el frontend). */
    private static final double K = ANCHO_HOJA / 279.4;
    private static final double MARGEN_TEXTO = 4 * K;
    private static final double ALTO_ENCABEZADO = 10 * K;
    private static final double ALTO_LEYENDA = 8.5 * K;
    private static final double ALTO_MAXIMO = 594; // lado largo de una A2
    private static final double GROSOR_CONTORNO_MM = 0.1;
    private static final float OPACIDAD_RELLENO = 0.25f;

    private static final float[] AZUL_WE = {36, 63, 184};
    private static final float[] TEXTO = {26, 26, 26};
    private static final float[] GRIS = {115, 115, 115};
    private static final float[] BORDE = {200, 200, 200};

    /** DEBEN COINCIDIR con ESTADO_LOTE_COLOR_RGB y ESTADO_LOTE_LABELS de frontend/src/app/core/models/lote.model.ts
     * (los colores y nombres con que el mapa y el PDF de jsPDF pintan cada estado). */
    static final Map<EstadoLote, float[]> COLOR_POR_ESTADO = Map.of(
            EstadoLote.DISPONIBLE, new float[] {34, 197, 94},
            EstadoLote.APARTADO, new float[] {245, 158, 11},
            EstadoLote.APARTADO_A_PLAZO, new float[] {168, 85, 247},
            EstadoLote.APARTADO_CON_DINERO, new float[] {156, 163, 175},
            EstadoLote.EN_PROCESO_DE_FIRMA, new float[] {36, 63, 184},
            EstadoLote.RESERVADO_WE, new float[] {13, 148, 136},
            EstadoLote.VENDIDO, new float[] {239, 68, 68});
    static final Map<EstadoLote, String> ETIQUETA_POR_ESTADO = Map.of(
            EstadoLote.DISPONIBLE, "Disponible",
            EstadoLote.APARTADO, "Apartado",
            EstadoLote.APARTADO_A_PLAZO, "Apartado a plazo",
            EstadoLote.APARTADO_CON_DINERO, "Apartado con dinero",
            EstadoLote.EN_PROCESO_DE_FIRMA, "En proceso de firma",
            EstadoLote.RESERVADO_WE, "Reservado por We",
            EstadoLote.VENDIDO, "Vendido");
    /** Orden de la leyenda (ESTADOS_LOTE_ORDEN / ESTADOS_LOTE_ORDEN_ADMIN del frontend). */
    private static final List<EstadoLote> ORDEN = List.of(
            EstadoLote.DISPONIBLE,
            EstadoLote.APARTADO,
            EstadoLote.APARTADO_A_PLAZO,
            EstadoLote.APARTADO_CON_DINERO,
            EstadoLote.EN_PROCESO_DE_FIRMA,
            EstadoLote.VENDIDO);
    private static final List<EstadoLote> ORDEN_ADMIN = List.of(
            EstadoLote.DISPONIBLE,
            EstadoLote.APARTADO,
            EstadoLote.APARTADO_A_PLAZO,
            EstadoLote.APARTADO_CON_DINERO,
            EstadoLote.EN_PROCESO_DE_FIRMA,
            EstadoLote.RESERVADO_WE,
            EstadoLote.VENDIDO);

    private final DesarrolloRepository desarrolloRepository;
    private final LoteRepository loteRepository;
    private final DesarrolloService desarrolloService;

    public PlanoPdfService(
            DesarrolloRepository desarrolloRepository,
            LoteRepository loteRepository,
            DesarrolloService desarrolloService) {
        this.desarrolloRepository = desarrolloRepository;
        this.loteRepository = loteRepository;
        this.desarrolloService = desarrolloService;
    }

    /** Resultado: el PDF y el nombre sugerido ("Plano-SAMAI-Campestre.pdf"). */
    public record Archivo(byte[] bytes, String nombre) {}

    /** Plano interno: los estados tal como los ve el rol de quien pide (RESERVADO_WE solo admin y administración). */
    public Archivo armarInterno(Long desarrolloId) {
        Desarrollo desarrollo = desarrolloRepository
                .findById(desarrolloId)
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        return armar(desarrollo, VisibilidadLote::visible, VisibilidadLote.veReservadosWe() ? ORDEN_ADMIN : ORDEN);
    }

    /** Plano público (con PIN de asesor): los estados como los ve el público, sin RESERVADO_WE. */
    public Archivo armarPublico(String proyecto) {
        Desarrollo desarrollo = desarrolloRepository
                .findByNombre(LoteConsultaService.nombreDesarrolloDeProyecto(proyecto))
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        return armar(desarrollo, VisibilidadLote::comoPublico, ORDEN);
    }

    private Archivo armar(Desarrollo desarrollo, UnaryOperator<EstadoLote> visible, List<EstadoLote> leyenda) {
        Path original = desarrolloService.rutaOriginalSiExiste(desarrollo.getId());
        if (original == null) {
            throw new ResourceNotFoundException(
                    "Este plano no tiene el PDF original guardado. Vuelve a subir el plano en PDF para poder descargarlo.");
        }
        List<Poligono> poligonos = new ArrayList<>();
        for (Lote lote : loteRepository.findByDesarrolloId(desarrollo.getId())) {
            List<PuntoMapaDto> puntos = PoligonoMapaJson.deserializar(lote.getMapaPoligonoJson());
            if (puntos == null || puntos.size() < 3) continue;
            poligonos.add(new Poligono(puntos, COLOR_POR_ESTADO.get(visible.apply(lote.getEstado()))));
        }
        String fecha = LocalDate.now(ZoneId.of("America/Mexico_City")).format(java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"));
        byte[] bytes = armarPdf(original, desarrollo.getNombre(), fecha, poligonos, leyenda);
        return new Archivo(bytes, "Plano-" + desarrollo.getNombre().trim().replaceAll("\\s+", "-") + ".pdf");
    }

    record Poligono(List<PuntoMapaDto> puntos, float[] color) {}

    static byte[] armarPdf(Path original, String nombre, String fecha, List<Poligono> poligonos, List<EstadoLote> leyenda) {
        PlanoPdfService.Semaforo.adquirir();
        try (PDDocument fuente = Loader.loadPDF(original.toFile());
                PDDocument doc = new PDDocument()) {
            if (fuente.getNumberOfPages() == 0) {
                throw new ResourceNotFoundException("El PDF original del plano no tiene páginas");
            }
            PDFormXObject forma = new LayerUtility(doc).importPageAsForm(fuente, 0);
            // La forma ya trae su BBox (la CropBox de la página) y su matriz (rotación): se mide su caja visible.
            AffineTransform m = forma.getMatrix().createAffineTransform();
            java.awt.geom.Rectangle2D caja = m.createTransformedShape(
                            new java.awt.geom.Rectangle2D.Float(
                                    forma.getBBox().getLowerLeftX(),
                                    forma.getBBox().getLowerLeftY(),
                                    forma.getBBox().getWidth(),
                                    forma.getBBox().getHeight()))
                    .getBounds2D();

            // Hoja a la medida (igual que pdf-plano.ts): 420 mm de ancho; el alto sale de la proporción, con tope de A2.
            double aspecto = caja.getWidth() / caja.getHeight();
            double dibujoAncho = ANCHO_HOJA;
            double dibujoAlto = dibujoAncho / aspecto;
            double altoHoja = ALTO_ENCABEZADO + dibujoAlto + ALTO_LEYENDA;
            if (altoHoja > ALTO_MAXIMO) {
                altoHoja = ALTO_MAXIMO;
                dibujoAlto = altoHoja - ALTO_ENCABEZADO - ALTO_LEYENDA;
                dibujoAncho = dibujoAlto * aspecto;
            }
            double dibujoX = (ANCHO_HOJA - dibujoAncho) / 2;
            double dibujoY = ALTO_ENCABEZADO;

            PDPage pagina = new PDPage(new PDRectangle((float) (ANCHO_HOJA * MM), (float) (altoHoja * MM)));
            doc.addPage(pagina);
            PDFont regular = cargarFuente(doc, "Montserrat-Regular.ttf");
            PDFont negrita = cargarFuente(doc, "Montserrat-Bold.ttf");
            double alturaPt = altoHoja * MM;

            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                // ---- Encabezado ----
                color(cs, TEXTO, true);
                texto(cs, negrita, (float) (11 * K), MARGEN_TEXTO * MM, alturaPt - (ALTO_ENCABEZADO / 2 + 1.5 * K) * MM,
                        "Plano interactivo · " + nombre, false);
                color(cs, GRIS, true);
                texto(cs, regular, (float) (7 * K), (ANCHO_HOJA - MARGEN_TEXTO) * MM,
                        alturaPt - (ALTO_ENCABEZADO / 2 + 1.5 * K) * MM, "Generado el " + fecha, true);
                linea(cs, 0, alturaPt - (ALTO_ENCABEZADO - 0.15) * MM, ANCHO_HOJA * MM,
                        alturaPt - (ALTO_ENCABEZADO - 0.15) * MM, 0.5 * MM, AZUL_WE);

                // ---- Página original (vectorial), de borde a borde ----
                double escala = dibujoAncho * MM / caja.getWidth();
                cs.saveGraphicsState();
                // Lleva la esquina inferior izquierda de la caja visible a la esquina inferior izquierda del área de dibujo.
                cs.transform(new Matrix(
                        (float) escala, 0, 0, (float) escala,
                        (float) (dibujoX * MM - caja.getMinX() * escala),
                        (float) (alturaPt - (dibujoY + dibujoAlto) * MM - caja.getMinY() * escala)));
                cs.drawForm(forma);
                cs.restoreGraphicsState();

                // ---- Polígonos de los lotes (vectores; los puntos son % de la imagen = de la caja visible) ----
                PDExtendedGraphicsState ligero = new PDExtendedGraphicsState();
                ligero.setNonStrokingAlphaConstant(OPACIDAD_RELLENO);
                ligero.setStrokingAlphaConstant(1f);
                PDExtendedGraphicsState opaco = new PDExtendedGraphicsState();
                opaco.setNonStrokingAlphaConstant(1f);
                opaco.setStrokingAlphaConstant(1f);
                cs.setLineWidth((float) (GROSOR_CONTORNO_MM * MM));
                cs.setLineJoinStyle(1);
                for (Poligono p : poligonos) {
                    cs.setGraphicsStateParameters(ligero);
                    color(cs, p.color(), true);
                    trazar(cs, p, dibujoX, dibujoY, dibujoAncho, dibujoAlto, alturaPt);
                    cs.fill();
                    cs.setGraphicsStateParameters(opaco);
                    color(cs, p.color(), false);
                    trazar(cs, p, dibujoX, dibujoY, dibujoAncho, dibujoAlto, alturaPt);
                    cs.stroke();
                }
                cs.setGraphicsStateParameters(opaco);

                // ---- Leyenda ----
                double yLinea = dibujoY + dibujoAlto + 0.15;
                linea(cs, 0, alturaPt - yLinea * MM, ANCHO_HOJA * MM, alturaPt - yLinea * MM, 0.15 * MM, BORDE);
                double leyendaY = dibujoY + dibujoAlto + ALTO_LEYENDA / 2 + 0.8 * K;
                double leyendaX = MARGEN_TEXTO;
                double radio = 1.5 * K;
                float tamano = (float) (7 * K);
                for (EstadoLote estado : leyenda) {
                    cs.setNonStrokingColor(rgb(COLOR_POR_ESTADO.get(estado)[0]), rgb(COLOR_POR_ESTADO.get(estado)[1]),
                            rgb(COLOR_POR_ESTADO.get(estado)[2]));
                    circulo(cs, (leyendaX + radio) * MM, alturaPt - (leyendaY - 0.9 * K) * MM, radio * MM);
                    color(cs, TEXTO, true);
                    String etiqueta = ETIQUETA_POR_ESTADO.get(estado);
                    texto(cs, regular, tamano, (leyendaX + radio * 2 + 1.6 * K) * MM, alturaPt - leyendaY * MM, etiqueta, false);
                    leyendaX += radio * 2 + 1.6 * K + ancho(regular, tamano, etiqueta) / MM + 8 * K;
                }
                color(cs, GRIS, true);
                texto(cs, regular, (float) (6 * K), (ANCHO_HOJA - MARGEN_TEXTO) * MM, alturaPt - leyendaY * MM,
                        "WE Real Estate · Plano informativo, sujeto a cambios", true);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream(8 * 1024 * 1024);
            doc.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo armar el PDF del plano", e);
        } finally {
            PlanoPdfService.Semaforo.liberar();
        }
    }

    /** Controla cuántos PDF se arman a la vez (cada uno carga el original completo en memoria). */
    private static final class Semaforo {
        private static final Semaphore S = new Semaphore(2);

        static void adquirir() {
            S.acquireUninterruptibly();
        }

        static void liberar() {
            S.release();
        }
    }

    private static void trazar(PDPageContentStream cs, Poligono p, double x, double y, double ancho, double alto, double alturaPt)
            throws IOException {
        boolean primero = true;
        for (PuntoMapaDto pt : p.puntos()) {
            float px = (float) ((x + pt.x().doubleValue() / 100d * ancho) * MM);
            float py = (float) (alturaPt - (y + pt.y().doubleValue() / 100d * alto) * MM);
            if (primero) cs.moveTo(px, py);
            else cs.lineTo(px, py);
            primero = false;
        }
        cs.closePath();
    }

    private static void circulo(PDPageContentStream cs, double cx, double cy, double r) throws IOException {
        final double k = 0.5522847498 * r;
        cs.moveTo((float) (cx + r), (float) cy);
        cs.curveTo((float) (cx + r), (float) (cy + k), (float) (cx + k), (float) (cy + r), (float) cx, (float) (cy + r));
        cs.curveTo((float) (cx - k), (float) (cy + r), (float) (cx - r), (float) (cy + k), (float) (cx - r), (float) cy);
        cs.curveTo((float) (cx - r), (float) (cy - k), (float) (cx - k), (float) (cy - r), (float) cx, (float) (cy - r));
        cs.curveTo((float) (cx + k), (float) (cy - r), (float) (cx + r), (float) (cy - k), (float) (cx + r), (float) cy);
        cs.closePath();
        cs.fill();
    }

    private static void linea(PDPageContentStream cs, double x1, double y1, double x2, double y2, double grosor, float[] c)
            throws IOException {
        color(cs, c, false);
        cs.setLineWidth((float) grosor);
        cs.moveTo((float) x1, (float) y1);
        cs.lineTo((float) x2, (float) y2);
        cs.stroke();
    }

    private static void texto(PDPageContentStream cs, PDFont fuente, float tamano, double x, double y, String t, boolean derecha)
            throws IOException {
        cs.beginText();
        cs.setFont(fuente, tamano);
        cs.newLineAtOffset((float) (derecha ? x - ancho(fuente, tamano, t) : x), (float) y);
        cs.showText(t);
        cs.endText();
    }

    private static double ancho(PDFont fuente, float tamano, String t) {
        try {
            return fuente.getStringWidth(t) / 1000d * tamano;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void color(PDPageContentStream cs, float[] c, boolean relleno) throws IOException {
        if (relleno) cs.setNonStrokingColor(rgb(c[0]), rgb(c[1]), rgb(c[2]));
        else cs.setStrokingColor(rgb(c[0]), rgb(c[1]), rgb(c[2]));
    }

    private static float rgb(float v) {
        return v / 255f;
    }

    private static PDFont cargarFuente(PDDocument doc, String archivo) throws IOException {
        try (InputStream in = PlanoPdfService.class.getResourceAsStream("/fonts/" + archivo)) {
            if (in == null) throw new IOException("Falta la fuente " + archivo);
            return PDType0Font.load(doc, in);
        }
    }
}
