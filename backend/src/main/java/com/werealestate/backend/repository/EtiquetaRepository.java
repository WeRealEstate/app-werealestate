package com.werealestate.backend.repository;

import com.werealestate.backend.model.Etiqueta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtiquetaRepository extends JpaRepository<Etiqueta, Long> {

    List<Etiqueta> findByAsesorIdOrderByNombreAsc(Long asesorId);

    void deleteByAsesorId(Long asesorId);

    /** Bloquea el borrado de una etiqueta mientras algún lead todavía la tenga asignada. */
    @Query("SELECT COUNT(l) > 0 FROM Lead l JOIN l.etiquetas e WHERE e.id = :etiquetaId")
    boolean tieneLeadsAsignados(@Param("etiquetaId") Long etiquetaId);
}
