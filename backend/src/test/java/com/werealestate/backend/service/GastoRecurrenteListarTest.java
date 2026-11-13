package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.GastoRecurrenteDto;
import com.werealestate.backend.model.FrecuenciaGasto;
import com.werealestate.backend.model.GastoRecurrente;
import com.werealestate.backend.model.GastoRecurrentePago;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.GastoRecurrentePagoRepository;
import com.werealestate.backend.repository.GastoRecurrenteRepository;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaComisionDevolucionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** La lista de gastos recurrentes trae, de cada uno, cuántos vencimientos tiene sin pagar y el más próximo. */
class GastoRecurrenteListarTest {

    private static GastoRecurrente recurrente(long id, String nombre) {
        GastoRecurrente g = new GastoRecurrente(nombre, new BigDecimal("100"), FrecuenciaGasto.SEMANAL, null, null,
                LocalDate.now().plusYears(5)); // en el futuro: generarPendientes no crea nada nuevo
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    private static GastoRecurrentePago pendiente(GastoRecurrente g, LocalDate fecha) {
        return new GastoRecurrentePago(g, fecha, g.getMontoEstimado());
    }

    @Test
    void cuentaLosPendientesDeCadaUnoYTomaElPrimeroComoProximo() {
        GastoRecurrente renta = recurrente(1, "Renta");
        GastoRecurrente luz = recurrente(2, "Luz");
        GastoRecurrente agua = recurrente(3, "Agua");
        LocalDate hoy = LocalDate.now();
        GastoRecurrenteRepository recurrentes = mock(GastoRecurrenteRepository.class);
        when(recurrentes.findAllByOrderByNombreAsc()).thenReturn(List.of(agua, luz, renta));
        GastoRecurrentePagoRepository pagos = mock(GastoRecurrentePagoRepository.class);
        when(pagos.findSinPagar()).thenReturn(List.of(
                pendiente(renta, hoy.minusDays(3)), pendiente(luz, hoy.plusDays(2)),
                pendiente(renta, hoy.plusDays(4)), pendiente(renta, hoy.plusDays(11))));
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(admin);
        GastoRecurrenteService service = new GastoRecurrenteService(recurrentes, pagos, mock(GastoRepository.class),
                mock(PagoVentaRepository.class), mock(VentaComisionDevolucionRepository.class), mock(GastoService.class),
                provider);

        List<GastoRecurrenteDto> lista = service.listar();

        assertEquals(List.of("Agua", "Luz", "Renta"), lista.stream().map(GastoRecurrenteDto::nombre).toList());
        assertEquals(0, lista.get(0).sinPagar());
        assertNull(lista.get(0).proximoVencimiento());
        assertEquals(1, lista.get(1).sinPagar());
        assertEquals(hoy.plusDays(2), lista.get(1).proximoVencimiento());
        assertEquals(3, lista.get(2).sinPagar());
        assertEquals(hoy.minusDays(3), lista.get(2).proximoVencimiento());
    }
}
