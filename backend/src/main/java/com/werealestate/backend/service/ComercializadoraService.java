package com.werealestate.backend.service;

import com.werealestate.backend.dto.ComercializadoraDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAsesorExterno;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.ComercializadoraRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comercializadoras en el panel (Admin y Administración con el módulo Asesores externos): crear una, elegir a su
 * líder (un asesor externo, que pasa a ser LIDER en Comunidades We), ver a sus integrantes y manejar el enlace
 * secreto que la líder comparte con su equipo. El alta de los integrantes está en ComercializadoraPublicoService.
 */
@Service
@Transactional
public class ComercializadoraService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ComercializadoraRepository repository;
    private final AsesorExternoRepository asesorRepository;
    private final CurrentUserProvider currentUserProvider;

    public ComercializadoraService(
            ComercializadoraRepository repository,
            AsesorExternoRepository asesorRepository,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.asesorRepository = asesorRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<ComercializadoraDtos.Item> listar() {
        exigirAdminOLider();
        return repository.findAllByOrderByNombreAsc().stream()
                .map(c -> ComercializadoraDtos.Item.from(c, asesorRepository.countByComercializadoraId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComercializadoraDtos.Integrante> integrantes(Long id) {
        exigirAdminOLider();
        obtener(id);
        return asesorRepository.findByComercializadoraIdOrderByNombreAsc(id).stream()
                .map(ComercializadoraDtos.Integrante::from)
                .toList();
    }

    public ComercializadoraDtos.Item crear(ComercializadoraDtos.CrearRequest request) {
        exigirAdminOLider();
        String nombre = request.nombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictException("Ya existe una comercializadora con ese nombre");
        }
        AsesorExterno lider = elegirLider(request.liderId(), null);
        Comercializadora c = repository.save(new Comercializadora(nombre, lider, vacioANulo(request.notas()), nuevoToken()));
        return ComercializadoraDtos.Item.from(c, 0);
    }

    public ComercializadoraDtos.Item actualizar(Long id, ComercializadoraDtos.ActualizarRequest request) {
        exigirAdminOLider();
        Comercializadora c = obtener(id);
        String nombre = request.nombre().trim();
        if (!nombre.equalsIgnoreCase(c.getNombre()) && repository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictException("Ya existe una comercializadora con ese nombre");
        }
        long integrantes = asesorRepository.countByComercializadoraId(id);
        AsesorExterno lider = c.getLider();
        if (!lider.getId().equals(request.liderId())) {
            if (integrantes > 0) {
                throw new ConflictException(
                        "No se puede cambiar a la líder: ya tiene integrantes en su equipo. Muévelos antes en Comunidades We.");
            }
            lider = elegirLider(request.liderId(), c.getId());
        }
        c.actualizar(nombre, lider, vacioANulo(request.notas()), request.activa());
        return ComercializadoraDtos.Item.from(c, integrantes);
    }

    /** Invalida el enlace anterior (por si se filtró) y deja uno nuevo. */
    public ComercializadoraDtos.Item regenerarEnlace(Long id) {
        exigirAdminOLider();
        Comercializadora c = obtener(id);
        c.regenerarToken(nuevoToken());
        return ComercializadoraDtos.Item.from(c, asesorRepository.countByComercializadoraId(id));
    }

    /** Solo si nadie se ha unido todavía; con integrantes se desactiva en vez de eliminar. */
    public void eliminar(Long id) {
        exigirAdminOLider();
        Comercializadora c = obtener(id);
        if (asesorRepository.countByComercializadoraId(id) > 0) {
            throw new ConflictException(
                    "No se puede eliminar: ya tiene integrantes. Desactívala para que su enlace deje de recibir altas.");
        }
        repository.delete(c);
    }

    // ---------------------------------------------------------------- internos

    private AsesorExterno elegirLider(Long liderId, Long comercializadoraActual) {
        AsesorExterno lider = asesorRepository
                .findById(liderId)
                .filter(AsesorExterno::isActivo)
                .orElseThrow(() -> new ValidationException("La líder elegida no existe o está inactiva"));
        if (lider.getUsuarioId() != null) {
            throw new ValidationException("La líder debe ser un asesor externo, no un usuario interno");
        }
        if (lider.getTipo() == TipoAsesorExterno.LINEA) {
            throw new ConflictException(
                    lider.getNombre() + " reporta a otra persona en Comunidades We: solo puede ser líder quien encabeza su propio equipo");
        }
        repository.findAllByOrderByNombreAsc().stream()
                .filter(o -> o.getLider().getId().equals(lider.getId()) && !o.getId().equals(comercializadoraActual))
                .findFirst()
                .ifPresent(o -> {
                    throw new ConflictException(lider.getNombre() + " ya es la líder de " + o.getNombre());
                });
        if (lider.getTipo() == TipoAsesorExterno.INDEPENDIENTE) {
            // Pasa a encabezar un equipo: así los que se unan quedan debajo de ella en Comunidades We.
            lider.setTipo(TipoAsesorExterno.LIDER);
            lider.setLiderDirecto(null);
            lider.setNivelLinea(null);
        }
        return lider;
    }

    private Comercializadora obtener(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comercializadora no encontrada"));
    }

    /** 32 caracteres aleatorios (192 bits): imposible de adivinar. */
    static String nuevoToken() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar comercializadoras");
        }
        return actual;
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
