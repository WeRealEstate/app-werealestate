package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.NumeroClienteDto;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.ClienteNumero;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.ClienteNumeroRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteNumeracionServiceTest {

    private ClienteNumeroRepository numeros;
    private VentaLoteRepository lotes;
    private VentaCopropietarioRepository copropietarios;
    private ClienteNumeracionService servicio;
    /** Lo que "hay en la base": se llena con lo que el servicio guarda. */
    private final List<ClienteNumero> guardados = new ArrayList<>();

    private final Desarrollo samai = desarrollo(1L, "SAMAI Campestre");
    private final Desarrollo nanuu = desarrollo(2L, "Aldea Nanuu");
    private final Desarrollo otro = desarrollo(3L, "Otro");

    @BeforeEach
    void preparar() {
        numeros = mock(ClienteNumeroRepository.class);
        lotes = mock(VentaLoteRepository.class);
        copropietarios = mock(VentaCopropietarioRepository.class);
        servicio = new ClienteNumeracionService(numeros, lotes, copropietarios);
        when(numeros.save(any(ClienteNumero.class))).thenAnswer(inv -> {
            ClienteNumero n = inv.getArgument(0);
            guardados.add(n);
            return n;
        });
        when(numeros.findByClienteIdAndDesarrolloId(anyLong(), anyLong())).thenAnswer(inv -> guardados.stream()
                .filter(n -> n.getCliente().getId().equals(inv.getArgument(0)) && n.getDesarrollo().getId().equals(inv.getArgument(1)))
                .findFirst());
        when(numeros.maxNumero(anyLong())).thenAnswer(inv -> guardados.stream()
                .filter(n -> n.getDesarrollo().getId().equals(inv.getArgument(0)))
                .mapToLong(ClienteNumero::getNumero).max().orElse(0));
    }

    @Test
    void elTitularNuevoRecibeElSiguienteYLosCopropietariosElMismo() {
        guardados.add(new ClienteNumero(cliente(90L), samai, 39)); // ya hay hasta el S-39
        Cliente titular = cliente(1L);
        Venta v = venta(10L, titular, List.of(samai), List.of(cliente(2L), cliente(3L)));

        servicio.asignarPorVenta(v);

        assertEquals(40, numeroDe(1L, samai));
        assertEquals(40, numeroDe(2L, samai));
        assertEquals(40, numeroDe(3L, samai));
    }

    @Test
    void quienYaTieneNumeroLoConservaAunqueCompreOtraVezOSeaTitular() {
        guardados.add(new ClienteNumero(cliente(1L), samai, 40)); // fue copropietario: S-40
        guardados.add(new ClienteNumero(cliente(5L), samai, 12)); // su copropietario ya tenía S-12
        Venta v = venta(11L, cliente(1L), List.of(samai), List.of(cliente(5L)));

        servicio.asignarPorVenta(v);

        assertEquals(40, numeroDe(1L, samai));
        assertEquals(12, numeroDe(5L, samai));
        assertEquals(2, guardados.size()); // no se creó nada nuevo
    }

    @Test
    void unaVentaMixtaDaNumeroEnCadaDesarrolloYOtroNoNumera() {
        Venta mixta = venta(12L, cliente(1L), List.of(samai, nanuu, otro), List.of());

        servicio.asignarPorVenta(mixta);

        assertEquals(1, numeroDe(1L, samai));
        assertEquals(1, numeroDe(1L, nanuu));
        assertTrue(guardados.stream().noneMatch(n -> n.getDesarrollo() == otro));
    }

    @Test
    void soloOtroNoDaNumero() {
        servicio.asignarPorVenta(venta(13L, cliente(1L), List.of(otro), List.of(cliente(2L))));
        assertTrue(guardados.isEmpty());
    }

    @Test
    void prefijosYBusqueda() {
        assertEquals("S", ClienteNumeracionService.prefijo("SAMAI Campestre"));
        assertEquals("N", ClienteNumeracionService.prefijo("Aldea Nanuu"));
        assertNull(ClienteNumeracionService.prefijo("Otro"));

        List<NumeroClienteDto> n = List.of(
                new NumeroClienteDto(1L, "SAMAI Campestre", "S", 40, "S-40"),
                new NumeroClienteDto(2L, "Aldea Nanuu", "N", 12, "N-12"));
        assertTrue(ClienteNumeracionService.coincide(n, "S-40"));
        assertTrue(ClienteNumeracionService.coincide(n, "s40"));
        assertTrue(ClienteNumeracionService.coincide(n, "n 12"));
        assertTrue(ClienteNumeracionService.coincide(n, "40"));
        assertFalse(ClienteNumeracionService.coincide(n, "N-40"));
        assertFalse(ClienteNumeracionService.coincide(n, "Laura"));
        assertFalse(ClienteNumeracionService.coincide(List.of(), "40"));
    }

    // ---------------------------------------------------------------- utilidades

    private long numeroDe(Long clienteId, Desarrollo d) {
        Optional<ClienteNumero> n = guardados.stream()
                .filter(x -> x.getCliente().getId().equals(clienteId) && x.getDesarrollo() == d)
                .findFirst();
        return n.map(ClienteNumero::getNumero).orElse(-1L);
    }

    private static Desarrollo desarrollo(Long id, String nombre) {
        Desarrollo d = mock(Desarrollo.class);
        when(d.getId()).thenReturn(id);
        when(d.getNombre()).thenReturn(nombre);
        return d;
    }

    private static Cliente cliente(Long id) {
        Cliente c = mock(Cliente.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Venta venta(Long id, Cliente titular, List<Desarrollo> desarrollos, List<Cliente> copros) {
        Venta v = mock(Venta.class);
        when(v.getId()).thenReturn(id);
        when(v.getClienteRef()).thenReturn(titular);
        List<VentaLote> vls = new ArrayList<>();
        for (Desarrollo d : desarrollos) {
            Lote l = mock(Lote.class);
            when(l.getDesarrollo()).thenReturn(d);
            VentaLote vl = mock(VentaLote.class);
            when(vl.getLote()).thenReturn(l);
            vls.add(vl);
        }
        when(lotes.findByVentaId(id)).thenReturn(vls);
        List<VentaCopropietario> cps = new ArrayList<>();
        for (Cliente c : copros) {
            VentaCopropietario cp = mock(VentaCopropietario.class);
            when(cp.getCliente()).thenReturn(c);
            cps.add(cp);
        }
        when(copropietarios.findByVentaIdOrderByOrdenAsc(id)).thenReturn(cps);
        return v;
    }
}
