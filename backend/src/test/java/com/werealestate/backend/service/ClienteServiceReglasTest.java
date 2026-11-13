package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.exception.ClienteDuplicadoException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Reglas de validación y de vinculación de ClienteService que fijan su comportamiento actual (mayoría de edad,
 * formato de CURP y RFC, país y estado de la lista, anti-duplicado y el cliente del registro de venta). */
class ClienteServiceReglasTest {

    private static final String CURP = "GOMA800101HOCRRN09";
    private final List<Cliente> clientes = new ArrayList<>();
    private ClienteRepository repo;
    private ClienteService service;

    @BeforeEach
    void preparar() {
        repo = mock(ClienteRepository.class);
        when(repo.findAll()).thenReturn(clientes);
        when(repo.maxNumero()).thenReturn(40L);
        when(repo.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        });
        when(repo.findByCurpIgnoreCase(any())).thenAnswer(inv -> clientes.stream()
                .filter(c -> c.getCurp() != null && c.getCurp().equalsIgnoreCase(inv.getArgument(0))).findFirst());
        CurrentUserProvider provider = mock(CurrentUserProvider.class);
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn(Role.ADMIN);
        when(provider.getUsuarioActual()).thenReturn(admin);
        service = new ClienteService(repo, mock(VentaRepository.class), mock(VentaLoteRepository.class),
                mock(VentaCopropietarioRepository.class), mock(PagoVentaRepository.class),
                mock(VentaAportacionRepository.class), mock(VentaComisionRepository.class), mock(UsuarioRepository.class),
                mock(AsesorExternoRepository.class), provider, mock(ClienteNumeracionService.class));
    }

    private static ClienteRequest peticion(String curp, String rfc, LocalDate nacimiento, String pais, String estado) {
        return new ClienteRequest(" Ana ", " Gómez ", " Pérez ", nacimiento, " 9511234567 ", null, "ana@correo.com", curp,
                rfc, null, null, null, null, "Calle 1", "Centro", "Oaxaca", estado, "68000", null, null, null, null, null,
                null, null, null, null, null, pais);
    }

    private static ClienteRequest valida() {
        return peticion(CURP, null, LocalDate.of(1980, 1, 1), null, null);
    }

    private Cliente existente(String nombre, String paterno, LocalDate nacimiento, String telefono, String curp) {
        Cliente c = new Cliente(nombre, paterno, "Pérez");
        ReflectionTestUtils.setField(c, "id", 100L + clientes.size());
        ReflectionTestUtils.setField(c, "activo", true);
        c.actualizarDatos(nombre, paterno, "Pérez", nacimiento, telefono, null, null, curp, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        clientes.add(c);
        return c;
    }

    private String mensaje(Runnable r) {
        return assertThrows(ValidationException.class, r::run).getMessage();
    }

    @Test
    void crearLimpiaTextosYAsignaElSiguienteNumero() {
        var dto = service.crear(valida());

        assertEquals("Ana", dto.nombre());
        assertEquals("Gómez", dto.apellidoPaterno());
        assertEquals("Pérez", dto.apellidoMaterno());
        assertEquals(CURP, dto.curp());
    }

    @Test
    void menorDeEdadSeRechaza() {
        assertEquals("El cliente debe ser mayor de edad", mensaje(() ->
                service.crear(peticion(CURP, null, LocalDate.now().minusYears(17), null, null))));
    }

    @Test
    void curpYRfcDebenTenerFormatoValido() {
        assertEquals("El CURP no tiene un formato válido (18 caracteres)", mensaje(() ->
                service.crear(peticion("CURP-MALA", null, LocalDate.of(1980, 1, 1), null, null))));
        assertEquals("El RFC no tiene un formato válido", mensaje(() ->
                service.crear(peticion(CURP, "XX", LocalDate.of(1980, 1, 1), null, null))));
    }

    @Test
    void curpYRfcSeGuardanEnMayusculas() {
        var dto = service.crear(peticion(CURP.toLowerCase(), "goma800101ab1", LocalDate.of(1980, 1, 1), null, null));

        assertEquals(CURP, dto.curp());
        assertEquals("GOMA800101AB1", dto.rfc());
    }

    @Test
    void paisYEstadoDebenSerDeLaLista() {
        assertEquals("Elige un país de la lista", mensaje(() ->
                service.crear(peticion(CURP, null, LocalDate.of(1980, 1, 1), "Narnia", null))));
        assertEquals("Elige un estado de la lista", mensaje(() ->
                service.crear(peticion(CURP, null, LocalDate.of(1980, 1, 1), "México", "Estado inventado"))));
        assertEquals("Oaxaca", service.crear(peticion(CURP, null, LocalDate.of(1980, 1, 1), "México", "oaxaca")).estado());
        // Con otro país el estado es texto libre.
        assertEquals("Texas", service.crear(peticion(null, null, LocalDate.of(1980, 1, 1), "Estados Unidos", "Texas")).estado());
    }

    @Test
    void duplicadoPorCurpNombreYNacimientoOTelefono() {
        Cliente otro = existente("Beto", "Ruiz", LocalDate.of(1990, 5, 5), "951 123 4567", CURP);

        var porCurp = assertThrows(ClienteDuplicadoException.class, () -> service.crear(valida()));
        assertEquals("Ya existe un cliente con ese CURP: " + otro.nombreCompleto(), porCurp.getMessage());

        var porTelefono = assertThrows(ClienteDuplicadoException.class, () -> service.crear(
                peticion("PERE800101HOCRRN09", null, LocalDate.of(1980, 1, 1), null, null)));
        assertEquals("Ya existe un cliente con ese teléfono: " + otro.nombreCompleto(), porTelefono.getMessage());

        clientes.clear();
        Cliente mismoNombre = existente("ana", "GOMEZ", LocalDate.of(1980, 1, 1), "9990000000", null);
        var porNombre = assertThrows(ClienteDuplicadoException.class, () -> service.crear(
                peticion(null, null, LocalDate.of(1980, 1, 1), null, null)));
        assertEquals("Ya existe un cliente con ese nombre y fecha de nacimiento: " + mismoNombre.nombreCompleto(),
                porNombre.getMessage());
    }

    @Test
    void registroDeVentaExigeLosDatosBasicos() {
        assertEquals("Faltan datos del cliente: nombre, apellido paterno, fecha de nacimiento, CURP y teléfono",
                mensaje(() -> service.resolverDesdeRegistroDeVenta(peticion(null, null, LocalDate.of(1980, 1, 1), null, null),
                        null, null)));
        assertEquals("El cliente debe ser mayor de edad", mensaje(() -> service.resolverDesdeRegistroDeVenta(
                peticion(CURP, null, LocalDate.now().minusYears(10), null, null), null, null)));
        assertEquals("El CURP no tiene un formato válido (18 caracteres)", mensaje(() ->
                service.resolverDesdeRegistroDeVenta(peticion("MALA", null, LocalDate.of(1980, 1, 1), null, null), null, null)));
        assertEquals("El RFC no tiene un formato válido", mensaje(() -> service.resolverDesdeRegistroDeVenta(
                peticion(CURP, "mal", LocalDate.of(1980, 1, 1), null, null), null, null)));
    }

    @Test
    void registroDeVentaCreaClienteNuevoConElSiguienteNumero() {
        var resuelto = service.resolverDesdeRegistroDeVenta(valida(), null, null);

        assertTrue(resuelto.nuevo());
        assertEquals(41L, resuelto.cliente().getNumero());
        assertEquals(CURP, resuelto.cliente().getCurp());
        assertEquals("9511234567", resuelto.cliente().getTelefono());
    }

    @Test
    void registroDeVentaVinculaPorCurpYSoloRellenaLoVacio() {
        Cliente c = existente("Ana", "Gómez", LocalDate.of(1980, 1, 1), "9510000000", CURP);

        var resuelto = service.resolverDesdeRegistroDeVenta(
                peticion(CURP.toLowerCase(), null, LocalDate.of(1980, 1, 1), "México", "Oaxaca"), null, null);

        assertFalse(resuelto.nuevo());
        assertEquals(c, resuelto.cliente());
        assertEquals("9510000000", c.getTelefono()); // lo que ya tenía no se cambia
        assertEquals("ana@correo.com", c.getCorreo()); // lo vacío se completa
        assertEquals("Oaxaca", c.getEstado());
        assertEquals("México", c.getPaisResidencia());
    }

    @Test
    void registroDeVentaConCurpDeClienteInactivoSeRechaza() {
        Cliente c = existente("Ana", "Gómez", LocalDate.of(1980, 1, 1), "9510000000", CURP);
        c.setActivo(false);

        assertEquals("Ya existe un cliente con ese CURP pero está inactivo: comunícate con administración", mensaje(() ->
                service.resolverDesdeRegistroDeVenta(valida(), null, null)));
    }

    @Test
    void normalizarYDesdeTexto() {
        assertEquals("jose maria", ClienteService.normalizar("  José   María "));
        assertEquals("", ClienteService.normalizar(null));
        Cliente c = ClienteService.desdeTexto("Norma Yaneli Yañez Miranda");
        assertEquals("Norma Yaneli", c.getNombre());
        assertEquals("Yañez", c.getApellidoPaterno());
        assertEquals("Miranda", c.getApellidoMaterno());
        assertNull(ClienteService.desdeTexto("Cher").getApellidoPaterno());
    }
}
