package com.werealestate.backend.repository;

import com.werealestate.backend.model.Seguimiento;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Long> {

    List<Seguimiento> findByLeadIdOrderByFechaDesc(Long leadId);

    /** Para resolver "el último seguimiento de cada lead" en una sola consulta en vez de una por
     * lead (ver NotificacionService.listar): ordenado por lead y luego por fecha desc, así que la
     * primera fila de cada lead dentro del resultado es su seguimiento más reciente. */
    List<Seguimiento> findByLeadIdInOrderByLeadIdAscFechaDesc(List<Long> leadIds);

    /** Actividad registrada en el periodo, para el dashboard de desempeño. */
    List<Seguimiento> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Seguimiento> findByProximoSeguimientoIsNotNullOrderByProximoSeguimientoAsc();

    List<Seguimiento> findByProximoSeguimientoIsNotNullAndLeadAsesorIdOrderByProximoSeguimientoAsc(Long asesorId);

    boolean existsByAsesorId(Long asesorId);

    void deleteByLeadId(Long leadId);
}
