package com.werealestate.backend.repository;

import com.werealestate.backend.model.PagoVenta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoVentaRepository extends JpaRepository<PagoVenta, Long> {

    /** Todas las líneas con su recibo (anulados incluidos): para los libros de Finanzas, que registran
     * un contra-asiento por cada recibo anulado. */
    @Query("select p from PagoVenta p join fetch p.recibo")
    List<PagoVenta> findAllConRecibo();

    /** Solo las vigentes (su recibo no está anulado): lo que cuenta para saldos y comisiones. */
    @Query("select p from PagoVenta p join fetch p.recibo r where r.anulado = false")
    List<PagoVenta> findAllVigentes();

    @Query("select p from PagoVenta p join fetch p.recibo r where p.venta.id = :ventaId and r.anulado = false order by p.fecha asc, p.id asc")
    List<PagoVenta> findVigentesByVentaIdOrderByFechaAscIdAsc(@Param("ventaId") Long ventaId);

    /** Lo abonado de cada venta (solo recibos vigentes), sumado en la base para varias ventas a la vez: filas
     * [ventaId, total]. Una venta sin abonos no aparece. Lista de Ventas, ver VentaService.toDtos. */
    @Query("select p.venta.id, sum(p.monto) from PagoVenta p join p.recibo r "
            + "where p.venta.id in :ventaIds and r.anulado = false group by p.venta.id")
    List<Object[]> totalVigentePorVenta(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    List<PagoVenta> findByReciboId(Long reciboId);

    List<PagoVenta> findByVentaIdOrderByFechaDesc(Long ventaId);

    List<PagoVenta> findByVentaIdOrderByFechaAscIdAsc(Long ventaId);
}
