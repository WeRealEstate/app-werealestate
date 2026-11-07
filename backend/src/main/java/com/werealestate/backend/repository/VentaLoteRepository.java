package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaLote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaLoteRepository extends JpaRepository<VentaLote, Long> {

    List<VentaLote> findByVentaId(Long ventaId);

    /** Los lotes de varias ventas de una sola vez (lista de Ventas, ver VentaService.toDtos). */
    List<VentaLote> findByVentaIdInOrderByIdAsc(java.util.Collection<Long> ventaIds);

    /** Cuántos lotes tiene cada venta del conjunto: filas [ventaId, lotes] (para el Score de ventas). */
    @Query("select vl.venta.id, count(vl) from VentaLote vl where vl.venta.id in :ventaIds group by vl.venta.id")
    List<Object[]> contarLotesPorVenta(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    boolean existsByLoteId(Long loteId);

    /** El desarrollo de cada lote de cada venta: filas [ventaId, nombre del desarrollo], sin repetir (desglose de
     * comisiones por desarrollo). */
    @Query("select distinct vl.venta.id, vl.lote.desarrollo.nombre from VentaLote vl")
    List<Object[]> desarrollosPorVenta();

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

    /** Todas las ventas que incluyen un lote (normalmente una; más solo con datos de antes de que se impidiera). */
    List<VentaLote> findByLoteId(Long loteId);

    /** Número de venta de cada lote del conjunto que ya está en alguna: filas [loteId, número]. Si un lote quedó en
     * más de una (datos de antes de que se impidiera), trae la de número mayor. Ver LoteService.marcarVentas. */
    @Query("select vl.lote.id, max(vl.venta.numero) from VentaLote vl where vl.lote.id in :loteIds group by vl.lote.id")
    List<Object[]> numeroDeVentaPorLote(@Param("loteIds") java.util.Collection<Long> loteIds);

    /** Lotes que están en una venta pero no están Vendidos ni En proceso de firma (datos de antes de que la venta
     * mandara el estado del lote): el aviso a Administración (ver NotificacionService). */
    @Query("select vl from VentaLote vl join fetch vl.lote l join fetch l.desarrollo d join fetch vl.venta "
            + "where l.estado not in (com.werealestate.backend.model.EstadoLote.VENDIDO, "
            + "com.werealestate.backend.model.EstadoLote.EN_PROCESO_DE_FIRMA) "
            + "order by d.nombre, l.manzana, l.numeroLote")
    List<VentaLote> findConLoteFueraDeEstadoDeVenta();
}
