package com.werealestate.backend.service;

import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAsesorExterno;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene la "ficha de comunidad" de cada usuario interno con rol ASESOR: una fila de
 * asesor_externo enlazada por usuario_id, para que aparezca en el árbol de Comunidades We (líder /
 * línea 1 / línea 2) junto a los asesores externos. Quien deja de ser ASESOR sale del árbol. La
 * ficha no es un asesor externo de verdad: las demás pantallas la ignoran (ver
 * AsesorExternoRepository.findByActivoTrueAndUsuarioIdIsNull... y el frontend).
 */
@Service
@Transactional
public class ComunidadInternosService {

    private final AsesorExternoRepository asesorExternoRepository;

    public ComunidadInternosService(AsesorExternoRepository asesorExternoRepository) {
        this.asesorExternoRepository = asesorExternoRepository;
    }

    /** Crea o actualiza la ficha si el usuario es ASESOR; si dejó de serlo, la quita del árbol. */
    public void sincronizar(Usuario usuario) {
        var ficha = asesorExternoRepository.findByUsuarioId(usuario.getId());
        if (usuario.getRol() == Role.ASESOR) {
            AsesorExterno asesor = ficha.orElseGet(() -> {
                AsesorExterno nueva = new AsesorExterno(usuario.getNombre(), null, usuario.getEmail());
                nueva.setUsuarioId(usuario.getId());
                return nueva;
            });
            asesor.setNombre(usuario.getNombre());
            asesor.setCorreo(usuario.getEmail());
            asesor.setActivo(usuario.isActivo());
            asesorExternoRepository.save(asesor);
        } else {
            ficha.ifPresent(this::quitarDelArbol);
        }
    }

    /** Al eliminar el usuario: su ficha se borra, salvo que tenga gente reportándole. */
    public void eliminarDe(Usuario usuario) {
        asesorExternoRepository.findByUsuarioId(usuario.getId()).ifPresent(this::quitarDelArbol);
    }

    private void quitarDelArbol(AsesorExterno ficha) {
        if (asesorExternoRepository.existsByLiderDirectoId(ficha.getId())) {
            throw new ConflictException(
                    "No se puede quitar a " + ficha.getNombre()
                            + " de Comunidades We: tiene gente reportándole. Reasígnalos o quítalos del equipo primero.");
        }
        if (asesorExternoRepository.existsByTraidoPorAsesorId(ficha.getId())) {
            // Figura como "quien trajo" a alguien: se conserva la fila pero fuera del árbol y inactiva.
            ficha.setActivo(false);
            ficha.setTipo(TipoAsesorExterno.INDEPENDIENTE);
            ficha.setLiderDirecto(null);
            ficha.setNivelLinea(null);
            asesorExternoRepository.save(ficha);
            return;
        }
        asesorExternoRepository.delete(ficha);
    }
}
