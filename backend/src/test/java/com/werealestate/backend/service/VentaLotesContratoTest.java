package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ContratoRequest;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaEliminarRequest;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MedioFirma;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.ReciboVentaHistorialRepository;
import com.werealestate.backend.repository.ReciboVentaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Lo que la venta le hace a sus lotes: al registrarla (según el contrato), al firmar o quitar la firma del contrato,
 * y al eliminarla. Un lote no puede quedar en dos ventas. */
class VentaLotesContratoTest {

    private VentaRepository ventas;
    private VentaLoteRepository ventaLotes;
    private LoteRepository lotes;
    private LoteService loteService;
    private UsuarioRepository usuarios;
    private ClienteService clientes;
    private PasswordEncoder passwordEncoder;
    private VentaService service;
    private final Venta[] guardada = new Venta[1];

    @BeforeEach
    void preparar() {
        ventas = mock(VentaRepository.class);
        ventaLotes = mock(VentaLoteRepository.class);
        lotes = mock(LoteRepository.class);
        loteService = mock(LoteService.class);
        usuarios = mock(UsuarioRepository.class);
        clientes = mock(ClienteService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario actual = mock(Usuario.class);
        when(actual.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(actual);
        when(ventas.save(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            if (v.getId() == null) ReflectionTestUtils.setField(v, "id", 50L);
            guardada[0] = v;
            return v;
        });
        when(ventas.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(guardada[0]));
        service = new VentaService(ventas, ventaLotes, mock(VentaAportacionRepository.class), lotes, loteService,
                mock(PagoVentaRepository.class), mock(ReciboVentaRepository.class), mock(ReciboVentaHistorialRepository.class),
                mock(ComisionService.class), clientes, mock(ClienteNumeracionService.class),
                mock(VentaCopropietarioRepository.class), mock(WeFormRespuestaRepository.class), passwordEncoder, usuarios,
                mock(AsesorExternoRepository.class), provider);
    }

    private Lote lote(long id) {
        Desarrollo desarrollo = mock(Desarrollo.class);
        when(desarrollo.getPrecioM2()).thenReturn(new BigDecimal("100"));
        Lote lote = new Lote(desarrollo, "2", String.valueOf(id), new BigDecimal("200"));
        ReflectionTestUtils.setField(lote, "id", id);
        when(lotes.findById(id)).thenReturn(Optional.of(lote));
        return lote;
    }

    /** Una venta ya guardada, con su asesor, sus lotes y su contrato. */
    private Venta venta(long id, long numero, boolean firmada, Lote... suyos) {
        Usuario asesor = mock(Usuario.class);
        when(asesor.getNombre()).thenReturn("Asesor Uno");
        Venta v = new Venta("Cliente Uno", asesor, null, "Contado", LocalDate.of(2026, 10, 1),
                null, null, null, null, 1, false, null);
        ReflectionTestUtils.setField(v, "id", id);
        v.setNumero(numero);
        if (firmada) v.registrarContrato(true, LocalDate.of(2026, 10, 2), MedioFirma.FISICO, null);
        guardada[0] = v;
        List<VentaLote> filas = java.util.Arrays.stream(suyos).map(l -> new VentaLote(v, l, new BigDecimal("20000"))).toList();
        when(ventaLotes.findByVentaId(id)).thenReturn(filas);
        for (VentaLote vl : filas) {
            when(ventaLotes.findByLoteId(vl.getLote().getId())).thenReturn(List.of(vl));
        }
        return v;
    }

    private static ContratoRequest contrato(boolean firmado) {
        return firmado
                ? new ContratoRequest(true, LocalDate.of(2026, 10, 20), MedioFirma.DOCUSIGN, null)
                : new ContratoRequest(false, null, null, null);
    }

    @Test
    void alFirmarElContratoLosLotesPasanAVendido() {
        venta(10, 1, false, lote(1), lote(2));

        service.registrarContrato(10L, contrato(true));

        verify(loteService).ponerEstadoDeVenta(1L, EstadoLote.VENDIDO, "Cliente Uno", "Contrato firmado");
        verify(loteService).ponerEstadoDeVenta(2L, EstadoLote.VENDIDO, "Cliente Uno", "Contrato firmado");
    }

    @Test
    void alQuitarLaFirmaRegresanAEnProcesoDeFirma() {
        venta(10, 1, true, lote(1));

        service.registrarContrato(10L, contrato(false));

        verify(loteService).ponerEstadoDeVenta(
                1L, EstadoLote.EN_PROCESO_DE_FIRMA, "Cliente Uno", "Se quitó la firma del contrato");
    }

    @Test
    void corregirSoloLaFechaNoMueveLosLotes() {
        venta(10, 1, true, lote(1));

        service.registrarContrato(10L, contrato(true));

        verify(loteService, never()).ponerEstadoDeVenta(anyLong(), any(), any(), any());
    }

    @Test
    void siOtraVentaFirmadaTieneElLoteSigueVendido() {
        Lote compartido = lote(1);
        Venta esta = venta(10, 1, true, compartido);
        Usuario asesor = mock(Usuario.class);
        Venta otra = new Venta("Otro", asesor, null, "Contado", LocalDate.of(2026, 9, 1), null, null, null, null, 1, false, null);
        otra.registrarContrato(true, LocalDate.of(2026, 9, 2), MedioFirma.FISICO, null);
        when(ventaLotes.findByLoteId(1L)).thenReturn(List.of(
                new VentaLote(esta, compartido, BigDecimal.TEN), new VentaLote(otra, compartido, BigDecimal.TEN)));

        service.registrarContrato(10L, contrato(false));

        verify(loteService).ponerEstadoDeVenta(1L, EstadoLote.VENDIDO, "Cliente Uno", "Se quitó la firma del contrato");
    }

    @Test
    void alEliminarLaVentaSusLotesSiempreSeLiberan() {
        venta(10, 4, false, lote(1), lote(2));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(ventaLotes.existsByLoteId(1L)).thenReturn(false);
        when(ventaLotes.existsByLoteId(2L)).thenReturn(true);

        service.eliminar(10L, new VentaEliminarRequest("secreta"));

        verify(loteService).liberarPorVentaEliminada(1L);
        verify(loteService, never()).liberarPorVentaEliminada(2L);
    }

    @Test
    void unLoteQueYaEstaEnOtraVentaNoSeVuelveAVender() {
        Lote lote = lote(1);
        Venta otra = venta(9, 3, true, lote);
        when(ventaLotes.findFirstByLoteIdOrderByIdDesc(1L)).thenReturn(Optional.of(new VentaLote(otra, lote, BigDecimal.TEN)));

        ConflictException e = assertThrows(ConflictException.class, () -> service.crear(nueva(false)));
        assertTrue(e.getMessage().contains("ya está en la venta #3"));
        verify(ventaLotes, never()).save(any());
    }

    @Test
    void laVentaNuevaDejaLosLotesSegunElContrato() {
        lote(1);
        Usuario asesor = mock(Usuario.class);
        when(asesor.isActivo()).thenReturn(true);
        when(asesor.getNombre()).thenReturn("Asesor Uno");
        when(usuarios.findById(7L)).thenReturn(Optional.of(asesor));
        Cliente cliente = new Cliente("Ana", "López", null);
        ReflectionTestUtils.setField(cliente, "id", 30L);
        when(clientes.obtenerEntidad(30L)).thenReturn(cliente);

        service.crear(nueva(false));
        verify(loteService).marcarPorVenta(eq(1L), eq(false), any(), eq("Asesor Uno"));

        service.crear(nueva(true));
        verify(loteService).marcarPorVenta(eq(1L), eq(true), any(), eq("Asesor Uno"));
    }

    private static VentaCreateRequest nueva(boolean contratoFirmado) {
        return new VentaCreateRequest(
                List.of(new VentaLoteItemRequest(1L, new BigDecimal("20000"))), 30L, null, List.of(), 7L, null,
                "Contado", LocalDate.of(2026, 10, 1), null, null, null, null, null, false, null, List.of(),
                contratoFirmado, contratoFirmado ? LocalDate.of(2026, 10, 1) : null,
                contratoFirmado ? MedioFirma.FISICO : null, null, null, null, null, null, null);
    }
}
