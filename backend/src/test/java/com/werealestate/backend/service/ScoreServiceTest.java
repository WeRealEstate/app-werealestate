package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ScoreDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Score de ventas: quién suma qué, en qué orden y quién no aparece. */
class ScoreServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private VentaRepository ventas;
    private VentaLoteRepository lotes;
    private VentaComisionRepository comisiones;
    private Usuario actual;
    private ScoreService service;
    private final List<Object[]> lotesPorVenta = new ArrayList<>();
    private final List<VentaComision> copropietarios = new ArrayList<>();

    @BeforeEach
    void preparar() {
        ventas = mock(VentaRepository.class);
        lotes = mock(VentaLoteRepository.class);
        comisiones = mock(VentaComisionRepository.class);
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        actual = mock(Usuario.class);
        when(actual.getRol()).thenReturn(Role.LIDER_AREA);
        when(provider.getUsuarioActual()).thenReturn(actual);
        when(lotes.contarLotesPorVenta(any())).thenReturn(lotesPorVenta);
        when(comisiones.findByVentaIdInAndSecundariaTrueAndCanceladaFalse(any())).thenReturn(copropietarios);
        service = new ScoreService(ventas, lotes, comisiones, provider);
    }

    private static Usuario interno(long id, String nombre, boolean activo) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id);
        when(u.getNombre()).thenReturn(nombre);
        when(u.isActivo()).thenReturn(activo);
        return u;
    }

    private static AsesorExterno externo(long id, String nombre) {
        AsesorExterno a = new AsesorExterno(nombre, "951000000" + id, null);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private Venta venta(long id, Usuario usuario, AsesorExterno externo, long numLotes) {
        Venta v = mock(Venta.class);
        when(v.getId()).thenReturn(id);
        when(v.getUsuarioAsesor()).thenReturn(usuario);
        when(v.getAsesorExterno()).thenReturn(externo);
        lotesPorVenta.add(new Object[] {id, numLotes});
        return v;
    }

    private void copropietario(Venta venta, Usuario usuario, AsesorExterno externo) {
        VentaComision c = mock(VentaComision.class);
        when(c.getVenta()).thenReturn(venta);
        when(c.getUsuarioAsesor()).thenReturn(usuario);
        when(c.getAsesorExterno()).thenReturn(externo);
        copropietarios.add(c);
    }

    private ScoreDtos.Ranking conVentas(Venta... lista) {
        when(ventas.findByFechaVentaBetween(DESDE, HASTA)).thenReturn(List.of(lista));
        return service.ranking(DESDE, HASTA);
    }

    @Test
    void cuentaVentasYLotesPorAsesorDeMasAMenosVentas() {
        Usuario ana = interno(1, "Ana Interna", true);
        AsesorExterno beto = externo(5, "Beto Externo");
        var r = conVentas(venta(10, null, beto, 3), venta(11, ana, null, 2), venta(12, ana, null, 1));

        assertEquals(3, r.totalVentas());
        assertEquals(2, r.asesores().size());
        assertEquals(new ScoreDtos.Asesor("U-1", "Ana Interna", ScoreDtos.TipoAsesor.INTERNO, 2, 3), r.asesores().get(0));
        assertEquals(new ScoreDtos.Asesor("E-5", "Beto Externo", ScoreDtos.TipoAsesor.EXTERNO, 1, 3), r.asesores().get(1));
    }

    @Test
    void unaVentaCompartidaCuentaCompletaParaLosDos() {
        Usuario ana = interno(1, "Ana Interna", true);
        AsesorExterno carla = externo(6, "Carla Copropietaria");
        Venta compartida = venta(10, ana, null, 2);
        copropietario(compartida, null, carla);

        var r = conVentas(compartida);
        assertEquals(1, r.totalVentas());
        assertEquals(2, r.asesores().size());
        for (ScoreDtos.Asesor a : r.asesores()) {
            assertEquals(1, a.ventas(), a.nombre());
            assertEquals(2, a.lotes(), a.nombre());
        }
    }

    @Test
    void losInactivosNoAparecenPeroSuVentaSigueEnElTotal() {
        Usuario baja = interno(2, "Dado de Baja", false);
        AsesorExterno inactivo = externo(7, "Externo Inactivo");
        inactivo.setActivo(false);
        AsesorExterno eva = externo(8, "Eva Activa");
        Venta compartida = venta(10, baja, null, 1);
        copropietario(compartida, null, eva);

        var r = conVentas(compartida, venta(11, null, inactivo, 1));
        assertEquals(2, r.totalVentas());
        assertEquals(List.of("Eva Activa"), r.asesores().stream().map(ScoreDtos.Asesor::nombre).toList());
    }

    @Test
    void conLasMismasVentasSeOrdenanPorNombreSinImportarAcentos() {
        var r = conVentas(
                venta(10, interno(3, "Beto", true), null, 5),
                venta(11, interno(4, "Álvaro", true), null, 1),
                venta(12, interno(9, "Zoe", true), null, 1),
                venta(13, interno(9, "Zoe", true), null, 1));
        assertEquals(List.of("Zoe", "Álvaro", "Beto"), r.asesores().stream().map(ScoreDtos.Asesor::nombre).toList());
    }

    @Test
    void laFichaDeComunidadDeUnInternoSumaEnLaFilaDeSuUsuario() {
        AsesorExterno ficha = externo(20, "Ana Interna");
        ficha.setUsuarioId(1L);
        var r = conVentas(venta(10, interno(1, "Ana Interna", true), null, 1), venta(11, null, ficha, 1));
        assertEquals(1, r.asesores().size());
        assertEquals(new ScoreDtos.Asesor("U-1", "Ana Interna", ScoreDtos.TipoAsesor.INTERNO, 2, 2), r.asesores().get(0));
    }

    @Test
    void elMismoAsesorNoSumaDosVecesUnaVenta() {
        Usuario ana = interno(1, "Ana Interna", true);
        Venta v = venta(10, ana, null, 1);
        copropietario(v, ana, null);
        var r = conVentas(v);
        assertEquals(1, r.asesores().get(0).ventas());
    }

    @Test
    void sinVentasNoConsultaLotesNiComisiones() {
        when(ventas.findByFechaVentaBetween(DESDE, HASTA)).thenReturn(List.of());
        var r = service.ranking(DESDE, HASTA);
        assertEquals(0, r.totalVentas());
        assertTrue(r.asesores().isEmpty());
        verify(lotes, never()).contarLotesPorVenta(any());
        verify(comisiones, never()).findByVentaIdInAndSecundariaTrueAndCanceladaFalse(any());
    }

    @Test
    void sinFechasEsTodoElHistorial() {
        when(ventas.findByFechaVentaBetween(LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31))).thenReturn(List.of());
        var r = service.ranking(null, null);
        assertEquals(0, r.totalVentas());
        verify(ventas).findByFechaVentaBetween(LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31));
    }

    @Test
    void rechazaUnRangoAlReves() {
        assertThrows(ValidationException.class, () -> service.ranking(HASTA, DESDE));
    }

    @Test
    void soloAdminYAdministracion() {
        when(actual.getRol()).thenReturn(Role.ASESOR);
        assertThrows(ForbiddenOperationException.class, () -> service.ranking(DESDE, HASTA));
        when(actual.getRol()).thenReturn(Role.EQUIPO_INTERNO);
        assertThrows(ForbiddenOperationException.class, () -> service.ranking(DESDE, HASTA));
        when(actual.getRol()).thenReturn(Role.ADMIN);
        when(ventas.findByFechaVentaBetween(DESDE, HASTA)).thenReturn(List.of());
        service.ranking(DESDE, HASTA);
    }
}
