package com.werealestate.backend.service;

import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.PromocionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromocionService {

    private final PromocionRepository promocionRepository;
    private final CurrentUserProvider currentUserProvider;

    public PromocionService(PromocionRepository promocionRepository, CurrentUserProvider currentUserProvider) {
        this.promocionRepository = promocionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Cualquier asesor o admin puede consultar qué promociones están vigentes para cotizar. */
    public List<PromocionDto> listarActivas() {
        return promocionRepository.findByActivaTrue().stream()
                .peek(this::expirarSiVencida)
                .filter(Promocion::isActiva)
                .map(PromocionDto::from)
                .toList();
    }

    /** Catálogo completo (activas e inactivas), solo para la pantalla de administración. */
    public List<PromocionDto> listar() {
        exigirAdmin();
        return promocionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .peek(this::expirarSiVencida)
                .map(PromocionDto::from)
                .toList();
    }

    public PromocionDto crear(PromocionCreateRequest request) {
        exigirAdmin();
        Promocion promocion = new Promocion(
                request.nombre(), request.proyecto(), request.mensualidadFija(), request.descripcion(), request.fechaFin());
        if (promocion.isActiva()) {
            desactivarOtrasDelProyecto(promocion.getProyecto(), null);
        }
        promocionRepository.save(promocion);
        expirarSiVencida(promocion);
        return PromocionDto.from(promocion);
    }

    public PromocionDto actualizar(Long id, PromocionUpdateRequest request) {
        exigirAdmin();
        Promocion promocion = buscar(id);
        promocion.setNombre(request.nombre());
        promocion.setMensualidadFija(request.mensualidadFija());
        promocion.setDescripcion(request.descripcion());
        promocion.setFechaFin(request.fechaFin());
        promocionRepository.save(promocion);
        expirarSiVencida(promocion);
        return PromocionDto.from(promocion);
    }

    /** Si la promoción sigue marcada como activa pero ya pasó su fecha de fin, se desactiva sola:
     * ni el cotizador ni la pantalla de administración deben seguir mostrándola como vigente. */
    private void expirarSiVencida(Promocion promocion) {
        if (promocion.isActiva() && promocion.getFechaFin() != null && !promocion.getFechaFin().isAfter(LocalDateTime.now())) {
            promocion.setActiva(false);
            promocionRepository.save(promocion);
        }
    }

    /** Al activar una promoción, se desactiva cualquier otra activa del mismo proyecto: solo una
     * promoción vigente por proyecto a la vez, para que el cotizador no tenga que elegir entre dos. */
    public PromocionDto cambiarEstado(Long id, boolean activa) {
        exigirAdmin();
        Promocion promocion = buscar(id);
        if (activa) {
            desactivarOtrasDelProyecto(promocion.getProyecto(), id);
        }
        promocion.setActiva(activa);
        return PromocionDto.from(promocionRepository.save(promocion));
    }

    public void eliminar(Long id) {
        exigirAdmin();
        Promocion promocion = buscar(id);
        promocionRepository.delete(promocion);
    }

    private void desactivarOtrasDelProyecto(String proyecto, Long exceptoId) {
        for (Promocion otra : promocionRepository.findByProyectoAndActivaTrue(proyecto)) {
            if (exceptoId == null || !otra.getId().equals(exceptoId)) {
                otra.setActiva(false);
                promocionRepository.save(otra);
            }
        }
    }

    private Promocion buscar(Long id) {
        return promocionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
    }

    private void exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar promociones");
        }
    }
}
