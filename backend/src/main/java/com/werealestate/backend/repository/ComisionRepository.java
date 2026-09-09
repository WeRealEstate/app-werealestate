package com.werealestate.backend.repository;

import com.werealestate.backend.model.Comision;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * La funcionalidad de comisiones se retiró de la app, pero la tabla y sus registros históricos se
 * conservan en la base de datos. Este repositorio solo queda para no permitir borrar un lead o un
 * asesor que ya tiene una comisión asociada (perdería la referencia).
 */
public interface ComisionRepository extends JpaRepository<Comision, Long> {

    boolean existsByLeadId(Long leadId);

    boolean existsByAsesorId(Long asesorId);
}
