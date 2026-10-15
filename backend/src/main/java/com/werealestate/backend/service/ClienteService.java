package com.werealestate.backend.service;

import com.werealestate.backend.dto.ClienteDto;
import com.werealestate.backend.dto.ClienteListaDto;
import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.dto.NumeracionClienteDto;
import com.werealestate.backend.exception.ClienteDuplicadoException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.FuenteCliente;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.EstadosMexico;
import com.werealestate.backend.model.Paises;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.UbicacionDocumento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clientes: se registran una vez y se ligan a todas sus ventas. Los gestionan un admin y el personal
 * de administración (líder de área) con el módulo activo. Las ventas anteriores a este módulo se
 * ligan solas al arrancar: un cliente por cada nombre distinto, marcado con "datos incompletos".
 */
@Service
@Transactional
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);
    private static final Pattern CURP = Pattern.compile("^[A-Z]{4}\\d{6}[HMX][A-Z]{5}[A-Z0-9]\\d$");
    private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final com.werealestate.backend.repository.VentaCopropietarioRepository copropietarioRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final VentaComisionRepository comisionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsesorExternoRepository asesorExternoRepository;
    private final CurrentUserProvider currentUserProvider;

    public ClienteService(
            ClienteRepository clienteRepository,
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            com.werealestate.backend.repository.VentaCopropietarioRepository copropietarioRepository,
            PagoVentaRepository pagoVentaRepository,
            VentaComisionRepository comisionRepository,
            UsuarioRepository usuarioRepository,
            AsesorExternoRepository asesorExternoRepository,
            CurrentUserProvider currentUserProvider) {
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.copropietarioRepository = copropietarioRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.comisionRepository = comisionRepository;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<ClienteListaDto> listar() {
        exigirAdminOLider();
        Resumenes r = new Resumenes();
        return clienteRepository.findAllByOrderByNombreAscApellidoPaternoAsc().stream().map(r::lista).toList();
    }

    /** Hasta 20 clientes activos que coinciden con el texto (nombre, teléfono o CURP): para elegir el
     * cliente de una venta. Sin texto devuelve los primeros por orden alfabético. */
    @Transactional(readOnly = true)
    public List<ClienteListaDto> buscar(String q) {
        exigirAdminOLider();
        String buscado = normalizar(q);
        Resumenes r = new Resumenes();
        return clienteRepository.findAllByOrderByNombreAscApellidoPaternoAsc().stream()
                .filter(Cliente::isActivo)
                .filter(c -> buscado.isEmpty()
                        || buscado.equals(String.valueOf(c.getNumero()))
                        || normalizar(c.nombreCompleto()).contains(buscado)
                        || (c.getTelefono() != null && c.getTelefono().replaceAll("\\D", "").contains(buscado.replaceAll("\\D", "")) && !buscado.replaceAll("\\D", "").isEmpty())
                        || (c.getCurp() != null && c.getCurp().toLowerCase(Locale.ROOT).contains(buscado)))
                .limit(20)
                .map(r::lista)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteDto obtener(Long id) {
        exigirAdminOLider();
        return detalle(obtenerEntidad(id));
    }

    // ---------------------------------------------------------------- escritura

    public ClienteDto crear(ClienteRequest r) {
        exigirAdminOLider();
        Cliente c = new Cliente(r.nombre().trim(), r.apellidoPaterno().trim(), limpio(r.apellidoMaterno()));
        aplicar(c, r);
        c.setNumero(siguienteNumero());
        return detalle(clienteRepository.save(c));
    }

    /** Cambia el número de un cliente. Si otro ya lo tiene, ese y los consecutivos que le siguen se recorren
     * +1 (se detiene en el primer hueco), igual que el número de venta; el lugar que deja queda libre. */
    public ClienteDto cambiarNumero(Long id, Long numero) {
        exigirAdminOLider();
        if (numero == null || numero < 1) throw new ValidationException("El número de cliente debe ser 1 o mayor");
        Cliente c = obtenerEntidad(id);
        long actual = c.getNumero();
        if (numero == actual) return detalle(c);
        clienteRepository.bloquearNumeracion();
        long hasta = finDeLaRacha(numero, actual);
        if (hasta >= numero) {
            clienteRepository.apartarDeSuLugar(id);
            clienteRepository.negarRango(numero, hasta, id);
            clienteRepository.subirNegados(-hasta, -numero);
        }
        clienteRepository.fijarNumero(id, numero);
        return detalle(obtenerEntidad(id));
    }

    /** Para el aviso previo: el siguiente número automático y, si el consultado lo tiene otro cliente
     * (distinto de excluirId), cuál es y cuántos se recorrerían. */
    @Transactional(readOnly = true)
    public NumeracionClienteDto consultarNumeracion(Long numero, Long excluirId) {
        exigirAdminOLider();
        long siguiente = clienteRepository.maxNumero() + 1;
        if (numero == null || numero < 1) return new NumeracionClienteDto(siguiente, null, null, 0);
        Cliente ocupante = clienteRepository.findByNumero(numero).orElse(null);
        if (ocupante == null || ocupante.getId().equals(excluirId)) {
            return new NumeracionClienteDto(siguiente, null, null, 0);
        }
        Long ignorado = excluirId == null ? null : clienteRepository.findById(excluirId).map(Cliente::getNumero).orElse(null);
        long fin = finDeLaRacha(numero, ignorado == null ? -1L : ignorado);
        return new NumeracionClienteDto(siguiente, ocupante.getId(), ocupante.nombreCompleto(), (int) (fin - numero + 1));
    }

    /** El número de un cliente nuevo: el mayor en uso + 1. Con candado para que dos altas simultáneas no repitan. */
    private long siguienteNumero() {
        clienteRepository.bloquearNumeracion();
        return clienteRepository.maxNumero() + 1;
    }

    /** Último número de la racha de consecutivos que empieza en "desde" (sin contar el que se está moviendo,
     * que se va de su lugar); desde - 1 si "desde" está libre. */
    private long finDeLaRacha(long desde, long numeroIgnorado) {
        java.util.Set<Long> enUso = new java.util.HashSet<>(clienteRepository.numerosDesde(desde));
        enUso.remove(numeroIgnorado);
        long fin = desde - 1;
        while (enUso.contains(fin + 1)) fin++;
        return fin;
    }

    /** El cliente de un registro de venta de We Forms (sin sesión: ya se validó el PIN del asesor). La CURP manda:
     * si ya existe un cliente con esa CURP es la misma persona y se vincula SIN tocar sus datos (solo se
     * rellenan los campos que tenga vacíos); si no, se crea uno nuevo aunque haya otro con el mismo nombre
     * o teléfono (pueden ser personas distintas). Nunca se vincula por nombre. */
    public ClienteResuelto resolverDesdeRegistroDeVenta(
            ClienteRequest r, Usuario captadoUsuario, AsesorExterno captadoAsesor) {
        String nombre = limpio(r.nombre());
        String paterno = limpio(r.apellidoPaterno());
        String telefono = limpio(r.telefono());
        String curp = limpio(r.curp()) == null ? null : limpio(r.curp()).toUpperCase(Locale.ROOT);
        if (nombre == null || paterno == null || telefono == null || r.fechaNacimiento() == null || curp == null) {
            throw new ValidationException("Faltan datos del cliente: nombre, apellido paterno, fecha de nacimiento, CURP y teléfono");
        }
        if (Period.between(r.fechaNacimiento(), LocalDate.now()).getYears() < 18) {
            throw new ValidationException("El cliente debe ser mayor de edad");
        }
        if (!CURP.matcher(curp).matches()) {
            throw new ValidationException("El CURP no tiene un formato válido (18 caracteres)");
        }
        String rfc = limpio(r.rfc()) == null ? null : limpio(r.rfc()).toUpperCase(Locale.ROOT);
        if (rfc != null && !RFC.matcher(rfc).matches()) {
            throw new ValidationException("El RFC no tiene un formato válido");
        }
        String pais = paisValido(r.paisResidencia());
        String estado = estadoValido(pais, r.estado());

        Cliente existente = clienteRepository.findByCurpIgnoreCase(curp).orElse(null);
        if (existente != null) {
            if (!existente.isActivo()) {
                throw new ValidationException(
                        "Ya existe un cliente con ese CURP pero está inactivo: comunícate con administración");
            }
            // Solo se completa lo que le falte; nada de lo que ya tiene se cambia.
            existente.actualizarDatos(
                    existente.getNombre(), existente.getApellidoPaterno(),
                    vacio(existente.getApellidoMaterno(), limpio(r.apellidoMaterno())),
                    existente.getFechaNacimiento(), existente.getTelefono(),
                    vacio(existente.getTelefono2(), limpio(r.telefono2())),
                    vacio(existente.getCorreo(), limpio(r.correo())), existente.getCurp(),
                    vacio(existente.getRfc(), rfc),
                    vacio(existente.getLugarNacimiento(), limpio(r.lugarNacimiento())),
                    existente.getNacionalidad(), existente.getEstadoCivil(), existente.getOcupacion(),
                    vacio(existente.getCalle(), limpio(r.calle())),
                    vacio(existente.getColonia(), limpio(r.colonia())),
                    vacio(existente.getMunicipio(), limpio(r.municipio())),
                    vacio(existente.getEstado(), estado),
                    vacio(existente.getCodigoPostal(), limpio(r.codigoPostal())),
                    existente.getBeneficiarioNombre(), existente.getBeneficiarioParentesco(),
                    existente.getFuente(), existente.getFuenteDetalle(),
                    existente.getCaptadoPorUsuario(), existente.getCaptadoPorAsesor(), existente.getNotas(),
                    existente.getExpedienteUbicacion(), existente.getExpedienteDriveUrl());
            if (existente.getPaisResidencia() == null || existente.getPaisResidencia().isBlank()) {
                existente.setPaisResidencia(pais);
            }
            return new ClienteResuelto(clienteRepository.save(existente), false);
        }

        Cliente c = new Cliente(nombre, paterno, limpio(r.apellidoMaterno()));
        c.setNumero(siguienteNumero());
        c.actualizarDatos(
                nombre, paterno, limpio(r.apellidoMaterno()), r.fechaNacimiento(), telefono, null,
                limpio(r.correo()), curp, rfc, limpio(r.lugarNacimiento()), null, null, null,
                limpio(r.calle()), limpio(r.colonia()), limpio(r.municipio()), estado,
                limpio(r.codigoPostal()), null, null,
                FuenteCliente.PRECONTRATO, null, captadoUsuario, captadoAsesor, null, null, null);
        c.setPaisResidencia(pais);
        return new ClienteResuelto(clienteRepository.save(c), true);
    }

    /** El cliente que resultó de un registro de venta y si se acaba de crear (false = ya existía con esa CURP). */
    public record ClienteResuelto(Cliente cliente, boolean nuevo) {
    }

    private static String vacio(String actual, String nuevo) {
        return actual != null && !actual.isBlank() ? actual : nuevo;
    }

    public ClienteDto actualizar(Long id, ClienteRequest r) {
        exigirAdminOLider();
        Cliente c = obtenerEntidad(id);
        aplicar(c, r);
        if (r.activo() != null) c.setActivo(r.activo());

        // El nombre de la ficha manda: se refleja en el texto de sus ventas y comisiones.
        String nombre = c.nombreCompleto();
        for (Venta v : ventaRepository.findByClienteRefId(c.getId())) {
            v.ligarCliente(c);
            comisionRepository.findAllByVentaId(v.getId()).forEach(com -> com.setCliente(nombre));
        }
        return detalle(clienteRepository.save(c));
    }

    /** Valida y copia los datos del request a la ficha (obligatorios, mayoría de edad, duplicados). */
    private void aplicar(Cliente c, ClienteRequest r) {
        LocalDate nacimiento = r.fechaNacimiento();
        if (Period.between(nacimiento, LocalDate.now()).getYears() < 18) {
            throw new ValidationException("El cliente debe ser mayor de edad");
        }
        String curp = limpio(r.curp()) == null ? null : limpio(r.curp()).toUpperCase(Locale.ROOT);
        if (curp != null && !CURP.matcher(curp).matches()) {
            throw new ValidationException("El CURP no tiene un formato válido (18 caracteres)");
        }
        String rfc = limpio(r.rfc()) == null ? null : limpio(r.rfc()).toUpperCase(Locale.ROOT);
        if (rfc != null && !RFC.matcher(rfc).matches()) {
            throw new ValidationException("El RFC no tiene un formato válido");
        }

        int quienes = (r.captadoPorUsuarioId() != null ? 1 : 0) + (r.captadoPorAsesorId() != null ? 1 : 0);
        if (quienes > 1) throw new ValidationException("Indica una sola persona que captó al cliente");
        Usuario captadoUsuario = r.captadoPorUsuarioId() == null
                ? null
                : usuarioRepository.findById(r.captadoPorUsuarioId())
                        .orElseThrow(() -> new ResourceNotFoundException("El usuario que lo captó no existe"));
        AsesorExterno captadoAsesor = r.captadoPorAsesorId() == null
                ? null
                : asesorExternoRepository.findById(r.captadoPorAsesorId())
                        .orElseThrow(() -> new ResourceNotFoundException("El asesor que lo captó no existe"));

        UbicacionDocumento ubicacion = r.expedienteUbicacion();
        String url = limpio(r.expedienteDriveUrl());
        if (url != null && (ubicacion == UbicacionDocumento.DRIVE || ubicacion == UbicacionDocumento.AMBOS)) {
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw new ValidationException("El link de la carpeta de Drive debe empezar con https://");
            }
        } else {
            url = null;
        }

        String nombre = r.nombre().trim();
        String paterno = r.apellidoPaterno().trim();
        String materno = limpio(r.apellidoMaterno());
        String telefono = r.telefono().trim();
        verificarDuplicado(c.getId(), nombre, paterno, materno, nacimiento, telefono, curp);
        String pais = paisValido(r.paisResidencia());
        String estadoCliente = estadoValido(pais, r.estado());

        c.actualizarDatos(
                nombre, paterno, materno, nacimiento, telefono, limpio(r.telefono2()), limpio(r.correo()), curp, rfc,
                limpio(r.lugarNacimiento()), limpio(r.nacionalidad()), r.estadoCivil(), limpio(r.ocupacion()),
                limpio(r.calle()), limpio(r.colonia()), limpio(r.municipio()), estadoCliente,
                limpio(r.codigoPostal()), limpio(r.beneficiarioNombre()), limpio(r.beneficiarioParentesco()),
                r.fuente(), limpio(r.fuenteDetalle()), captadoUsuario, captadoAsesor, limpio(r.notas()),
                ubicacion, url);
        c.setPaisResidencia(pais);
    }

    /** El país de la lista que corresponde al texto (null si viene vacío); cualquier otro texto se rechaza. */
    private static String paisValido(String texto) {
        String limpio = limpio(texto);
        if (limpio == null) return null;
        String canonico = Paises.canonico(limpio);
        if (canonico == null) throw new ValidationException("Elige un país de la lista");
        return canonico;
    }

    /** El estado limpio. Si el país es México debe ser uno de los 32 estados de la lista (se guarda el nombre
     * de la lista); en cualquier otro caso (otro país o sin país) es texto libre. */
    private static String estadoValido(String pais, String texto) {
        String limpio = limpio(texto);
        if (limpio == null || !Paises.MEXICO.equals(pais)) return limpio;
        String canonico = EstadosMexico.canonico(limpio);
        if (canonico == null) throw new ValidationException("Elige un estado de la lista");
        return canonico;
    }

    /** Avisa si ya existe alguien con el mismo CURP, el mismo nombre y fecha de nacimiento, o el mismo
     * teléfono (devuelve el id del existente para ofrecer usarlo). */
    private void verificarDuplicado(
            Long propioId, String nombre, String paterno, String materno, LocalDate nacimiento, String telefono, String curp) {
        String nombreNorm = normalizar(nombre + " " + paterno + (materno != null ? " " + materno : ""));
        String telDigitos = telefono.replaceAll("\\D", "");
        for (Cliente otro : clienteRepository.findAll()) {
            if (otro.getId().equals(propioId)) continue;
            if (curp != null && curp.equalsIgnoreCase(otro.getCurp())) {
                throw new ClienteDuplicadoException("Ya existe un cliente con ese CURP: " + otro.nombreCompleto(), otro.getId());
            }
            if (nacimiento.equals(otro.getFechaNacimiento()) && nombreNorm.equals(normalizar(otro.nombreCompleto()))) {
                throw new ClienteDuplicadoException(
                        "Ya existe un cliente con ese nombre y fecha de nacimiento: " + otro.nombreCompleto(), otro.getId());
            }
            if (telDigitos.length() >= 8 && telDigitos.equals(digitos(otro.getTelefono()))) {
                throw new ClienteDuplicadoException(
                        "Ya existe un cliente con ese teléfono: " + otro.nombreCompleto(), otro.getId());
            }
        }
    }

    // ---------------------------------------------------------------- ventas anteriores

    /** Liga a un cliente cada venta que aún no lo tiene (un cliente por nombre distinto). */
    @EventListener(ApplicationReadyEvent.class)
    public void migrarDesdeVentas() {
        try {
            Map<String, Cliente> porNombre = new HashMap<>();
            for (Cliente c : clienteRepository.findAll()) porNombre.putIfAbsent(normalizar(c.nombreCompleto()), c);
            for (Venta v : ventaRepository.findAll()) {
                if (v.getClienteRef() != null) continue;
                String clave = normalizar(v.getCliente());
                Cliente c = porNombre.get(clave);
                if (c == null) {
                    c = desdeTexto(v.getCliente());
                    c.setNumero(siguienteNumero());
                    c = clienteRepository.save(c);
                    porNombre.put(clave, c);
                }
                v.ligarCliente(c);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron crear los clientes de las ventas existentes", e);
        }
    }

    /** "Norma Yaneli Yañez Miranda" → nombre "Norma Yaneli", apellidos "Yañez" y "Miranda" (con tres o
     * más palabras los dos últimos son apellidos; con dos, el segundo). Es una suposición: queda como
     * "datos incompletos" para que se revise. */
    static Cliente desdeTexto(String texto) {
        String[] p = texto.trim().replaceAll("\\s+", " ").split(" ");
        int n = p.length;
        if (n >= 3) {
            return new Cliente(String.join(" ", java.util.Arrays.copyOfRange(p, 0, n - 2)), p[n - 2], p[n - 1]);
        }
        if (n == 2) return new Cliente(p[0], p[1], null);
        return new Cliente(p[0], null, null);
    }

    // ---------------------------------------------------------------- DTOs y utilidades

    /** Sumas por cliente, calculadas de una vez para toda la lista. */
    private final class Resumenes {
        private final Map<Long, List<Venta>> ventasPorCliente = new HashMap<>();
        private final Map<Long, List<VentaLote>> lotesPorVenta = new HashMap<>();
        private final Map<Long, BigDecimal> abonadoPorVenta = new HashMap<>();
        /** "clienteId:ventaId" de las ventas en las que el cliente es copropietario (no el principal). */
        private final java.util.Set<String> copropiedades = new java.util.HashSet<>();

        Resumenes() {
            for (Venta v : ventaRepository.findAll()) {
                if (v.getClienteRef() != null) {
                    ventasPorCliente.computeIfAbsent(v.getClienteRef().getId(), k -> new ArrayList<>()).add(v);
                }
            }
            for (com.werealestate.backend.model.VentaCopropietario cp : copropietarioRepository.findAll()) {
                ventasPorCliente.computeIfAbsent(cp.getCliente().getId(), k -> new ArrayList<>()).add(cp.getVenta());
                copropiedades.add(cp.getCliente().getId() + ":" + cp.getVenta().getId());
            }
            for (VentaLote vl : ventaLoteRepository.findAll()) {
                lotesPorVenta.computeIfAbsent(vl.getVenta().getId(), k -> new ArrayList<>()).add(vl);
            }
            for (PagoVenta p : pagoVentaRepository.findAllVigentes()) {
                abonadoPorVenta.merge(p.getVenta().getId(), p.getMonto(), BigDecimal::add);
            }
        }

        List<ClienteDto.ClienteVentaDto> ventasDe(Long clienteId) {
            return ventasPorCliente.getOrDefault(clienteId, List.of()).stream()
                    .sorted(Comparator.comparing(Venta::getFechaVenta).reversed())
                    .map(v -> {
                        List<VentaLote> lotes = lotesPorVenta.getOrDefault(v.getId(), List.of());
                        BigDecimal precio = lotes.stream().map(VentaLote::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal abonado = abonadoPorVenta.getOrDefault(v.getId(), BigDecimal.ZERO);
                        return new ClienteDto.ClienteVentaDto(
                                v.getId(),
                                v.getNumero(),
                                v.getFechaVenta(),
                                new ArrayList<>(new LinkedHashSet<>(
                                        lotes.stream().map(l -> l.getLote().getDesarrollo().getNombre()).toList())),
                                lotes.stream()
                                        .map(l -> "Mz " + l.getLote().getManzana() + " / Lote " + l.getLote().getNumeroLote())
                                        .toList(),
                                precio,
                                abonado,
                                precio.subtract(abonado).max(BigDecimal.ZERO),
                                copropiedades.contains(clienteId + ":" + v.getId()));
                    })
                    .toList();
        }

        ClienteListaDto lista(Cliente c) {
            List<ClienteDto.ClienteVentaDto> ventas = ventasDe(c.getId());
            return new ClienteListaDto(
                    c.getId(),
                    c.getNumero(),
                    c.nombreCompleto(),
                    c.getTelefono(),
                    c.getCorreo(),
                    c.isActivo(),
                    c.datosIncompletos(),
                    ventas.size(),
                    new ArrayList<>(new LinkedHashSet<>(ventas.stream().flatMap(v -> v.desarrollos().stream()).toList())),
                    ventas.stream().map(ClienteDto.ClienteVentaDto::saldo).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    private ClienteDto detalle(Cliente c) {
        return ClienteDto.from(c, new Resumenes().ventasDe(c.getId()));
    }

    public Cliente obtenerEntidad(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String digitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** Minúsculas, sin acentos y con un solo espacio entre palabras. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar clientes");
        }
        return actual;
    }
}
