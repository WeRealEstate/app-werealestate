package com.werealestate.backend.repository;

import com.werealestate.backend.model.Lead;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    List<Lead> findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoAsc(Long asesorId);

    List<Lead> findByArchivadoFalseOrderByFechaUltimoContactoAsc();

    /** Para la lista principal: el lead recién tocado (nuevo o con seguimiento reciente) sube arriba. Excluye archivados. */
    List<Lead> findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoDesc(Long asesorId);

    List<Lead> findByArchivadoFalseOrderByFechaUltimoContactoDesc();

    /** Leads archivados de un asesor, o de todo el equipo si se llama sin filtrar por asesor. */
    List<Lead> findByAsesorIdAndArchivadoTrueOrderByFechaUltimoContactoDesc(Long asesorId);

    List<Lead> findByArchivadoTrueOrderByFechaUltimoContactoDesc();

    List<Lead> findByAsesorIdAndFechaUltimoContactoBeforeAndEstadoNotInOrderByFechaUltimoContactoAsc(
            Long asesorId, LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);

    List<Lead> findByFechaUltimoContactoBeforeAndEstadoNotInAndArchivadoFalseOrderByFechaUltimoContactoAsc(
            LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);

    boolean existsByAsesorId(Long asesorId);

    long countByAsesorIdAndArchivadoFalse(Long asesorId);

    /** Cohorte de leads creados en el periodo, para el dashboard de desempeño. */
    List<Lead> findByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);

    /** Leads cerrados (ganados o perdidos) en el periodo: se usa fechaUltimoContacto como
     * aproximación de la fecha de cierre, ya que el modelo no tiene una dedicada. */
    List<Lead> findByEstadoInAndFechaUltimoContactoBetween(
            List<com.werealestate.backend.model.EstadoLead> estados, LocalDateTime desde, LocalDateTime hasta);
}
