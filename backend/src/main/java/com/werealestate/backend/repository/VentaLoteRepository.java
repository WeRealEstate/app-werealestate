package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaLote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaLoteRepository extends JpaRepository<VentaLote, Long> {

    List<VentaLote> findByVentaId(Long ventaId);

    /** Más reciente primero: si un lote llegó a estar en más de una venta (ej. una venta cancelada
     * a mano y vuelto a vender), nos interesa la última. */
    Optional<VentaLote> findFirstByLoteIdOrderByIdDesc(Long loteId);

    /** De este conjunto de lotes, cuáles ya tienen algún VentaLote — para el punto rojo de "falta
     * registrar la venta" en /panel/lotes (ver LoteService.buscarPaginado). */
    @Query("select distinct vl.lote.id from VentaLote vl where vl.lote.id in :loteIds")
    List<Long> findLoteIdsConVenta(@Param("loteIds") List<Long> loteIds);
}
