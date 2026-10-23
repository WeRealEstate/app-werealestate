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
import com.werealestate.backend.repository.UsuarioRepository;
import java.security.SecureRandom;
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
    private static final long VENTANA_MS = 60 * 60 * 1000L;
    private static final String ALFABETO_PIN = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ComercializadoraRepository repository;
    private final AsesorExternoRepository asesorRepository;
    private final UsuarioRepository usuarioRepository;
    private final Map<String, Deque<Long>> altasRecientes = new ConcurrentHashMap<>();

    public ComercializadoraPublicoService(
            ComercializadoraRepository repository,
            AsesorExternoRepository asesorRepository,
            UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.asesorRepository = asesorRepository;
        this.usuarioRepository = usuarioRepository;
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
        nuevo.setPin(pinLibre());
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

    /** 4 letras o números, sin los que se confunden (0/O, 1/I), que ningún asesor ni usuario tenga ya. */
    private String pinLibre() {
        for (int intento = 0; intento < 500; intento++) {
            StringBuilder sb = new StringBuilder(4);
            for (int i = 0; i < 4; i++) sb.append(ALFABETO_PIN.charAt(ALEATORIO.nextInt(ALFABETO_PIN.length())));
            String pin = sb.toString();
            if (asesorRepository.findByPin(pin).isEmpty() && usuarioRepository.findByPin(pin).isEmpty()) return pin;
        }
        throw new IllegalStateException("No se pudo generar un PIN libre");
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
