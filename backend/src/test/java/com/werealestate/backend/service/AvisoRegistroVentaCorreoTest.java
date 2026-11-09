package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** El aviso "¡Es hora de realizar un contrato!" que le llega a finanzas por cada registro de venta. */
class AvisoRegistroVentaCorreoTest {

    private static final AvisoRegistroVentaCorreo.RegistroDeVentaRecibido REGISTRO =
            new AvisoRegistroVentaCorreo.RegistroDeVentaRecibido("Ana López", "Beto Externo", "SAMAI Campestre",
                    "Registro SAMAI", 7L, List.of("Manzana 3, Lote 1", "Manzana 3, Lote 2"));

    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> proveedor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(sender);
    }

    private AvisoRegistroVentaCorreo aviso(String remitente) {
        return new AvisoRegistroVentaCorreo(proveedor, remitente, new long[] {0, 0, 0});
    }

    @Test
    void sinCuentaDeCorreoNoMandaNada() {
        aviso("").alRecibirRegistro(REGISTRO);
        aviso(null).alRecibirRegistro(REGISTRO);

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void leLlegaAFinanzasConElAsuntoYLosDatos() throws Exception {
        aviso("avisos.we@gmail.com").alRecibirRegistro(REGISTRO);

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(enviado.capture());
        MimeMessage correo = enviado.getValue();
        assertEquals("finanzas.weinmobiliaria@gmail.com",
                ((InternetAddress) correo.getRecipients(Message.RecipientType.TO)[0]).getAddress());
        assertEquals(1, correo.getAllRecipients().length);
        assertEquals("¡Es hora de realizar un contrato!", correo.getSubject());
        InternetAddress de = (InternetAddress) correo.getFrom()[0];
        assertEquals("avisos.we@gmail.com", de.getAddress());
        assertEquals("We Real Estate", de.getPersonal());
    }

    @Test
    void elTextoLlevaClienteAsesorDesarrolloLotesVentaYFormulario() {
        String texto = AvisoRegistroVentaCorreo.texto(REGISTRO);

        assertTrue(texto.startsWith("¡Es hora de realizar un contrato!"));
        assertTrue(texto.contains("Cliente: Ana López"));
        assertTrue(texto.contains("Asesor: Beto Externo"));
        assertTrue(texto.contains("Desarrollo: SAMAI Campestre"));
        assertTrue(texto.contains("Lotes: Manzana 3, Lote 1; Manzana 3, Lote 2"));
        assertTrue(texto.contains("Venta: #7"));
        assertTrue(texto.contains("Formulario: Registro SAMAI"));
    }

    @Test
    void unSoloLoteDiceLote() {
        var unLote = new AvisoRegistroVentaCorreo.RegistroDeVentaRecibido(
                "Ana", "Beto", "Aldea Nanuu", "Registro Nanuu", 3L, List.of("Manzana 1, Lote 9"));

        assertTrue(AvisoRegistroVentaCorreo.texto(unLote).contains("Lote: Manzana 1, Lote 9"));
        assertTrue(AvisoRegistroVentaCorreo.html(unLote).contains(">Lote<"));
    }

    @Test
    void elHtmlNoInterpretaLoQueEscribioElAsesor() {
        var conEtiquetas = new AvisoRegistroVentaCorreo.RegistroDeVentaRecibido(
                "Ana <script>alert(1)</script>", "Beto & Co", "SAMAI Campestre", "Registro <b>", 8L, List.of("Manzana 1, Lote 1"));

        String html = AvisoRegistroVentaCorreo.html(conEtiquetas);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("Ana &lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("Beto &amp; Co"));
        assertTrue(html.contains("Registro &lt;b&gt;"));
    }

    @Test
    void siFallaElEnvioLoReintenta() {
        doThrow(new MailSendException("SMTP caído")).doNothing().when(sender).send(any(MimeMessage.class));

        aviso("avisos.we@gmail.com").alRecibirRegistro(REGISTRO);

        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void siNuncaSaleSeRindeTrasTresIntentos() {
        doThrow(new MailSendException("SMTP caído")).when(sender).send(any(MimeMessage.class));

        aviso("avisos.we@gmail.com").alRecibirRegistro(REGISTRO);

        verify(sender, times(3)).send(any(MimeMessage.class));
    }
}
