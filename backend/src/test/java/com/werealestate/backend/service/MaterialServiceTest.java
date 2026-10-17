package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.ProyectoMaterial;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MaterialServiceTest {

    @Test
    void reconoceElTipoPorLaFirmaNoPorElNombre() {
        assertEquals("pdf", MaterialService.extensionPorFirma("%PDF-1.7 ...".getBytes()));
        assertEquals("png", MaterialService.extensionPorFirma(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}));
        assertEquals("jpg", MaterialService.extensionPorFirma(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}));
        assertEquals("mp4", MaterialService.extensionPorFirma(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}));
        assertThrows(ValidationException.class, () -> MaterialService.extensionPorFirma("MZ ejecutable".getBytes()));
    }

    @Test
    void elNombreDeDescargaNoLlevaRutasNiCaracteresRaros() {
        assertEquals("flyer samai.pdf", MaterialService.nombreSeguro("../../etc/flyer samai.exe", "pdf"));
        assertEquals("material.jpg", MaterialService.nombreSeguro("<>|?.png", "jpg"));
        assertEquals("material.mp4", MaterialService.nombreSeguro(null, "mp4"));
    }

    @Test
    void elProyectoAmbosAplicaAAmbosPlanos() {
        assertTrue(ProyectoMaterial.AMBOS.aplicaA("samai"));
        assertTrue(ProyectoMaterial.AMBOS.aplicaA("nanuu"));
        assertTrue(ProyectoMaterial.SAMAI.aplicaA("samai"));
        assertFalse(ProyectoMaterial.SAMAI.aplicaA("nanuu"));
    }

    @Test
    void laMiniaturaReduceLaImagenSinDeformarla() throws Exception {
        BufferedImage grande = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(grande, "png", png);
        BufferedImage mini = MaterialService.reducir(png.toByteArray());
        assertEquals(480, mini.getWidth());
        assertEquals(240, mini.getHeight());
    }

    @Test
    void unaImagenChicaNoSeAgranda() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "png", png);
        BufferedImage mini = MaterialService.reducir(png.toByteArray());
        assertEquals(200, mini.getWidth());
    }
}
