package com.werealestate.backend.repository;

import com.werealestate.backend.model.Lead;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    List<Lead> findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoAsc(Long asesorId);

    List<Lead> findByArchivadoFalseOrderByFechaUltimoContactoAsc();

    /** Para la lista principal: el lead recién tocado (nuevo o con seguimiento reciente) sube arriba. Excluye archivados. */
    @EntityGraph(attributePaths = {"desarrollo", "asesor", "columnaPersonalizada", "etiquetas"})
    List<Lead> findByAsesorIdAndArchivadoFalseOrderByFechaUltimoContactoDesc(Long asesorId);

    @EntityGraph(attributePaths = {"desarrollo", "asesor", "columnaPersonalizada", "etiquetas"})
    List<Lead> findByArchivadoFalseOrderByFechaUltimoContactoDesc();

    /** Leads archivados de un asesor, o de todo el equipo si se llama sin filtrar por asesor. */
    @EntityGraph(attributePaths = {"desarrollo", "asesor", "columnaPersonalizada", "etiquetas"})
    List<Lead> findByAsesorIdAndArchivadoTrueOrderByFechaUltimoContactoDesc(Long asesorId);

    @EntityGraph(attributePaths = {"desarrollo", "asesor", "columnaPersonalizada", "etiquetas"})
    List<Lead> findByArchivadoTrueOrderByFechaUltimoContactoDesc();

    List<Lead> findByAsesorIdAndFechaUltimoContactoBeforeAndEstadoNotInOrderByFechaUltimoContactoAsc(
            Long asesorId, LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);

    List<Lead> findByFechaUltimoContactoBeforeAndEstadoNotInAndArchivadoFalseOrderByFechaUltimoContactoAsc(
            LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);

    boolean existsByAsesorId(Long asesorId);

    /** Candado que dura lo que la transacción, para que dos altas del mismo asesor no tomen el mismo número. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    Integer bloquearNumeracion(@Param("clave") long clave);

    /** El siguiente número libre del asesor (el mayor que ha tenido + 1; los huecos por reasignar no se reutilizan). */
    @Query(value = "select coalesce(max(numero), 0) + 1 from lead where asesor_id = :asesorId", nativeQuery = true)
    long siguienteNumero(@Param("asesorId") Long asesorId);

    long countByAsesorIdAndArchivadoFalse(Long asesorId);

    /** Cohorte de leads creados en el periodo, para el dashboard de desempeño. */
    List<Lead> findByFechaCreacionBetween(LocalDateTime desde, LocalDateTime hasta);

    /** Leads cerrados (ganados o perdidos) en el periodo: se usa fechaUltimoContacto como
     * aproximación de la fecha de cierre, ya que el modelo no tiene una dedicada. */
    List<Lead> findByEstadoInAndFechaUltimoContactoBetween(
            List<com.werealestate.backend.model.EstadoLead> estados, LocalDateTime desde, LocalDateTime hasta);

    @Query("select l.estado, count(l) from Lead l where l.archivado = false group by l.estado")
    List<Object[]> contarPorEstadoActivos();

    @Query("select l.estado, count(l) from Lead l where l.archivado = false and l.asesor.id = :asesorId group by l.estado")
    List<Object[]> contarPorEstadoActivosDeAsesor(@Param("asesorId") Long asesorId);

    @Query("select count(l) from Lead l where l.archivado = false and l.fechaUltimoContacto <= :limite "
            + "and l.estado not in (com.werealestate.backend.model.EstadoLead.CERRADO_GANADO, com.werealestate.backend.model.EstadoLead.CERRADO_PERDIDO)")
    long contarFriosActivos(@Param("limite") LocalDateTime limite);

    @Query("select count(l) from Lead l where l.archivado = false and l.asesor.id = :asesorId and l.fechaUltimoContacto <= :limite "
            + "and l.estado not in (com.werealestate.backend.model.EstadoLead.CERRADO_GANADO, com.werealestate.backend.model.EstadoLead.CERRADO_PERDIDO)")
    long contarFriosActivosDeAsesor(@Param("asesorId") Long asesorId, @Param("limite") LocalDateTime limite);
}
