package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaLote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaLoteRepository extends JpaRepository<VentaLote, Long> {

    List<VentaLote> findByVentaId(Long ventaId);

    /** Cuántos lotes tiene cada venta del conjunto: filas [ventaId, lotes] (para el Score de ventas). */
    @Query("select vl.venta.id, count(vl) from VentaLote vl where vl.venta.id in :ventaIds group by vl.venta.id")
    List<Object[]> contarLotesPorVenta(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    boolean existsByLoteId(Long loteId);

    @Query("select count(distinct vl.lote.id) from VentaLote vl")
    long countLotesVendidos();

    /** Lotes vendidos (cada uno una vez) de un desarrollo. */
    @Query("select count(distinct vl.lote.id) from VentaLote vl where vl.lote.desarrollo.id = :desarrolloId")
    long countLotesVendidosByDesarrolloId(@Param("desarrolloId") Long desarrolloId);

    /** Ventas con al menos un lote de ese desarrollo (una venta mixta cuenta en cada desarrollo que toca). */
    @Query("select count(distinct vl.venta.id) from VentaLote vl where vl.lote.desarrollo.id = :desarrolloId")
    long countVentasByDesarrolloId(@Param("desarrolloId") Long desarrolloId);

    @Query("select count(distinct vl.lote.id) from VentaLote vl where vl.lote.desarrollo.nombre = :desarrolloNombre")
    long countLotesVendidosByDesarrolloNombre(@Param("desarrolloNombre") String desarrolloNombre);

    /** Más reciente primero: si un lote llegó a estar en más de una venta (ej. una venta cancelada
     * a mano y vuelto a vender), nos interesa la última. */
    Optional<VentaLote> findFirstByLoteIdOrderByIdDesc(Long loteId);

    /** De este conjunto de lotes, cuáles ya tienen algún VentaLote — para el punto rojo de "falta
     * registrar la venta" en /panel/lotes (ver LoteService.buscarPaginado). */
    @Query("select distinct vl.lote.id from VentaLote vl where vl.lote.id in :loteIds")
    List<Long> findLoteIdsConVenta(@Param("loteIds") List<Long> loteIds);
}
