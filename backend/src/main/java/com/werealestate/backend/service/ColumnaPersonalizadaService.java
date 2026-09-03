package com.werealestate.backend.service;

import com.werealestate.backend.dto.ColumnaPersonalizadaCreateRequest;
import com.werealestate.backend.dto.ColumnaPersonalizadaDto;
import com.werealestate.backend.dto.ColumnaPersonalizadaUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.ColumnaPersonalizada;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ColumnaPersonalizadaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Columnas extra que cada asesor arma para su propio tablero de tarjetas, además de los estados
 * fijos del lead. Un admin puede gestionarlas mientras ve el tablero de cualquier asesor.
 */
@Service
@Transactional
public class ColumnaPersonalizadaService {

    /** Tope de tarjetas por asesor, para mantener el tablero manejable. */
    private static final int MAX_TARJETAS_POR_ASESOR = 20;

    private final ColumnaPersonalizadaRepository columnaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;

    public ColumnaPersonalizadaService(
            ColumnaPersonalizadaRepository columnaRepository,
            UsuarioRepository usuarioRepository,
            CurrentUserProvider currentUserProvider) {
        this.columnaRepository = columnaRepository;
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ColumnaPersonalizadaDto> listar(Long asesorIdParam) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Long targetId = resolverAsesorId(actual, asesorIdParam);
        return columnaRepository.findByAsesorIdOrderByOrdenAsc(targetId).stream()
                .map(ColumnaPersonalizadaDto::from)
                .toList();
    }

    public ColumnaPersonalizadaDto crear(ColumnaPersonalizadaCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Long targetId = resolverAsesorId(actual, request.asesorId());
        Usuario asesor = targetId.equals(actual.getId())
                ? actual
                : usuarioRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        long total = columnaRepository.countByAsesorId(targetId);
        if (total >= MAX_TARJETAS_POR_ASESOR) {
            throw new ConflictException(asesor.getNombre() + " ya tiene " + MAX_TARJETAS_POR_ASESOR
                    + " tarjetas, el máximo permitido. Elimina alguna antes de agregar otra.");
        }

        ColumnaPersonalizada columna = new ColumnaPersonalizada(request.nombre().trim(), asesor, (int) total);
        return ColumnaPersonalizadaDto.from(columnaRepository.save(columna));
    }

    public ColumnaPersonalizadaDto renombrar(Long id, ColumnaPersonalizadaUpdateRequest request) {
        ColumnaPersonalizada columna = buscarPermitida(id);
        columna.setNombre(request.nombre().trim());
        return ColumnaPersonalizadaDto.from(columnaRepository.save(columna));
    }

    /** Al eliminar, los leads que estaban en esta columna quedan con columnaPersonalizadaId = null (ON DELETE SET NULL) y vuelven a agruparse por su estado real. */
    public void eliminar(Long id) {
        ColumnaPersonalizada columna = buscarPermitida(id);
        columnaRepository.delete(columna);
    }

    private ColumnaPersonalizada buscarPermitida(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        ColumnaPersonalizada columna =
                columnaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Columna no encontrada"));

        boolean esDueno = columna.getAsesor().getId().equals(actual.getId());
        if (!esDueno && actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("No tienes acceso a esta columna");
        }
        return columna;
    }

    /** Un asesor solo ve/gestiona las suyas; un admin puede pasar asesorId para operar el tablero de cualquiera. */
    private Long resolverAsesorId(Usuario actual, Long asesorIdParam) {
        if (asesorIdParam == null || asesorIdParam.equals(actual.getId())) {
            return actual.getId();
        }
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("No tienes acceso a las columnas de otro asesor");
        }
        return asesorIdParam;
    }
}
