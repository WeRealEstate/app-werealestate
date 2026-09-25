package com.werealestate.backend.service;

import com.werealestate.backend.dto.AsesorExternoCreateRequest;
import com.werealestate.backend.dto.AsesorExternoDto;
import com.werealestate.backend.dto.AsesorExternoUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roster de asesores externos: gente que vende pero no tiene cuenta en el sistema (ver
 * AsesorExterno). Gestionarlo (crear/editar/desactivar/eliminar) es exclusivo de admin, igual que
 * Usuarios; listar los activos también lo puede hacer un líder de área, porque también registra
 * ventas y necesita poder elegir un asesor externo ahí (ver VentaService).
 */
@Service
@Transactional
public class AsesorExternoService {

    private final AsesorExternoRepository asesorExternoRepository;
    private final VentaRepository ventaRepository;
    private final CurrentUserProvider currentUserProvider;

    public AsesorExternoService(
            AsesorExternoRepository asesorExternoRepository,
            VentaRepository ventaRepository,
            CurrentUserProvider currentUserProvider) {
        this.asesorExternoRepository = asesorExternoRepository;
        this.ventaRepository = ventaRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<AsesorExternoDto> listar() {
        exigirAdmin();
        return asesorExternoRepository.findAllByOrderByNombreAsc().stream().map(AsesorExternoDto::from).toList();
    }

    public List<AsesorExternoDto> listarActivos() {
        exigirAdminOLider();
        return asesorExternoRepository.findByActivoTrueOrderByNombreAsc().stream().map(AsesorExternoDto::from).toList();
    }

    public AsesorExternoDto crear(AsesorExternoCreateRequest request) {
        exigirAdmin();
        AsesorExterno asesor = new AsesorExterno(
                request.nombre().trim(), request.celular().trim(), request.correo().trim());
        return AsesorExternoDto.from(asesorExternoRepository.save(asesor));
    }

    public AsesorExternoDto actualizar(Long id, AsesorExternoUpdateRequest request) {
        exigirAdmin();
        AsesorExterno asesor = asesorExternoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asesor externo no encontrado"));
        asesor.setNombre(request.nombre().trim());
        asesor.setCelular(request.celular() == null || request.celular().isBlank() ? null : request.celular().trim());
        asesor.setCorreo(request.correo() == null || request.correo().isBlank() ? null : request.correo().trim());
        asesor.setActivo(request.activo());
        return AsesorExternoDto.from(asesorExternoRepository.save(asesor));
    }

    /** Se rechaza si ya se le acreditó alguna venta, para no dejar ventas con una referencia
     * colgando: en ese caso hay que desactivarlo en vez de eliminarlo (igual que un Usuario). */
    public void eliminar(Long id) {
        exigirAdmin();
        AsesorExterno asesor = asesorExternoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asesor externo no encontrado"));

        if (ventaRepository.existsByAsesorExternoId(id)) {
            throw new ConflictException(
                    "No se puede eliminar a " + asesor.getNombre()
                            + ": tiene ventas registradas. Desactívalo para quitarlo de la lista sin perder ese historial.");
        }

        asesorExternoRepository.delete(asesor);
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar asesores externos");
        }
        return actual;
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o líder de área puede consultar esta lista");
        }
        return actual;
    }
}
