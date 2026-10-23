package com.werealestate.backend.service;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.TipoAsesorExterno;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ComercializadoraRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alta de un integrante por el enlace de su comercializadora (sin sesión; el token del enlace es el único permiso).
 * Cada integrante entra directo como asesor externo de LÍNEA 1 de la líder (así aparece en su equipo de Comunidades
 * We), con contrato vigente, acceso a SAMAI y Nanuu, "traído por" la líder y un PIN generado aquí. Se limita la
 * cantidad de altas por IP y por enlace para que un enlace filtrado no se use para llenar el sistema.
 */
@Service
public class ComercializadoraPublicoService {

    static final int MAX_ALTAS_POR_IP = 10;
    static final int MAX_ALTAS_POR_ENLACE = 60;
    /** PINs rechazados por estar en uso, por IP y hora: sin tope, probar PINs serviría para descubrir cuáles existen. */
    static final int MAX_PINES_RECHAZADOS_POR_IP = 5;
    private static final long VENTANA_MS = 60 * 60 * 1000L;
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ComercializadoraRepository repository;
    private final AsesorExternoRepository asesorRepository;
    private final PinAsesor pinAsesor;
    private final Map<String, Deque<Long>> altasRecientes = new ConcurrentHashMap<>();

    public ComercializadoraPublicoService(
            ComercializadoraRepository repository,
            AsesorExternoRepository asesorRepository,
            PinAsesor pinAsesor) {
        this.repository = repository;
        this.asesorRepository = asesorRepository;
        this.pinAsesor = pinAsesor;
    }

    @Transactional(readOnly = true)
    public ComercializadoraDtos.PublicoInfo info(String token) {
        Comercializadora c = activa(token);
        return new ComercializadoraDtos.PublicoInfo(c.getNombre(), c.getLider().getNombre());
    }

    @Transactional
    public ComercializadoraDtos.UnirseResultado unirse(String token, ComercializadoraDtos.UnirseRequest request, String ip) {
        Comercializadora c = activa(token);
        if (superaLimite("ip:" + ip, MAX_ALTAS_POR_IP) || superaLimite("enlace:" + c.getId(), MAX_ALTAS_POR_ENLACE)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Demasiados registros en poco tiempo. Inténtalo de nuevo más tarde.");
        }

        String nombre = limpiarNombre(request.nombre());
        if (nombre.length() < 3) throw new ValidationException("Escribe tu nombre completo");
        String celular = soloDigitos(request.celular());
        if (celular.length() != 10) throw new ValidationException("El celular debe tener 10 dígitos");
        String correo = request.correo() == null || request.correo().isBlank() ? null : request.correo().trim();
        if (correo != null && !CORREO.matcher(correo).matches()) throw new ValidationException("El correo no es válido");

        AsesorExterno lider = c.getLider();
        if (lider.getTipo() != TipoAsesorExterno.LIDER || !lider.isActivo()) {
            throw new ConflictException("Este enlace no está disponible por ahora. Avísale a tu líder.");
        }
        boolean yaRegistrado = asesorRepository.findAll().stream()
                .anyMatch(a -> a.isActivo() && celular.equals(soloDigitos(a.getCelular())));
        if (yaRegistrado) {
            throw new ConflictException("Ese celular ya está registrado. Si es tuyo, pídele tu PIN a tu líder.");
        }
        String pin = elegirPin(request.pin(), ip);
        registrarAlta("ip:" + ip);
        registrarAlta("enlace:" + c.getId());

        AsesorExterno nuevo = new AsesorExterno(nombre, celular, correo);
        nuevo.setTipo(TipoAsesorExterno.LINEA);
        nuevo.setLiderDirecto(lider);
        nuevo.setNivelLinea(1);
        nuevo.setContratoEstado(EstadoContratoAsesor.VIGENTE);
        nuevo.setAccesoSamai(true);
        nuevo.setAccesoNanuu(true);
        nuevo.setComercializadora(c);
        nuevo.actualizarFicha(null, null, null, null, null, null, null, lider, null, null);
        nuevo.setPin(pin);
        asesorRepository.save(nuevo);
        return new ComercializadoraDtos.UnirseResultado(nuevo.getNombre(), nuevo.getPin(), c.getNombre(), lider.getNombre());
    }

    // ---------------------------------------------------------------- internos

    /** El enlace debe existir y la comercializadora estar activa; si no, se responde igual que si no existiera. */
    private Comercializadora activa(String token) {
        Comercializadora c = repository
                .findByToken(token == null ? "" : token)
                .orElseThrow(() -> new ResourceNotFoundException("Este enlace no es válido"));
        if (!c.isActiva()) throw new ResourceNotFoundException("Este enlace no es válido");
        return c;
    }

    /** El PIN que escribió el integrante, o uno al azar si lo dejó vacío. Un PIN que ya está en uso cuenta contra un tope
     * por IP, para que el formulario no sirva para averiguar qué PINs existen. */
    private String elegirPin(String escrito, String ip) {
        if (escrito == null || escrito.isBlank()) return pinAsesor.generarLibre();
        String clave = "pin:" + ip;
        if (superaLimite(clave, MAX_PINES_RECHAZADOS_POR_IP)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos con el PIN. Inténtalo de nuevo más tarde.");
        }
        try {
            return pinAsesor.validarElegido(escrito);
        } catch (ConflictException e) {
            registrarAlta(clave);
            throw e;
        }
    }

    private boolean superaLimite(String clave, int maximo) {
        Deque<Long> altas = altasRecientes.get(clave);
        if (altas == null) return false;
        synchronized (altas) {
            purgar(altas);
            return altas.size() >= maximo;
        }
    }

    private void registrarAlta(String clave) {
        Deque<Long> altas = altasRecientes.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (altas) {
            purgar(altas);
            altas.addLast(System.currentTimeMillis());
        }
    }

    private static void purgar(Deque<Long> altas) {
        long limite = System.currentTimeMillis() - VENTANA_MS;
        while (!altas.isEmpty() && altas.peekFirst() < limite) altas.removeFirst();
    }

    static String soloDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    static String limpiarNombre(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
