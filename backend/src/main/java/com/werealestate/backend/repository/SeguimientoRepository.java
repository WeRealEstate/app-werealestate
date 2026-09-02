package com.werealestate.backend.repository;

import com.werealestate.backend.model.Seguimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Long> {

    List<Seguimiento> findByLeadIdOrderByFechaDesc(Long leadId);

    List<Seguimiento> findByProximoSeguimientoIsNotNullOrderByProximoSeguimientoAsc();

    List<Seguimiento> findByProximoSeguimientoIsNotNullAndLeadAsesorIdOrderByProximoSeguimientoAsc(Long asesorId);

    boolean existsByAsesorId(Long asesorId);
}
