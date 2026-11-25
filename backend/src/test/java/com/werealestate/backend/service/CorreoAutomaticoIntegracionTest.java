package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ContratoRequest;
import com.werealestate.backend.dto.CorreosVentaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.MedioFirma;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAbono;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.BienvenidaClienteRepository;
import com.werealestate.backend.repository.CorreoAutomaticoConfigRepository;
import com.werealestate.backend.repository.CorreoAutomaticoRepository;
import com.werealestate.backend.repository.LiquidacionAvisoRepository;
import com.werealestate.backend.repository.RecordatorioPagoRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Correos automáticos contra la BD local (transacción que se revierte), con un JavaMailSender simulado: ninguna prueba
 * manda correo de verdad. Las demás ventas de la BD local son anteriores (correos_automaticos = false) y los
 * recordatorios se revisan solo sobre la venta de la prueba (las demás se excluyen dentro de la transacción).
 */
class CorreoAutomaticoIntegracionTest extends PruebaDeIntegracion {

    @Autowired EntityManager em;
    @Autowired AutorizacionVentas autorizacion;
    @Autowired ConsultasVenta consultas;
    @Autowired EstadoCuentaService estadoCuenta;
    @Autowired VentaLoteRepository ventaLoteRepository;
    @Autowired VentaCopropietarioRepository copropietarios;
    @Autowired BienvenidaClienteRepository bienvenidas;
    @Autowired LiquidacionAvisoRepository liquidaciones;
    @Autowired RecordatorioPagoRepository recordatorios;
    @Autowired CorreoAutomaticoRepository automaticos;
    @Autowired CorreoAutomaticoConfigRepository config;
    @Autowired CorreoVentaConsultaService consulta;
    @Autowired ContratoVentaService contratos;
    @Autowired PlatformTransactionManager transacciones;

    private JavaMailSender sender;
    private CorreoAutomaticoService servicio;
    private Usuario admin;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(sender);
        var bienvenida = new BienvenidaClienteService(
                autorizacion, consultas, ventaLoteRepository, bienvenidas, proveedor, "avisos@gmail.com");
        var liquidacion = new LiquidacionAvisoService(autorizacion, consultas, ventaLoteRepository, copropietarios,
                liquidaciones, proveedor, "avisos@gmail.com", "https://app.ejemplo.com");
        var recordatorio = new RecordatorioPagoService(
                autorizacion, consultas, estadoCuenta, recordatorios, proveedor, "avisos@gmail.com");
        servicio = new CorreoAutomaticoService(automaticos, config, bienvenida, liquidacion, recordatorio,
                bienvenidas, liquidaciones, recordatorios, consultas, transacciones);
        em.createNativeQuery("update venta set sin_recordatorio_automatico = true").executeUpdate();
        em.createNativeQuery("delete from correo_automatico_config").executeUpdate();
        em.createNativeQuery("delete from correo_automatico where estado = 'PENDIENTE'").executeUpdate();
    }

    private void encender(TipoCorreoAutomatico tipo) {
        consulta.cambiar(tipo, true);
    }

    private VentaDto venta(String precio, String enganche, String mensualidad, Integer plazo, LocalDate fecha) {
        var lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        VentaDto v = crearVenta(admin, lote, precio, enganche, mensualidad, plazo, fecha);
        em.createNativeQuery("update cliente set correo = 'cliente@ejemplo.com' where id = (select cliente_id from venta where id = :v)")
                .setParameter("v", v.id()).executeUpdate();
        em.flush();
        em.clear();
        return v;
    }

    private void firmar(VentaDto v, boolean firmado) {
        contratos.registrarContrato(v.id(), new ContratoRequest(firmado, firmado ? LocalDate.now() : null,
                firmado ? MedioFirma.FISICO : null, null));
        em.flush();
        em.clear();
    }

    private void pagar(VentaDto v, String monto) {
        reciboVentaService.registrarPago(v.id(), new PagoVentaCreateRequest(LocalDate.now(), "F-" + etiqueta(), null,
                List.of(new PagoVentaCreateRequest.Linea(TipoAbono.ENGANCHE, new BigDecimal(monto), null, null, null))));
        em.flush();
        em.clear();
    }

    private CorreosVentaDto registro(VentaDto v) {
        em.flush();
        em.clear();
        return consulta.deVenta(v.id());
    }

    @Test
    void laBienvenidaSaleDiezMinutosDespuesDeFirmarYUnaSolaVez() {
        encender(TipoCorreoAutomatico.BIENVENIDA);
        VentaDto v = venta("300000", "60000", "4000", 60, LocalDate.now());
        firmar(v, true);
        LocalDateTime t = LocalDate.now().atTime(10, 0);

        servicio.ciclo(t);
        servicio.ciclo(t.plusMinutes(9));
        verify(sender, never()).send(any(MimeMessage.class));
        assertEquals(1, registro(v).pendientes().size());

        servicio.ciclo(t.plusMinutes(10));
        servicio.ciclo(t.plusMinutes(11));
        verify(sender, times(1)).send(any(MimeMessage.class));
        var envio = registro(v).envios().get(0);
        assertTrue(envio.enviado() && envio.automatico());
        assertNull(envio.usuario());

        firmar(v, false);
        firmar(v, true);
        servicio.ciclo(t.plusMinutes(40));
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void siSeQuitaLaFirmaAntesNoSale() {
        encender(TipoCorreoAutomatico.BIENVENIDA);
        VentaDto v = venta("300000", "60000", "4000", 60, LocalDate.now());
        firmar(v, true);
        LocalDateTime t = LocalDate.now().atTime(10, 0);
        servicio.ciclo(t);
        firmar(v, false);

        servicio.ciclo(t.plusMinutes(10));

        verify(sender, never()).send(any(MimeMessage.class));
        assertTrue(registro(v).pendientes().isEmpty());
    }

    @Test
    void apagadoQuedaAnotadoYNoSaleAlEncenderloDespues() {
        VentaDto v = venta("300000", "60000", "4000", 60, LocalDate.now());
        firmar(v, true);
        LocalDateTime t = LocalDate.now().atTime(10, 0);
        servicio.ciclo(t);
        encender(TipoCorreoAutomatico.BIENVENIDA);

        servicio.ciclo(t.plusMinutes(30));

        verify(sender, never()).send(any(MimeMessage.class));
        var envio = registro(v).envios().get(0);
        assertFalse(envio.enviado());
        assertEquals("El envío automático estaba apagado", envio.detalle());
    }

    @Test
    void alLiquidarAvisaAFinanzasPeroNoEnVentasAnteriores() {
        encender(TipoCorreoAutomatico.LIQUIDACION);
        VentaDto nueva = venta("100000", "100000", null, null, LocalDate.now());
        VentaDto anterior = venta("100000", "100000", null, null, LocalDate.now());
        em.createNativeQuery("update venta set correos_automaticos = false where id = :v")
                .setParameter("v", anterior.id()).executeUpdate();
        pagar(nueva, "100000");
        pagar(anterior, "100000");
        LocalDateTime t = LocalDate.now().atTime(10, 0);

        servicio.ciclo(t);
        servicio.ciclo(t.plusMinutes(10));

        verify(sender, times(1)).send(any(MimeMessage.class));
        assertEquals("A finanzas", registro(nueva).envios().get(0).detalle());
        assertTrue(registro(anterior).envios().isEmpty());
        assertFalse(registro(anterior).ventaNueva());
    }

    @Test
    void siElCorreoFallaSeReintentaTresVecesYQuedaAnotado() {
        encender(TipoCorreoAutomatico.BIENVENIDA);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(sender).send(any(MimeMessage.class));
        VentaDto v = venta("300000", "60000", "4000", 60, LocalDate.now());
        firmar(v, true);
        LocalDateTime t = LocalDate.now().atTime(10, 0);
        servicio.ciclo(t);

        for (int minuto = 10; minuto <= 40; minuto += 5) servicio.ciclo(t.plusMinutes(minuto));

        verify(sender, times(3)).send(any(MimeMessage.class));
        var envio = registro(v).envios().get(0);
        assertFalse(envio.enviado());
        assertTrue(registro(v).pendientes().isEmpty());
    }

    @Test
    void elRecordatorioSaleALasSeisDelDiaAnteriorTambienEnVentasAnterioresSalvoExcluidas() {
        encender(TipoCorreoAutomatico.RECORDATORIO);
        // Primera mensualidad el mes que viene: el día anterior a ella se manda.
        LocalDate fecha = LocalDate.now().withDayOfMonth(10);
        VentaDto v = venta("300000", "60000", "4000", 60, fecha);
        VentaDto excluida = venta("300000", "60000", "4000", 60, fecha);
        pagar(v, "60000");
        pagar(excluida, "60000");
        em.createNativeQuery("update venta set correos_automaticos = false, sin_recordatorio_automatico = false where id = :v")
                .setParameter("v", v.id()).executeUpdate();
        em.clear();
        consulta.excluirRecordatorio(excluida.id(), true);
        LocalDate proximo = estadoCuenta.deVenta(v.id()).proximaFecha();
        LocalDate vispera = proximo.minusDays(1);

        servicio.ciclo(vispera.atTime(LocalTime.of(17, 59)));
        verify(sender, never()).send(any(MimeMessage.class));
        servicio.ciclo(vispera.atTime(LocalTime.of(18, 0)));
        servicio.ciclo(vispera.atTime(LocalTime.of(18, 1)));

        verify(sender, times(1)).send(any(MimeMessage.class));
        assertEquals(proximo, recordatorios.findByVentaId(v.id()).get(0).getProximoPagoFecha());
        assertTrue(recordatorios.findByVentaId(excluida.id()).isEmpty());
    }
}
