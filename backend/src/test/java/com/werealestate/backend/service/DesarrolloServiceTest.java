package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class DesarrolloServiceTest {

    @TempDir
    Path tempDir;

    private DesarrolloRepository desarrolloRepository;
    private CurrentUserProvider currentUserProvider;
    private DesarrolloService service;

    @BeforeEach
    void setUp() {
        desarrolloRepository = mock(DesarrolloRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new DesarrolloService(desarrolloRepository, currentUserProvider, tempDir.toString());

        Usuario admin = new Usuario("Admin", "admin@test.com", "hash", Role.ADMIN, null);
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(currentUserProvider.getUsuarioActual()).thenReturn(admin);
    }

    @Test
    void subirPdfGeneraImagenHdYVistaRapidaConLaMismaProporcion() throws Exception {
        Desarrollo d = mock(Desarrollo.class);
        when(desarrolloRepository.findById(1L)).thenReturn(Optional.of(d));

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(360, 240)); // Proporción 3:2
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(30, 80, 50));
                cs.addRect(0, 0, 360, 240);
                cs.fill();
            }
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        MockMultipartFile archivo = new MockMultipartFile("archivo", "plano.pdf", "application/pdf", pdfBytes);
        service.actualizarPlano(1L, archivo);

        Path hdPath = tempDir.resolve("planos/desarrollo-1.jpg");
        Path previewPath = tempDir.resolve("planos/desarrollo-1-preview.jpg");
        assertTrue(Files.exists(hdPath));
        assertTrue(Files.exists(previewPath));

        BufferedImage hd = ImageIO.read(hdPath.toFile());
        BufferedImage preview = ImageIO.read(previewPath.toFile());
        assertNotNull(hd);
        assertNotNull(preview);
        assertEquals(2400, preview.getWidth());
        assertEquals(1600, preview.getHeight());
        assertEquals((double) hd.getWidth() / hd.getHeight(), (double) preview.getWidth() / preview.getHeight(), 0.001);
    }

    @Test
    void alArrancarGeneraVistaRapidaParaPlanosYaSubidos() throws Exception {
        Path carpeta = tempDir.resolve("planos");
        Files.createDirectories(carpeta);

        BufferedImage existente = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = existente.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 3000, 2000);
        g.dispose();

        Path hdPath = carpeta.resolve("desarrollo-2.jpg");
        ImageIO.write(existente, "jpg", hdPath.toFile());

        service.asegurarPreviewsYOptimizarPlanosExistentes();

        Path previewPath = carpeta.resolve("desarrollo-2-preview.jpg");
        assertTrue(Files.exists(previewPath));
        BufferedImage preview = ImageIO.read(previewPath.toFile());
        assertNotNull(preview);
        assertEquals(2400, preview.getWidth());
        assertEquals(1600, preview.getHeight());
    }

    private void escribirJpeg(Path destino, int ancho, int alto) throws Exception {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        ImageIO.write(img, "jpg", destino.toFile());
    }

    @Test
    void alArrancarGeneraCopiaParaPdfConLadoLargoDe6000SinCambiarLaProporcion() throws Exception {
        Path carpeta = tempDir.resolve("planos");
        Files.createDirectories(carpeta);
        escribirJpeg(carpeta.resolve("desarrollo-3.jpg"), 9000, 6001);

        service.asegurarPreviewsYOptimizarPlanosExistentes();

        BufferedImage copia = ImageIO.read(carpeta.resolve("desarrollo-3-pdf.jpg").toFile());
        assertNotNull(copia);
        assertEquals(6000, copia.getWidth());
        assertEquals(4001, copia.getHeight());
        assertEquals(9000d / 6001d, (double) copia.getWidth() / copia.getHeight(), 0.001);
    }

    @Test
    void laCopiaParaPdfNoAgrandaUnPlanoChico() throws Exception {
        Path carpeta = tempDir.resolve("planos");
        Files.createDirectories(carpeta);
        escribirJpeg(carpeta.resolve("desarrollo-4.jpg"), 3000, 2000);

        service.asegurarPreviewsYOptimizarPlanosExistentes();

        BufferedImage copia = ImageIO.read(carpeta.resolve("desarrollo-4-pdf.jpg").toFile());
        assertEquals(3000, copia.getWidth());
        assertEquals(2000, copia.getHeight());
    }

    @Test
    void subirUnaImagenGeneraSuCopiaParaPdf() throws Exception {
        Desarrollo d = mock(Desarrollo.class);
        when(desarrolloRepository.findById(1L)).thenReturn(Optional.of(d));
        Path origen = tempDir.resolve("origen.jpg");
        escribirJpeg(origen, 7000, 3000);

        service.actualizarPlano(1L, new MockMultipartFile("archivo", "plano.jpg", "image/jpeg", Files.readAllBytes(origen)));

        BufferedImage copia = ImageIO.read(tempDir.resolve("planos/desarrollo-1-pdf.jpg").toFile());
        assertEquals(6000, copia.getWidth());
        assertEquals(2571, copia.getHeight());
    }

    @Test
    void subirUnPdfTambienGeneraCopiaParaPdf() throws Exception {
        Desarrollo d = mock(Desarrollo.class);
        when(desarrolloRepository.findById(1L)).thenReturn(Optional.of(d));
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(new PDRectangle(360, 240)));
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        service.actualizarPlano(1L, new MockMultipartFile("archivo", "plano.pdf", "application/pdf", pdfBytes));

        BufferedImage copia = ImageIO.read(tempDir.resolve("planos/desarrollo-1-pdf.jpg").toFile());
        assertNotNull(copia);
        assertTrue(Math.max(copia.getWidth(), copia.getHeight()) <= 6000);
    }
}
