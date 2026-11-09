package com.werealestate.backend.repository;

import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Seguimiento;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Long> {

    List<Seguimiento> findByLeadIdOrderByFechaDesc(Long leadId);

    /**
     * Para la campana (NotificacionService): el seguimiento más reciente de cada lead abierto (ni archivado ni cerrado)
     * cuyo próximo seguimiento ya llegó, con su lead, en una sola consulta. Antes se cargaban todos los leads y todos
     * sus seguimientos para quedarse con esto, en cada consulta de notificaciones. Mismo orden que antes: último
     * contacto más antiguo primero.
     */
    @Query("""
            select s from Seguimiento s join fetch s.lead l
            where l.archivado = false and l.estado not in :cerrados
              and s.proximoSeguimiento is not null and s.proximoSeguimiento <= :ahora
              and s.fecha = (select max(s2.fecha) from Seguimiento s2 where s2.lead = l)
            order by l.fechaUltimoContacto asc, l.id asc, s.id desc
            """)
    List<Seguimiento> ultimosVencidos(
            @Param("cerrados") Collection<EstadoLead> cerrados, @Param("ahora") LocalDateTime ahora);

    /** Lo mismo que {@link #ultimosVencidos}, solo de los leads de un asesor. */
    @Query("""
            select s from Seguimiento s join fetch s.lead l
            where l.asesor.id = :asesorId and l.archivado = false and l.estado not in :cerrados
              and s.proximoSeguimiento is not null and s.proximoSeguimiento <= :ahora
              and s.fecha = (select max(s2.fecha) from Seguimiento s2 where s2.lead = l)
            order by l.fechaUltimoContacto asc, l.id asc, s.id desc
            """)
    List<Seguimiento> ultimosVencidosDeAsesor(
            @Param("asesorId") Long asesorId,
            @Param("cerrados") Collection<EstadoLead> cerrados,
            @Param("ahora") LocalDateTime ahora);

    /** Actividad registrada en el periodo, para el dashboard de desempeño. */
    List<Seguimiento> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Seguimiento> findByProximoSeguimientoIsNotNullOrderByProximoSeguimientoAsc();

    List<Seguimiento> findByProximoSeguimientoIsNotNullAndLeadAsesorIdOrderByProximoSeguimientoAsc(Long asesorId);

    boolean existsByAsesorId(Long asesorId);

    void deleteByLeadId(Long leadId);

    /** IDs de los leads activos que tienen al menos un seguimiento registrado (evita N+1 en ReporteService.calcularRiesgo). */
    @Query("select distinct s.lead.id from Seguimiento s where s.lead.archivado = false")
    List<Long> leadIdsConSeguimiento();
}
