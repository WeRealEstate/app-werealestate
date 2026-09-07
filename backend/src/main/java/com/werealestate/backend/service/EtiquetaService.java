package com.werealestate.backend.service;

import com.werealestate.backend.dto.EtiquetaCreateRequest;
import com.werealestate.backend.dto.EtiquetaDto;
import com.werealestate.backend.dto.EtiquetaUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Etiqueta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EtiquetaRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Etiquetas que cada asesor arma para su propio catálogo de leads. Son privadas: no se comparten
 * entre asesores. Un admin puede gestionarlas mientras ve los leads de cualquier asesor.
 */
@Service
@Transactional
public class EtiquetaService {

    private final EtiquetaRepository etiquetaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;

    public EtiquetaService(
            EtiquetaRepository etiquetaRepository,
            UsuarioRepository usuarioRepository,
            CurrentUserProvider currentUserProvider) {
        this.etiquetaRepository = etiquetaRepository;
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<EtiquetaDto> listar(Long asesorIdParam) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Long targetId = resolverAsesorId(actual, asesorIdParam);
        return etiquetaRepository.findByAsesorIdOrderByNombreAsc(targetId).stream()
                .map(EtiquetaDto::from)
                .toList();
    }

    public EtiquetaDto crear(EtiquetaCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Long targetId = resolverAsesorId(actual, request.asesorId());
        Usuario asesor = targetId.equals(actual.getId())
                ? actual
                : usuarioRepository.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Etiqueta etiqueta = new Etiqueta(request.nombre().trim(), request.color(), asesor);
        return EtiquetaDto.from(etiquetaRepository.save(etiqueta));
    }

    public EtiquetaDto actualizar(Long id, EtiquetaUpdateRequest request) {
        Etiqueta etiqueta = buscarPermitida(id);
        etiqueta.setNombre(request.nombre().trim());
        etiqueta.setColor(request.color());
        return EtiquetaDto.from(etiquetaRepository.save(etiqueta));
    }

    /** Se rechaza si algún lead todavía tiene esta etiqueta asignada. */
    public void eliminar(Long id) {
        Etiqueta etiqueta = buscarPermitida(id);
        if (etiquetaRepository.tieneLeadsAsignados(id)) {
            throw new ConflictException("No se puede eliminar la etiqueta \"" + etiqueta.getNombre()
                    + "\": todavía hay leads con esta etiqueta asignada. Quítala de esos leads primero.");
        }
        etiquetaRepository.delete(etiqueta);
    }

    private Etiqueta buscarPermitida(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Etiqueta etiqueta =
                etiquetaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada"));

        boolean esDueno = etiqueta.getAsesor().getId().equals(actual.getId());
        if (!esDueno && actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("No tienes acceso a esta etiqueta");
        }
        return etiqueta;
    }

    /** Un asesor solo ve/gestiona las suyas; un admin puede pasar asesorId para operar el catálogo de cualquiera. */
    private Long resolverAsesorId(Usuario actual, Long asesorIdParam) {
        if (asesorIdParam == null || asesorIdParam.equals(actual.getId())) {
            return actual.getId();
        }
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("No tienes acceso a las etiquetas de otro asesor");
        }
        return asesorIdParam;
    }
}
