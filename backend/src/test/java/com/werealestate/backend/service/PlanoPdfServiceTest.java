package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.werealestate.backend.controller.PlanoPdfController;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

/** PDF vectorial del plano: la página original se importa como vector, los polígonos caen exactamente sobre ella
 * (también con CropBox desplazada y con /Rotate) y el acceso sigue las mismas reglas que el resto de planos. */
class PlanoPdfServiceTest {

    @TempDir
    Path tempDir;

    private DesarrolloRepository desarrollos;
    private LoteRepository lotes;
    private DesarrolloService desarrolloService;
    private PlanoPdfService service;
    private Desarrollo desarrollo;

    @BeforeEach
    void preparar() {
        desarrollos = mock(DesarrolloRepository.class);
        lotes = mock(LoteRepository.class);
        desarrolloService = new DesarrolloService(
                desarrollos, mock(CurrentUserProvider.class), tempDir.toString(), tempDir.resolve("privados").toString());
        service = new PlanoPdfService(desarrollos, lotes, desarrolloService);
        desarrollo = mock(Desarrollo.class);
        when(desarrollo.getId()).thenReturn(1L);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");
        when(desarrollos.findById(1L)).thenReturn(Optional.of(desarrollo));
        when(desarrollos.findByNombre("SAMAI Campestre")).thenReturn(Optional.of(desarrollo));
    }

    /** Página con un rectángulo negro en coordenadas de usuario (100,100)-(300,250). */
    private Path crearOriginal(String nombre, PDRectangle media, PDRectangle crop, int rotacion) throws Exception {
        Path destino = tempDir.resolve(nombre);
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(media);
            if (crop != null) pagina.setCropBox(crop);
            pagina.setRotation(rotacion);
            doc.addPage(pagina);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.setNonStrokingColor(0, 0, 0);
                cs.addRect(100, 100, 200, 150);
                cs.fill();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    private static List<com.werealestate.backend.dto.PuntoMapaDto> rectangulo(double x1, double y1, double x2, double y2) {
        java.util.function.BiFunction<Double, Double, com.werealestate.backend.dto.PuntoMapaDto> p = (x, y) ->
                new com.werealestate.backend.dto.PuntoMapaDto(java.math.BigDecimal.valueOf(x), java.math.BigDecimal.valueOf(y));
        return List.of(p.apply(x1, y1), p.apply(x2, y1), p.apply(x2, y2), p.apply(x1, y2));
    }

    /** Caja (en % de la imagen) del rectángulo negro tal como lo rasteriza PDFBox al subir el plano (PDFRenderer). */
    private double[] porcentajesEnRasterDeSubida(Path original) throws Exception {
        try (PDDocument doc = Loader.loadPDF(original.toFile())) {
            BufferedImage r = new PDFRenderer(doc).renderImageWithDPI(0, 144f, ImageType.RGB);
            int[] caja = caja(r, 0, r.getHeight(), false);
            return new double[] {
                100.0 * caja[0] / r.getWidth(), 100.0 * caja[1] / r.getHeight(),
                100.0 * caja[2] / r.getWidth(), 100.0 * caja[3] / r.getHeight()
            };
        }
    }

    /** Caja [minX, minY, maxX+1, maxY+1] de los píxeles oscuros (negros) o teñidos de rojo, dentro de filas [y0, y1). */
    private static int[] caja(BufferedImage img, int y0, int y1, boolean rojo) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
                boolean coincide = rojo ? (r - g > 30 && r - b > 30) : (r < 10 && g < 10 && b < 10);
                if (!coincide) continue;
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        return new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    private void verificarAlineacion(Path original) throws Exception {
        double[] pct = porcentajesEnRasterDeSubida(original);
        float[] rojo = {255, 0, 0};
        byte[] conPoligono = PlanoPdfService.armarPdf(
                original, "T", "1/1/2026",
                List.of(new PlanoPdfService.Poligono(rectangulo(pct[0], pct[1], pct[2], pct[3]), rojo)),
                List.of(EstadoLote.DISPONIBLE));
        byte[] sinPoligono = PlanoPdfService.armarPdf(original, "T", "1/1/2026", List.of(), List.of(EstadoLote.DISPONIBLE));

        int[] conRojo, negro;
        try (PDDocument a = Loader.loadPDF(conPoligono); PDDocument b = Loader.loadPDF(sinPoligono)) {
            float dpi = (float) (1800d * 72 / a.getPage(0).getMediaBox().getWidth());
            BufferedImage ia = new PDFRenderer(a).renderImageWithDPI(0, dpi, ImageType.RGB);
            BufferedImage ib = new PDFRenderer(b).renderImageWithDPI(0, dpi, ImageType.RGB);
            // Encabezado y leyenda no tienen ni negro puro ni rojo: se busca en toda la hoja.
            conRojo = caja(ia, 0, ia.getHeight(), true);
            negro = caja(ib, 0, ib.getHeight(), false);
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(Math.abs(conRojo[i] - negro[i]) <= 1,
                    "el polígono debe caer sobre el vector (<=1 px a 1800 px de ancho): lado " + i
                            + " polígono " + conRojo[i] + " vector " + negro[i]);
        }
    }

    @Test
    void lospoligonosCaenSobreLaPaginaOriginal() throws Exception {
        verificarAlineacion(crearOriginal("plana.pdf", new PDRectangle(600, 400), null, 0));
    }

    @Test
    void conCropBoxDesplazadaSeMapeaALaCajaVisible() throws Exception {
        verificarAlineacion(crearOriginal("crop.pdf", new PDRectangle(600, 400), new PDRectangle(60, 40, 500, 330), 0));
    }

    @Test
    void conRotacionSeMapeaALaPaginaRotada() throws Exception {
        verificarAlineacion(crearOriginal("rot90.pdf", new PDRectangle(600, 400), null, 90));
        verificarAlineacion(crearOriginal("rot270.pdf", new PDRectangle(600, 400), new PDRectangle(60, 40, 500, 330), 270));
    }

    @Test
    void elPdfTrasladaLaPaginaVectorialLeyendaYEncabezado() throws Exception {
        Path original = crearOriginal("a.pdf", new PDRectangle(600, 400), null, 0);
        byte[] pdf = PlanoPdfService.armarPdf(original, "SAMAI Campestre", "17/11/2026", List.of(),
                List.of(EstadoLote.DISPONIBLE, EstadoLote.VENDIDO));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            PDRectangle caja = doc.getPage(0).getMediaBox();
            assertEquals(420 * 72 / 25.4, caja.getWidth(), 0.01);
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("Plano interactivo · SAMAI Campestre"), texto);
            assertTrue(texto.contains("Disponible") && texto.contains("Vendido"), texto);
            assertTrue(texto.contains("Generado el 17/11/2026"), texto);
            // La página original va como Form XObject (vector), no como imagen raster.
            var recursos = doc.getPage(0).getResources();
            int formas = 0, imagenes = 0;
            for (var nombre : recursos.getXObjectNames()) {
                var x = recursos.getXObject(nombre);
                if (x instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject) formas++;
                else imagenes++;
            }
            assertEquals(1, formas);
            assertEquals(0, imagenes);
        }
    }

    @Test
    void sinPdfOriginalResponde404ConMensajeClaro() {
        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class, () -> service.armarInterno(1L));
        assertTrue(e.getMessage().contains("Vuelve a subir el plano en PDF"));
        assertThrows(ResourceNotFoundException.class, () -> service.armarPublico("samai"));
    }

    @Test
    void armaElPdfDeUnDesarrolloConSusLotesYElNombreDeDescarga() throws Exception {
        Path original = desarrolloService.rutaOriginal(1L);
        Files.createDirectories(original.getParent());
        Files.copy(crearOriginal("b.pdf", new PDRectangle(600, 400), null, 0), original);
        Lote lote = mock(Lote.class);
        when(lote.getEstado()).thenReturn(EstadoLote.RESERVADO_WE);
        when(lote.getMapaPoligonoJson()).thenReturn("[{\"x\":10,\"y\":10},{\"x\":30,\"y\":10},{\"x\":30,\"y\":40}]");
        when(lotes.findByDesarrolloId(1L)).thenReturn(List.of(lote));

        PlanoPdfService.Archivo archivo = service.armarPublico("samai");
        assertEquals("Plano-SAMAI-Campestre.pdf", archivo.nombre());
        try (PDDocument doc = Loader.loadPDF(archivo.bytes())) {
            // Público: RESERVADO_WE sale como VENDIDO, y la leyenda no lista "Reservado por We".
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("Vendido"));
            assertTrue(!texto.contains("Reservado por We"));
        }
    }

    @Test
    void elControladorPublicoExigeElPinAntesDeArmarNada() {
        PlanoPdfService servicio = mock(PlanoPdfService.class);
        LimitadorPinPublico limitador = mock(LimitadorPinPublico.class);
        when(limitador.exigir(any(), anyString(), anyString())).thenThrow(new ResourceNotFoundException("PIN incorrecto"));
        PlanoPdfController controlador = new PlanoPdfController(servicio, limitador);

        assertThrows(ResourceNotFoundException.class,
                () -> controlador.publico("samai", "0000", new MockHttpServletRequest()));
        verify(servicio, never()).armarPublico(anyString());
    }

    @Test
    void elControladorEntregaUnPdfConNombre() {
        PlanoPdfService servicio = mock(PlanoPdfService.class);
        when(servicio.armarInterno(1L)).thenReturn(new PlanoPdfService.Archivo(new byte[] {1, 2}, "Plano-X.pdf"));
        var respuesta = new PlanoPdfController(servicio, mock(LimitadorPinPublico.class)).interno(1L);
        assertEquals(MediaType.APPLICATION_PDF, respuesta.getHeaders().getContentType());
        assertTrue(respuesta.getHeaders().getContentDisposition().toString().contains("Plano-X.pdf"));
    }
}
