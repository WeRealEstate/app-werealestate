package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.BienvenidaClienteDto;
import com.werealestate.backend.dto.ContratoRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.MedioFirma;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.BienvenidaClienteRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

/**
 * El correo de bienvenida contra la BD local (transacción que se revierte). El servicio se arma a mano con un
 * JavaMailSender simulado: ninguna prueba manda correo de verdad.
 */
class BienvenidaClienteIntegracionTest extends PruebaDeIntegracion {

    @Autowired EntityManager em;
    @Autowired AutorizacionVentas autorizacion;
    @Autowired ConsultasVenta consultas;
    @Autowired VentaLoteRepository ventaLoteRepository;
    @Autowired BienvenidaClienteRepository repositorio;
    @Autowired ContratoVentaService contratoVentaService;

    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> proveedor;
    private Usuario admin;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(sender);
    }

    private BienvenidaClienteService servicio(String remitente) {
        return new BienvenidaClienteService(autorizacion, consultas, ventaLoteRepository, repositorio, proveedor, remitente);
    }

    private VentaDto venta(Desarrollo desarrollo, String correo, boolean firmada) {
        var lote = lote(desarrollo, etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        VentaDto v = crearVenta(admin, lote, "300000", "60000", "4000", 60, LocalDate.now().minusMonths(1));
        em.createNativeQuery("update cliente set correo = :c where id = (select cliente_id from venta where id = :v)")
                .setParameter("c", correo).setParameter("v", v.id()).executeUpdate();
        em.clear();
        if (firmada) {
            contratoVentaService.registrarContrato(v.id(),
                    new ContratoRequest(true, LocalDate.now().minusDays(1), MedioFirma.FISICO, null));
            em.flush();
            em.clear();
        }
        return v;
    }

    @Test
    void armaElCorreoConNombreAsuntoConEmojiYSeisImagenesEnLinea() throws Exception {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        String nombre = consultas.obtenerEntidad(v.id()).getClienteRef().nombreCompleto();

        BienvenidaClienteDto dto = servicio("avisos.we@gmail.com").enviar(v.id());

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(enviado.capture());
        MimeMessage m = enviado.getValue();
        m.saveChanges();
        assertEquals("cliente@ejemplo.com", m.getAllRecipients()[0].toString());
        assertEquals("Bienvenido a We Real Estate - Samai Campestre 🌴", m.getSubject());
        // En el encabezado crudo viaja codificado en UTF-8 (no se pierden el emoji ni los acentos).
        assertTrue(m.getHeader("Subject", null).contains("=?UTF-8?"), m.getHeader("Subject", null));
        StringBuilder cuerpo = new StringBuilder();
        int[] imagenes = {0};
        recoger(m.getContent(), cuerpo, imagenes);
        assertTrue(cuerpo.toString().contains(nombre), "lleva el nombre del titular");
        assertTrue(cuerpo.toString().contains("Gracias por confiar en We Real Estate y formar parte de SAMAI Campestre."));
        assertEquals(6, imagenes[0], "foto, logo y 4 íconos incrustados");
        assertTrue(dto.puedeEnviar());
        assertTrue(dto.disponible());
    }

    @Test
    void guardaElRegistroTrasEnviarYLoMuestraComoUltimo() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        BienvenidaClienteService servicio = servicio("avisos.we@gmail.com");
        assertNull(servicio.consultar(v.id()).ultimo());

        BienvenidaClienteDto dto = servicio.enviar(v.id());

        var guardado = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).orElseThrow();
        assertEquals("cliente@ejemplo.com", guardado.getCorreoDestino());
        assertEquals(admin.getId(), guardado.getUsuario().getId());
        assertNotNull(dto.ultimo());
        assertEquals(admin.getNombre(), dto.ultimo().usuario());
        assertEquals("cliente@ejemplo.com", dto.ultimo().correo());
    }

    @Test
    void sinContratoFirmadoResponde400YNoEnvia() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", false);
        BienvenidaClienteService servicio = servicio("avisos.we@gmail.com");

        BienvenidaClienteDto estado = servicio.consultar(v.id());
        assertFalse(estado.puedeEnviar());
        assertTrue(estado.disponible());
        assertEquals("El contrato aún no está firmado", estado.motivoNoDisponible());
        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("El contrato aún no está firmado", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinCorreoDelClienteResponde400() {
        VentaDto v = venta(samai(), null, true);
        BienvenidaClienteService servicio = servicio("avisos.we@gmail.com");

        assertEquals("El cliente no tiene correo registrado", servicio.consultar(v.id()).motivoNoDisponible());
        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("El cliente no tiene correo registrado", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void unaVentaDeNanuuNoTienePlantillaYResponde400() {
        VentaDto v = venta(nanuu(), "cliente@ejemplo.com", true);
        BienvenidaClienteService servicio = servicio("avisos.we@gmail.com");

        BienvenidaClienteDto estado = servicio.consultar(v.id());
        assertFalse(estado.disponible());
        assertFalse(estado.puedeEnviar());
        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("Este desarrollo aún no tiene correo de bienvenida", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinCorreoConfiguradoEnLaAppResponde503() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);

        var e = assertThrows(ResponseStatusException.class, () -> servicio("").enviar(v.id()));
        assertEquals(503, e.getStatusCode().value());
        assertEquals("El correo de la app no está configurado", e.getReason());
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void siElServidorDeCorreoFallaResponde502YNoQuedaRegistro() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(sender).send(any(MimeMessage.class));

        var e = assertThrows(ResponseStatusException.class, () -> servicio("avisos.we@gmail.com").enviar(v.id()));
        assertEquals(502, e.getStatusCode().value());
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void unAsesorNoPuedeConsultarNiEnviar() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        iniciarSesionComo(usuario(Role.ASESOR));
        BienvenidaClienteService servicio = servicio("avisos.we@gmail.com");

        assertThrows(ForbiddenOperationException.class, () -> servicio.consultar(v.id()));
        assertThrows(ForbiddenOperationException.class, () -> servicio.enviar(v.id()));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void alEliminarLaVentaSusRegistrosSeBorranConElla() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        servicio("avisos.we@gmail.com").enviar(v.id());
        em.flush();
        em.clear();

        ventaService.eliminarYaAutorizada(v.id());
        em.flush();
        em.clear();

        assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from bienvenida_cliente where venta_id = :v")
                .setParameter("v", v.id()).getSingleResult()).longValue());
    }

    private static void recoger(Object parte, StringBuilder sb, int[] imagenes) throws Exception {
        if (parte instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                var bp = mp.getBodyPart(i);
                if (bp.getContentType().toLowerCase().startsWith("image/")) imagenes[0]++;
                else recoger(bp.getContent(), sb, imagenes);
            }
        } else {
            sb.append(parte);
        }
    }
}
