package com.werealestate.backend.repository;

import com.werealestate.backend.model.MovimientoLote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MovimientoLoteRepository
        extends JpaRepository<MovimientoLote, Long>, JpaSpecificationExecutor<MovimientoLote> {

    /** Historial completo de un lote específico, más reciente primero — para el ícono de "ver
     * información" en /panel/lotes. */
    List<MovimientoLote> findByLoteIdOrderByFechaDesc(Long loteId);
}
