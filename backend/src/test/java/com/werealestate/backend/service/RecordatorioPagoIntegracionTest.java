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

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.RecordatorioPagoDto;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.RecordatorioPagoRepository;
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
 * El recordatorio de pago por correo contra la BD local (transacción que se revierte). El servicio se arma a mano con
 * un JavaMailSender simulado: ninguna prueba manda correo de verdad.
 */
class RecordatorioPagoIntegracionTest extends PruebaDeIntegracion {

    @Autowired EntityManager em;
    @Autowired AutorizacionVentas autorizacion;
    @Autowired ConsultasVenta consultas;
    @Autowired EstadoCuentaService estadoCuenta;
    @Autowired RecordatorioPagoRepository repositorio;

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

    private RecordatorioPagoService servicio(String remitente) {
        return new RecordatorioPagoService(autorizacion, consultas, estadoCuenta, repositorio, proveedor, remitente);
    }

    /** Venta de hace dos meses (queda con atraso) y con próximo pago el mes que viene; el cliente con ese correo. */
    private VentaDto ventaConAtraso(String correoCliente) {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        VentaDto v = crearVenta(admin, lote, "300000", "60000", "4000", 60,
                LocalDate.now().minusMonths(2).withDayOfMonth(1));
        ponerCorreo(v, correoCliente);
        return v;
    }

    private void ponerCorreo(VentaDto v, String correo) {
        em.createNativeQuery("update cliente set correo = :c where id = (select cliente_id from venta where id = :v)")
                .setParameter("c", correo).setParameter("v", v.id()).executeUpdate();
        em.clear();
    }

    @Test
    void conProximoPagoArmaElCorreoConFechaMontoYEtiquetaDeVenta() throws Exception {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");
        AmortizacionDto tabla = estadoCuenta.deVenta(v.id());
        assertNotNull(tabla.proximaFecha());

        RecordatorioPagoDto dto = servicio("avisos.we@gmail.com").enviar(v.id());

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(enviado.capture());
        MimeMessage m = enviado.getValue();
        assertEquals("cliente@ejemplo.com", m.getAllRecipients()[0].toString());
        assertEquals("Recordatorio de pago – SAMAI Campestre", m.getSubject());
        String cuerpo = contenido(m);
        assertTrue(cuerpo.contains(tabla.ventaNumero()), "lleva la etiqueta de la venta");
        assertTrue(cuerpo.contains(RecordatorioPagoCorreo.dinero(tabla.proximoMonto())), "lleva el monto del próximo pago");
        assertTrue(cuerpo.contains(RecordatorioPagoCorreo.fecha(tabla.proximaFecha())), "lleva la fecha del próximo pago");
        assertTrue(cuerpo.contains("Mensualidad"), "lleva el concepto");
        assertTrue(cuerpo.contains(RecordatorioPagoCorreo.dinero(tabla.atrasoMonto())), "lleva el atraso");
        assertTrue(cuerpo.contains("We Real Estate"));
        assertTrue(dto.puedeEnviar());
        assertEquals(tabla.proximoMonto(), dto.proximoPago().monto());
    }

    @Test
    void guardaElRegistroTrasEnviarYLoMuestraComoUltimo() {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");
        RecordatorioPagoService servicio = servicio("avisos.we@gmail.com");
        assertNull(servicio.consultar(v.id()).ultimo());

        RecordatorioPagoDto dto = servicio.enviar(v.id());

        var guardado = repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).orElseThrow();
        assertEquals("cliente@ejemplo.com", guardado.getCorreoDestino());
        assertEquals(admin.getId(), guardado.getUsuario().getId());
        assertEquals(dto.proximoPago().fecha(), guardado.getProximoPagoFecha());
        assertTrue(guardado.getAtrasoMonto().signum() > 0);
        assertNotNull(dto.ultimo());
        assertEquals(admin.getNombre(), dto.ultimo().usuario());
    }

    @Test
    void alEliminarLaVentaSusRecordatoriosSeBorranConElla() {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");
        servicio("avisos.we@gmail.com").enviar(v.id());
        em.flush();
        em.clear(); // en producción enviar y eliminar son peticiones distintas: no comparten contexto de persistencia

        ventaService.eliminarYaAutorizada(v.id());
        em.flush();
        em.clear();

        assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from recordatorio_pago where venta_id = :v")
                .setParameter("v", v.id()).getSingleResult()).longValue());
    }

    @Test
    void sinCorreoDelClienteResponde400YNoEnvia() {
        VentaDto v = ventaConAtraso(null);
        RecordatorioPagoService servicio = servicio("avisos.we@gmail.com");

        RecordatorioPagoDto estado = servicio.consultar(v.id());
        assertFalse(estado.puedeEnviar());
        assertEquals("El cliente no tiene correo registrado", estado.motivoNoDisponible());

        var e = assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        assertEquals("El cliente no tiene correo registrado", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void sinPagosPendientesNoSePuedeEnviar() {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        VentaDto v = crearVenta(admin, lote, "100000", "100000", null, null, LocalDate.now().minusDays(3));
        ponerCorreo(v, "cliente@ejemplo.com");
        reciboVentaService.registrarPago(v.id(), new PagoVentaCreateRequest(LocalDate.now(), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.ENGANCHE, new BigDecimal("100000"), null, null, null))));
        em.flush();
        em.clear();
        RecordatorioPagoService servicio = servicio("avisos.we@gmail.com");

        assertEquals("No hay pagos pendientes", servicio.consultar(v.id()).motivoNoDisponible());
        assertThrows(ValidationException.class, () -> servicio.enviar(v.id()));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinCorreoConfiguradoEnLaAppResponde503() {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");

        var e = assertThrows(ResponseStatusException.class, () -> servicio("").enviar(v.id()));
        assertEquals("El correo de la app no está configurado", e.getReason());
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void siElServidorDeCorreoFallaNoQuedaRegistro() {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(sender).send(any(MimeMessage.class));

        var e = assertThrows(ResponseStatusException.class, () -> servicio("avisos.we@gmail.com").enviar(v.id()));
        assertEquals(502, e.getStatusCode().value());
        assertTrue(repositorio.findFirstByVentaIdOrderByEnviadoEnDescIdDesc(v.id()).isEmpty());
    }

    @Test
    void unAsesorNoPuedeEnviar() {
        VentaDto v = ventaConAtraso("cliente@ejemplo.com");
        iniciarSesionComo(usuario(Role.ASESOR));

        assertThrows(ForbiddenOperationException.class, () -> servicio("avisos.we@gmail.com").enviar(v.id()));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    private static String contenido(MimeMessage m) throws Exception {
        StringBuilder sb = new StringBuilder();
        recoger(m.getContent(), sb);
        return sb.toString();
    }

    private static void recoger(Object parte, StringBuilder sb) throws Exception {
        if (parte instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) recoger(mp.getBodyPart(i).getContent(), sb);
        } else {
            sb.append(parte);
        }
    }
}
