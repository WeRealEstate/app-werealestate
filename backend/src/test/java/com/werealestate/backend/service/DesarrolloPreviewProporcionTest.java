package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La vista rápida del plano debe tener la proporción del plano: la de Nanuu era de un plano anterior (1920×2400 con
 * un plano casi cuadrado) y, como era más nueva que el HD, nunca se rehacía; el plano público arrancaba con franjas
 * negras. */
class DesarrolloPreviewProporcionTest {

    @TempDir Path uploads;

    private static void jpg(Path ruta, int ancho, int alto) throws Exception {
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpg", ruta.toFile());
    }

    @Test
    void rehaceLaVistaRapidaConOtraProporcionAunqueSeaMasNueva() throws Exception {
        Path planos = Files.createDirectories(uploads.resolve("planos"));
        Path hd = planos.resolve("desarrollo-2.jpg");
        Path preview = planos.resolve("desarrollo-2-preview.jpg");
        jpg(hd, 949, 981);
        jpg(preview, 192, 240);
        Files.setLastModifiedTime(hd, FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(preview, FileTime.fromMillis(2_000_000_000_000L));
        assertFalse(DesarrolloService.mismaProporcion(preview, hd));

        new DesarrolloService(mock(DesarrolloRepository.class), mock(CurrentUserProvider.class), uploads.toString(),
                uploads.resolve("privados").toString()).asegurarPreviewsYOptimizarPlanosExistentes();

        assertTrue(DesarrolloService.mismaProporcion(preview, hd));
        BufferedImage nueva = ImageIO.read(preview.toFile());
        assertEquals(949.0 / 981.0, (double) nueva.getWidth() / nueva.getHeight(), 0.01);
    }

    @Test
    void conLaMismaProporcionNoLaToca() throws Exception {
        Path planos = Files.createDirectories(uploads.resolve("planos"));
        Path hd = planos.resolve("desarrollo-1.jpg");
        Path preview = planos.resolve("desarrollo-1-preview.jpg");
        jpg(hd, 1500, 1000);
        jpg(preview, 150, 100);
        FileTime despues = FileTime.fromMillis(2_000_000_000_000L);
        Files.setLastModifiedTime(hd, FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(preview, despues);

        new DesarrolloService(mock(DesarrolloRepository.class), mock(CurrentUserProvider.class), uploads.toString(),
                uploads.resolve("privados").toString()).asegurarPreviewsYOptimizarPlanosExistentes();

        assertEquals(despues, Files.getLastModifiedTime(preview));
    }
}
