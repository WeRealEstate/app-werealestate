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
}
