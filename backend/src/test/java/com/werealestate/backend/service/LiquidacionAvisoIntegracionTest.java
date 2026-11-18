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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.LiquidacionAvisoDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.repository.LiquidacionAvisoRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los correos de liquidación contra la BD local (transacción que se revierte). El servicio se arma a mano con un
 * JavaMailSender simulado: ninguna prueba manda correo de verdad.
 */
class LiquidacionAvisoIntegracionTest extends PruebaDeIntegracion {

    private static final String BASE = "https://app.prueba.test/";

    @Autowired EntityManager em;
    @Autowired AutorizacionVentas autorizacion;
    @Autowired ConsultasVenta consultas;
    @Autowired VentaLoteRepository ventaLoteRepository;
    @Autowired VentaCopropietarioRepository copropietarioRepository;
    @Autowired LiquidacionAvisoRepository repositorio;

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

    private LiquidacionAvisoService servicio(String remitente) {
        return new LiquidacionAvisoService(autorizacion, consultas, ventaLoteRepository, copropietarioRepository,
                repositorio, proveedor, remitente, BASE);
    }

    /** Venta de contado de 50 000 con el correo del titular dado; si liquidada, con un recibo por todo el precio. */
    private VentaDto venta(Desarrollo desarrollo, String correo, boolean liquidada) {
        var lote = lote(desarrollo, etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        VentaDto v = crearVenta(admin, lote, "50000", null, null, null, LocalDate.now().minusMonths(1));
        em.createNativeQuery("update cliente set correo = :c where id = (select cliente_id from venta where id = :v)")
                .setParameter("c", correo).setParameter("v", v.id()).executeUpdate();
        em.clear();
        if (liquidada) {
            reciboVentaService.registrarPago(v.id(), new PagoVentaCreateRequest(LocalDate.now().minusDays(2),
                    "F-" + etiqueta(), null,
                    List.of(new PagoVentaCreateRequest.Linea(TipoAbono.APARTADO, new BigDecimal("50000"), null, null, null))));
            em.flush();
            em.clear();
        }
        return v;
    }

    private java.util.List<MimeMessage> enviados(int cuantos) {
        ArgumentCaptor<MimeMessage> c = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(cuantos)).send(c.capture());
        return c.getAllValues();
    }

    @Test
    void liquidadaConCorreoMandaDosCorreosYLosRegistra() throws Exception {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        var entidad = consultas.obtenerEntidad(v.id());
        em.persist(new VentaCopropietario(entidad, cliente("Copropietaria Uno"), 1));
        em.flush();
        String etiqueta = entidad.getEtiqueta();
        String nombre = entidad.getClienteRef().nombreCompleto();

        LiquidacionAvisoDto dto = servicio("avisos.we@gmail.com").enviar(v.id());

        var mensajes = enviados(2);
        MimeMessage finanzas = mensajes.get(0);
        finanzas.saveChanges();
        assertEquals("finanzas.weinmobiliaria@gmail.com", finanzas.getAllRecipients()[0].toString());
        assertEquals("¡Es hora de realizar una carta finiquito! – Venta " + etiqueta, finanzas.getSubject());
        StringBuilder cuerpoF = new StringBuilder();
        int[] imgF = {0};
        recoger(finanzas.getContent(), cuerpoF, imgF);
        assertTrue(cuerpoF.toString().contains("https://app.prueba.test/panel/ventas/" + etiqueta), "enlace sin doble barra");
        assertTrue(cuerpoF.toString().contains(nombre));
        assertTrue(cuerpoF.toString().contains("Copropietaria Uno"));
        assertTrue(cuerpoF.toString().contains("$50,000.00"));
        assertEquals(0, imgF[0]);

        MimeMessage cliente = mensajes.get(1);
        cliente.saveChanges();
        assertEquals("cliente@ejemplo.com", cliente.getAllRecipients()[0].toString());
        assertEquals("¡Felicidades! Ha liquidado su terreno en SAMAI Campestre 🌴", cliente.getSubject());
        assertTrue(cliente.getHeader("Subject", null).contains("=?UTF-8?"));
        StringBuilder cuerpoC = new StringBuilder();
        int[] imgC = {0};
        recoger(cliente.getContent(), cuerpoC, imgC);
        assertTrue(cuerpoC.toString().contains(nombre));
        assertTrue(cuerpoC.toString().contains("carta finiquito"));
        assertEquals(6, imgC[0], "foto, logo y 4 íconos incrustados");

        var guardado = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).orElseThrow();
        assertEquals("cliente@ejemplo.com", guardado.getCorreoCliente());
        assertTrue(guardado.isClienteEnviado());
        assertTrue(guardado.isFinanzasEnviado());
        assertEquals(admin.getId(), guardado.getUsuario().getId());
        assertNotNull(dto.ultimo());
        assertEquals(admin.getNombre(), dto.ultimo().usuario());
        assertTrue(dto.puedeEnviar());
    }

    @Test
    void sinCorreoDelClienteSoloAvisaAFinanzasYLoRegistra() throws Exception {
        VentaDto v = venta(samai(), null, true);

        LiquidacionAvisoDto estado = servicio("avisos.we@gmail.com").consultar(v.id());
        assertTrue(estado.puedeEnviar());
        assertNull(estado.correoCliente());
        servicio("avisos.we@gmail.com").enviar(v.id());

        var mensajes = enviados(1);
        mensajes.get(0).saveChanges();
        assertEquals("finanzas.weinmobiliaria@gmail.com", mensajes.get(0).getAllRecipients()[0].toString());
        var guardado = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).orElseThrow();
        assertNull(guardado.getCorreoCliente());
        assertFalse(guardado.isClienteEnviado());
        assertTrue(guardado.isFinanzasEnviado());
    }

    @Test
    void reenviarCreaOtroRegistro() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        var servicio = servicio("avisos.we@gmail.com");
        servicio.enviar(v.id());
        servicio.enviar(v.id());
        em.flush();
        assertEquals(2L, ((Number) em.createNativeQuery("select count(*) from liquidacion_aviso where venta_id = :v")
                .setParameter("v", v.id()).getSingleResult()).longValue());
        verify(sender, times(4)).send(any(MimeMessage.class));
    }

    @Test
    void ventaConSaldoPendienteResponde400YNoEnvia() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", false);
        var servicio = servicio("avisos.we@gmail.com");

        LiquidacionAvisoDto estado = servicio.consultar(v.id());
        assertTrue(estado.disponible());
        assertFalse(estado.puedeEnviar());
        assertEquals("La venta aún no está liquidada: todavía tiene saldo pendiente", estado.motivoNoDisponible());
        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("La venta aún no está liquidada: todavía tiene saldo pendiente", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void unaVentaDeNanuuNoEstaDisponibleYResponde400() {
        VentaDto v = venta(nanuu(), "cliente@ejemplo.com", true);
        var servicio = servicio("avisos.we@gmail.com");

        LiquidacionAvisoDto estado = servicio.consultar(v.id());
        assertFalse(estado.disponible());
        assertFalse(estado.puedeEnviar());
        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("Este desarrollo aún no tiene correos de liquidación", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinCorreoConfiguradoEnLaAppResponde503() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);

        var e = assertThrows(ResponseStatusException.class, () -> servicio("").enviar(v.id()));
        assertEquals(503, e.getStatusCode().value());
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void siFallaElDeFinanzasResponde502YNoSaleNadaNiQuedaRegistro() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(sender).send(any(MimeMessage.class));

        var e = assertThrows(ResponseStatusException.class, () -> servicio("avisos.we@gmail.com").enviar(v.id()));
        assertEquals(502, e.getStatusCode().value());
        verify(sender, times(1)).send(any(MimeMessage.class));
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void siSaleElDeFinanzasYFallaElDelClienteResponde502PeroRegistraLoQueSalio() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        org.mockito.Mockito.doNothing().doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(sender).send(any(MimeMessage.class));

        var e = assertThrows(ResponseStatusException.class, () -> servicio("avisos.we@gmail.com").enviar(v.id()));
        assertEquals(502, e.getStatusCode().value());
        assertTrue(e.getReason().startsWith("Se avisó a finanzas"));
        var guardado = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).orElseThrow();
        assertTrue(guardado.isFinanzasEnviado());
        assertFalse(guardado.isClienteEnviado());
        assertEquals("cliente@ejemplo.com", guardado.getCorreoCliente());
    }

    @Test
    void unAsesorNoPuedeConsultarNiEnviar() {
        VentaDto v = venta(samai(), "cliente@ejemplo.com", true);
        iniciarSesionComo(usuario(Role.ASESOR));
        var servicio = servicio("avisos.we@gmail.com");

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

        assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from liquidacion_aviso where venta_id = :v")
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
