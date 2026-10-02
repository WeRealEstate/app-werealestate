package com.werealestate.backend.repository;

import com.werealestate.backend.model.MovimientoLote;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoLoteRepository
        extends JpaRepository<MovimientoLote, Long>, JpaSpecificationExecutor<MovimientoLote> {

    /** Historial completo de un lote específico, más reciente primero — para el ícono de "ver
     * información" en /panel/lotes. */
    List<MovimientoLote> findByLoteIdOrderByFechaDesc(Long loteId);

    /** Cambios de estado desde `desde` hechos por alguien distinto de `usuarioId` (o por nadie: la
     * reversión automática por vencimiento) — la base de los avisos de apartado/desapartado de
     * NotificacionService, que no le avisan a quien hizo el cambio. */
    @Query("""
            select m from MovimientoLote m
            join fetch m.lote l
            join fetch l.desarrollo
            left join fetch m.usuario u
            where m.fecha >= :desde and (u is null or u.id <> :usuarioId)
            order by m.fecha desc
            """)
    List<MovimientoLote> recientesDeOtros(
            @Param("desde") LocalDateTime desde, @Param("usuarioId") Long usuarioId);
}
