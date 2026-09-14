package com.werealestate.backend.repository;

import com.werealestate.backend.model.Seguimiento;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Long> {

    List<Seguimiento> findByLeadIdOrderByFechaDesc(Long leadId);

    /** Actividad registrada en el periodo, para el dashboard de desempeño. */
    List<Seguimiento> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Seguimiento> findByProximoSeguimientoIsNotNullOrderByProximoSeguimientoAsc();

    List<Seguimiento> findByProximoSeguimientoIsNotNullAndLeadAsesorIdOrderByProximoSeguimientoAsc(Long asesorId);

    boolean existsByAsesorId(Long asesorId);

    void deleteByLeadId(Long leadId);
}
