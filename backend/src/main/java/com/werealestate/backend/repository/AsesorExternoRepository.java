package com.werealestate.backend.repository;

import com.werealestate.backend.model.AsesorExterno;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsesorExternoRepository extends JpaRepository<AsesorExterno, Long> {

    List<AsesorExterno> findByActivoTrueOrderByNombreAsc();

    List<AsesorExterno> findAllByOrderByNombreAsc();

    /** Para bloquear borrado/cambio de tipo mientras tenga gente reportándole (ver
     * AsesorExternoService). */
    boolean existsByLiderDirectoId(Long liderDirectoId);

    /** Para no borrar a quien figura como "quien trajo" a otro asesor (ver la ficha). */
    boolean existsByTraidoPorAsesorId(Long asesorId);

    boolean existsByTraidoPorUsuarioId(Long usuarioId);
}
