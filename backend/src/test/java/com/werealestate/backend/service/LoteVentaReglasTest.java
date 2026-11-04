package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** El estado de un lote con venta lo manda la venta: a mano no se saca de Vendido / En proceso de firma, y a esos
 * dos estados solo se llega registrando la venta. */
class LoteVentaReglasTest {

    private LoteRepository lotes;
    private MovimientoLoteRepository movimientos;
    private VentaLoteRepository ventaLotes;
    private UsuarioRepository usuarios;
    private Usuario actual;
    private LoteService service;
    private final List<Object[]> numeros = new ArrayList<>();

    @BeforeEach
    void preparar() {
        lotes = mock(LoteRepository.class);
        movimientos = mock(MovimientoLoteRepository.class);
        ventaLotes = mock(VentaLoteRepository.class);
        usuarios = mock(UsuarioRepository.class);
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        actual = mock(Usuario.class);
        when(actual.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(actual);
        when(ventaLotes.numeroDeVentaPorLote(any())).thenReturn(numeros);
        service = new LoteService(lotes, mock(DesarrolloRepository.class), usuarios, mock(AsesorExternoRepository.class),
                movimientos, ventaLotes, provider, mock(PasswordEncoder.class));
    }

    private Lote lote(long id, EstadoLote estado) {
        Desarrollo desarrollo = mock(Desarrollo.class);
        when(desarrollo.getPrecioM2()).thenReturn(new BigDecimal("100"));
        Lote lote = new Lote(desarrollo, "1", String.valueOf(id), new BigDecimal("200"));
        ReflectionTestUtils.setField(lote, "id", id);
        ReflectionTestUtils.setField(lote, "estado", estado);
        when(lotes.findById(id)).thenReturn(Optional.of(lote));
        return lote;
    }

    /** El lote queda en la venta con ese número. */
    private void enVenta(Lote lote, long numeroVenta) {
        Venta venta = mock(Venta.class);
        when(venta.getNumero()).thenReturn(numeroVenta);
        VentaLote vl = new VentaLote(venta, lote, new BigDecimal("1000"));
        when(ventaLotes.findFirstByLoteIdOrderByIdDesc(lote.getId())).thenReturn(Optional.of(vl));
        when(ventaLotes.existsByLoteId(lote.getId())).thenReturn(true);
        numeros.add(new Object[] {lote.getId(), numeroVenta});
    }

    private static CambiarEstadoLoteRequest a(EstadoLote estado) {
        return new CambiarEstadoLoteRequest(estado, null, "Motivo", "Cliente", null, null, null);
    }

    @Test
    void unLoteVendidoConVentaNoSeLiberaAMano() {
        Lote lote = lote(1, EstadoLote.VENDIDO);
        enVenta(lote, 7);

        ConflictException e = assertThrows(ConflictException.class, () -> service.cambiarEstado(1L, a(EstadoLote.DISPONIBLE)));
        assertTrue(e.getMessage().contains("venta #7"));
        assertEquals(EstadoLote.VENDIDO, lote.getEstado());
        verify(movimientos, never()).save(any());
    }

    @Test
    void niSeMueveEntreEnFirmaYVendido() {
        Lote lote = lote(1, EstadoLote.EN_PROCESO_DE_FIRMA);
        enVenta(lote, 3);

        assertThrows(ConflictException.class, () -> service.cambiarEstado(1L, a(EstadoLote.VENDIDO)));
        assertThrows(ConflictException.class, () -> service.cambiarEstado(1L, a(EstadoLote.RESERVADO_WE)));
        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, lote.getEstado());
    }

    @Test
    void unLoteConVentaQueQuedoDisponibleSoloVuelveAUnEstadoDeVenta() {
        Lote lote = lote(1, EstadoLote.DISPONIBLE);
        enVenta(lote, 4);

        assertThrows(ConflictException.class, () -> service.cambiarEstado(1L, a(EstadoLote.APARTADO)));
        LoteDto dto = service.cambiarEstado(1L, a(EstadoLote.VENDIDO));
        assertEquals(EstadoLote.VENDIDO, lote.getEstado());
        assertEquals(4L, dto.ventaNumero());
    }

    @Test
    void sinVentaNoSeMarcaVendidoNiEnFirmaAMano() {
        Lote lote = lote(1, EstadoLote.DISPONIBLE);

        ValidationException e = assertThrows(ValidationException.class, () -> service.cambiarEstado(1L, a(EstadoLote.VENDIDO)));
        assertTrue(e.getMessage().contains("registra su venta"));
        assertThrows(ValidationException.class, () -> service.cambiarEstado(1L, a(EstadoLote.EN_PROCESO_DE_FIRMA)));
        assertEquals(EstadoLote.DISPONIBLE, lote.getEstado());
    }

    @Test
    void sinVentaLosDemasEstadosSiguenIgual() {
        Lote lote = lote(1, EstadoLote.VENDIDO);

        LoteDto dto = service.cambiarEstado(1L, a(EstadoLote.RESERVADO_WE));
        assertEquals(EstadoLote.RESERVADO_WE, lote.getEstado());
        assertNull(dto.ventaNumero());
        verify(movimientos).save(any());
    }

    @Test
    void unAsesorNoVeElNumeroDeLaVenta() {
        when(actual.getRol()).thenReturn(Role.ASESOR);
        Lote lote = lote(1, EstadoLote.DISPONIBLE);
        enVenta(lote, 9);

        ConflictException e = assertThrows(ConflictException.class, () -> service.cambiarEstado(1L, a(EstadoLote.APARTADO)));
        assertTrue(e.getMessage().contains("pide a administración"));
    }

    @Test
    void elPublicoNoTocaUnLoteConVenta() {
        Lote lote = lote(1, EstadoLote.DISPONIBLE);
        enVenta(lote, 2);

        assertThrows(ForbiddenOperationException.class, () -> service.cambiarEstadoPublico(
                1L, new CambiarEstadoLotePublicoRequest(EstadoLote.APARTADO, "Asesor", "Cliente", null)));
        assertEquals(EstadoLote.DISPONIBLE, lote.getEstado());
    }

    @Test
    void alEliminarLaVentaTambienSeLiberaElEnFirma() {
        Lote lote = lote(1, EstadoLote.EN_PROCESO_DE_FIRMA);
        lote.setMontoApartado(new BigDecimal("5000"));

        service.liberarPorVentaEliminada(1L);

        assertEquals(EstadoLote.DISPONIBLE, lote.getEstado());
        assertNull(lote.getMontoApartado());
    }

    @Test
    void alEliminarLaVentaUnLoteEnOtroEstadoSeQuedaIgual() {
        Lote lote = lote(1, EstadoLote.RESERVADO_WE);

        service.liberarPorVentaEliminada(1L);

        assertEquals(EstadoLote.RESERVADO_WE, lote.getEstado());
        verify(movimientos, never()).save(any());
    }

    @Test
    void laVentaDelPanelPoneElEstadoSegunElContrato() {
        Lote sinFirmar = lote(1, EstadoLote.APARTADO_CON_DINERO);
        sinFirmar.setMontoApartado(new BigDecimal("3000"));
        Lote firmado = lote(2, EstadoLote.DISPONIBLE);

        service.marcarPorVenta(1L, false, "Cliente", "Asesor");
        service.marcarPorVenta(2L, true, "Cliente", "Asesor");

        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, sinFirmar.getEstado());
        assertNull(sinFirmar.getMontoApartado());
        assertEquals(EstadoLote.VENDIDO, firmado.getEstado());
    }

    @Test
    void elContratoSoloDejaMovimientoSiCambiaElEstado() {
        Lote lote = lote(1, EstadoLote.VENDIDO);

        service.ponerEstadoDeVenta(1L, EstadoLote.VENDIDO, "Cliente", "Contrato firmado");
        verify(movimientos, never()).save(any());

        service.ponerEstadoDeVenta(1L, EstadoLote.EN_PROCESO_DE_FIRMA, "Cliente", "Se quitó la firma del contrato");
        assertEquals(EstadoLote.EN_PROCESO_DE_FIRMA, lote.getEstado());
        verify(movimientos).save(any());
    }

    @Test
    void elPlanoMarcaEnQueVentaEstaCadaLote() {
        Lote vendido = lote(1, EstadoLote.VENDIDO);
        enVenta(vendido, 5);
        Lote reservado = lote(2, EstadoLote.VENDIDO);
        Lote libre = lote(3, EstadoLote.DISPONIBLE);
        when(lotes.findByDesarrolloId(10L)).thenReturn(List.of(vendido, reservado, libre));
        DesarrolloRepository desarrollos = (DesarrolloRepository) ReflectionTestUtils.getField(service, "desarrolloRepository");
        when(desarrollos.findById(10L)).thenReturn(Optional.of(mock(Desarrollo.class)));

        List<LoteDto> plano = service.obtenerMapa(10L).lotes();

        assertEquals(5L, plano.get(0).ventaNumero());
        assertTrue(!plano.get(0).sinVentaRegistrada());
        assertNull(plano.get(1).ventaNumero());
        assertTrue(plano.get(1).sinVentaRegistrada());
        assertNull(plano.get(2).ventaNumero());
    }
}
