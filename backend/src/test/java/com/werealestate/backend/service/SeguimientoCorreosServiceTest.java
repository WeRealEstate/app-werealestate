package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.werealestate.backend.model.*;
import com.werealestate.backend.repository.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SeguimientoCorreosServiceTest {
    /** Una venta con un solo pago en esa fecha y lo que prepararía el recordatorio. */
    private static SeguimientoCorreosService.Resumen previstos(LocalDate pago, boolean activos, String correo) {
        var automaticos = mock(CorreoAutomaticoRepository.class);
        var envio = mock(CorreoAutomaticoService.class);
        var recordatorio = mock(RecordatorioPagoService.class);
        var estadoCuenta = mock(EstadoCuentaService.class);
        var venta = mock(Venta.class);
        when(venta.getId()).thenReturn(1L);
        when(venta.getCliente()).thenReturn("Ana López");
        when(venta.getEtiqueta()).thenReturn("S-1");
        when(envio.activo(TipoCorreoAutomatico.RECORDATORIO)).thenReturn(activos);
        when(automaticos.conRecordatorio()).thenReturn(List.of(venta));
        var fila = mock(com.werealestate.backend.dto.AmortizacionDto.Fila.class);
        when(fila.fecha()).thenReturn(pago);
        var cuenta = mock(com.werealestate.backend.dto.AmortizacionDto.class);
        when(cuenta.detalleMensual()).thenReturn(List.of(fila));
        when(estadoCuenta.paraVenta(eq(venta), any())).thenReturn(cuenta);
        var proximo = new RecordatorioPagoCorreo.ProximoPago(pago, BigDecimal.TEN, "Mensualidad", BigDecimal.ZERO);
        var contenido = new RecordatorioPagoCorreo.Contenido("Ana", null, "S-1", List.of(), List.of(), proximo, BigDecimal.ZERO, 0, 0);
        when(recordatorio.preparar(eq(venta), any())).thenReturn(new RecordatorioPagoService.Preparado(venta, null,
                correo, contenido, null, null));
        return new SeguimientoCorreosService(mock(AutorizacionVentas.class), mock(BienvenidaClienteRepository.class),
                mock(RecordatorioPagoRepository.class), mock(LiquidacionAvisoRepository.class), automaticos, envio,
                recordatorio, mock(JdbcTemplate.class), mock(VentaRepository.class), estadoCuenta).mesActual();
    }

    @Test
    void preveLosProximosTreintaDiasAunqueCambieElMes() {
        var dentro = previstos(LocalDate.now().plusDays(29), true, "ana@example.com");
        assertEquals(1, dentro.entradas().size());
        assertEquals("PREVISTO", dentro.entradas().getFirst().estado());
        assertTrue(previstos(LocalDate.now().plusDays(40), true, "ana@example.com").entradas().isEmpty());
    }

    @Test
    void apagadosOSinCorreoSeListanComoNoSaldraConElMotivo() {
        LocalDate pago = LocalDate.now().plusDays(10);
        var apagado = previstos(pago, false, "ana@example.com").entradas().getFirst();
        assertEquals("NO_SALDRA", apagado.estado());
        assertTrue(apagado.detalle().endsWith(" · recordatorios apagados"));

        var sinCorreo = previstos(pago, true, null).entradas().getFirst();
        assertEquals("NO_SALDRA", sinCorreo.estado());
        assertTrue(sinCorreo.detalle().endsWith(" · sin correo registrado"));
    }

    @Test
    void muestraEnviosYFallosSinDuplicarAutomaticosEnviados() {
        var autorizacion = mock(AutorizacionVentas.class);
        var bienvenidas = mock(BienvenidaClienteRepository.class);
        var recordatorios = mock(RecordatorioPagoRepository.class);
        var liquidaciones = mock(LiquidacionAvisoRepository.class);
        var automaticos = mock(CorreoAutomaticoRepository.class);
        var envio = mock(CorreoAutomaticoService.class);
        var recordatorio = mock(RecordatorioPagoService.class);
        var venta = mock(Venta.class);
        when(venta.getCliente()).thenReturn("Ana López");
        when(venta.getEtiqueta()).thenReturn("S-1");
        var enviado = mock(BienvenidaCliente.class);
        when(enviado.getVenta()).thenReturn(venta);
        when(enviado.getEnviadoEn()).thenReturn(LocalDateTime.now());
        when(enviado.getCorreoDestino()).thenReturn("ana@example.com");
        when(bienvenidas.findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(any(), any())).thenReturn(List.of(enviado));
        var fallo = mock(CorreoAutomatico.class);
        when(fallo.getVenta()).thenReturn(venta);
        when(fallo.getTipo()).thenReturn(TipoCorreoAutomatico.RECORDATORIO);
        when(fallo.getEstado()).thenReturn(EstadoCorreoAutomatico.FALLIDO);
        when(fallo.getResueltoEn()).thenReturn(LocalDateTime.now());
        when(fallo.getDetalle()).thenReturn("Error de envío");
        when(automaticos.findByResueltoEnGreaterThanEqualAndResueltoEnLessThan(any(), any())).thenReturn(List.of(fallo));
        var servicio = new SeguimientoCorreosService(autorizacion, bienvenidas, recordatorios, liquidaciones,
                automaticos, envio, recordatorio, mock(JdbcTemplate.class), mock(VentaRepository.class), mock(EstadoCuentaService.class));

        var resultado = servicio.mesActual();
        verify(autorizacion).exigirAdminOLider();
        assertEquals(2, resultado.entradas().size());
        assertTrue(resultado.entradas().stream().anyMatch(e -> e.estado().equals("ENVIADO") && e.destino().equals("ana@example.com")));
        assertTrue(resultado.entradas().stream().anyMatch(e -> e.estado().equals("FALLIDO") && e.detalle().equals("Error de envío")));
    }
}
