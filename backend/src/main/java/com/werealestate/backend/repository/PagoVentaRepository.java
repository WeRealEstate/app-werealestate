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

    /** Lo cobrado en total (solo recibos vigentes), sumado en la base: Finanzas → Valor vendido no necesita cada línea. */
    @Query("select coalesce(sum(p.monto), 0) from PagoVenta p join p.recibo r where r.anulado = false")
    java.math.BigDecimal totalVigente();

    /** Las líneas vigentes con los meses que cubre cada una ya cargados, en una sola consulta (no una por cada bloque
     * de pagos): cobro de toda la cartera, ver FinanzasService.Datos. Deja las colecciones listas en la sesión. */
    @Query("select p from PagoVenta p join p.recibo r left join fetch p.meses where r.anulado = false")
    List<PagoVenta> findAllVigentesConMeses();

    @Query("select p from PagoVenta p join fetch p.recibo r where p.venta.id = :ventaId and r.anulado = false order by p.fecha asc, p.id asc")
    List<PagoVenta> findVigentesByVentaIdOrderByFechaAscIdAsc(@Param("ventaId") Long ventaId);

    /** Las líneas vigentes de varias ventas a la vez (con su recibo), de la más antigua a la más nueva: el atraso por
     * mes de Clientes, ver ClienteService.Resumenes. */
    @Query("select p from PagoVenta p join fetch p.recibo r where p.venta.id in :ventaIds and r.anulado = false "
            + "order by p.fecha asc, p.id asc")
    List<PagoVenta> findVigentesByVentaIdIn(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    /** Lo abonado de cada venta (solo recibos vigentes), sumado en la base para varias ventas a la vez: filas
     * [ventaId, total]. Una venta sin abonos no aparece. Lista de Ventas, ver VentaService.toDtos. */
    @Query("select p.venta.id, sum(p.monto) from PagoVenta p join p.recibo r "
            + "where p.venta.id in :ventaIds and r.anulado = false group by p.venta.id")
    List<Object[]> totalVigentePorVenta(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    /** Todos los pagos cuya fecha de abono o fecha de anulación del recibo cae en el rango [desde, hasta]. */
    @Query("select p from PagoVenta p join fetch p.recibo r "
            + "where (p.fecha between :desde and :hasta) or (r.anulado = true and r.anuladoFecha between :desde and :hasta)")
    List<PagoVenta> findEnRangoConRecibo(@Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta);

    List<PagoVenta> findByReciboId(Long reciboId);

    List<PagoVenta> findByVentaIdOrderByFechaDesc(Long ventaId);

    List<PagoVenta> findByVentaIdOrderByFechaAscIdAsc(Long ventaId);
}
