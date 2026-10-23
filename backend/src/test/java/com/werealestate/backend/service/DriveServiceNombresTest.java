package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.WeFormRespuestaItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DriveServiceNombresTest {

    @Test
    void carpetaConSoloElNombreDelCliente() {
        Cliente c = mock(Cliente.class);
        when(c.getNumero()).thenReturn(12L);
        when(c.nombreCompleto()).thenReturn("  Laura   Méndez Ruiz ");
        assertEquals("Laura Méndez Ruiz", DriveService.nombreDeCarpeta(c));
    }

    @Test
    void archivoConSoloElTituloDeLaPregunta() {
        WeFormRespuestaItem item = mock(WeFormRespuestaItem.class);
        when(item.getPreguntaTitulo()).thenReturn("INE frente");
        when(item.getArchivoExtension()).thenReturn("jpg");
        assertEquals("INE frente.jpg", DriveService.nombreDelArchivo(item));
    }

    @Test
    void sinLasTresVariablesQuedaApagado() {
        assertFalse(new GoogleDriveClient(new ObjectMapper(), "id", "", "http://localhost/cb", "").configurado());
        assertTrue(new GoogleDriveClient(new ObjectMapper(), "id", "secreto", "http://localhost/cb", "").configurado());
    }

    @Test
    void laDireccionDeGooglePideSoloDriveFileYUnRefreshToken() {
        String url = new GoogleDriveClient(new ObjectMapper(), "123456789-abc.apps.googleusercontent.com", "secreto", "https://app.ejemplo.com/api/drive/oauth/callback", "clave")
                .urlDeAutorizacion("abc");
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        assertTrue(url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file"));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("prompt=consent"));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.ejemplo.com%2Fapi%2Fdrive%2Foauth%2Fcallback"));
        assertTrue(url.contains("state=abc"));
        assertFalse(url.contains("secreto"));
    }

    @Test
    void elAppIdDelSelectorEsElNumeroDeProyectoDelClientId() {
        GoogleDriveClient g = new GoogleDriveClient(new ObjectMapper(), "123456789-abc.apps.googleusercontent.com", "s", "http://x/cb", "clave");
        assertEquals("123456789", g.appId());
        assertTrue(g.selectorConfigurado());
        assertFalse(new GoogleDriveClient(new ObjectMapper(), "123-abc", "s", "http://x/cb", "").selectorConfigurado());
    }

    @Test
    void despuesDeFallarEsperaCadaVezMasAntesDeReintentar() {
        WeFormRespuestaItem nuevo = mock(WeFormRespuestaItem.class);
        when(nuevo.getDriveIntentos()).thenReturn(0);
        assertTrue(DriveService.leToca(nuevo));

        WeFormRespuestaItem recien = mock(WeFormRespuestaItem.class);
        when(recien.getDriveIntentos()).thenReturn(3); // espera 8 minutos
        when(recien.getDriveUltimoIntento()).thenReturn(LocalDateTime.now().minusMinutes(5));
        assertFalse(DriveService.leToca(recien));

        WeFormRespuestaItem yaEspero = mock(WeFormRespuestaItem.class);
        when(yaEspero.getDriveIntentos()).thenReturn(3);
        when(yaEspero.getDriveUltimoIntento()).thenReturn(LocalDateTime.now().minusMinutes(9));
        assertTrue(DriveService.leToca(yaEspero));

        WeFormRespuestaItem muchos = mock(WeFormRespuestaItem.class); // tope: 6 horas
        when(muchos.getDriveIntentos()).thenReturn(7);
        when(muchos.getDriveUltimoIntento()).thenReturn(LocalDateTime.now().minusMinutes(130));
        assertTrue(DriveService.leToca(muchos));
    }

    @Test
    void lasCaidasDeGoogleNoCuentanComoFalloDelArchivo() {
        assertTrue(new GoogleDriveClient.GoogleError(0, "sin red").esTemporal());
        assertTrue(new GoogleDriveClient.GoogleError(503, "x").esTemporal());
        assertTrue(new GoogleDriveClient.GoogleError(429, "x").esTemporal());
        assertTrue(new GoogleDriveClient.GoogleError(403, "{\"reason\":\"userRateLimitExceeded\"}").esTemporal());
        assertFalse(new GoogleDriveClient.GoogleError(404, "File not found").esTemporal());
        assertFalse(new GoogleDriveClient.GoogleError(400, "invalid").esTemporal());
        assertTrue(new GoogleDriveClient.GoogleError(400, "{\"error\":\"invalid_grant\"}").accesoPerdido());
    }
}
