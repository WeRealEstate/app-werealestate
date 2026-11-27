package com.werealestate.backend.repository;

import com.werealestate.backend.model.CorreoAutomatico;
import com.werealestate.backend.model.EstadoCorreoAutomatico;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import com.werealestate.backend.model.Venta;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CorreoAutomaticoRepository extends JpaRepository<CorreoAutomatico, Long> {

    List<CorreoAutomatico> findByEstadoOrderById(EstadoCorreoAutomatico estado);

    List<CorreoAutomatico> findByVentaIdOrderByCreadoEnDescIdDesc(Long ventaId);

    List<CorreoAutomatico> findByResueltoEnGreaterThanEqualAndResueltoEnLessThan(LocalDateTime desde, LocalDateTime hasta);

    boolean existsByVentaIdAndTipoAndFechaPago(Long ventaId, TipoCorreoAutomatico tipo, LocalDate fechaPago);

    /** Ventas nuevas con el contrato firmado sobre las que aún no se decide la bienvenida. */
    @Query("""
            select v from Venta v where v.correosAutomaticos = true and v.contratoFirmado = true
              and not exists (select c.id from CorreoAutomatico c where c.venta = v
                  and c.tipo = com.werealestate.backend.model.TipoCorreoAutomatico.BIENVENIDA)
            """)
    List<Venta> firmadasSinBienvenida();

    /** Ventas nuevas ya pagadas por completo sobre las que aún no se deciden los correos de liquidación. */
    @Query("""
            select v from Venta v where v.correosAutomaticos = true
              and not exists (select c.id from CorreoAutomatico c where c.venta = v
                  and c.tipo = com.werealestate.backend.model.TipoCorreoAutomatico.LIQUIDACION)
              and (select coalesce(sum(p.monto), 0) from PagoVenta p where p.venta = v and p.recibo.anulado = false) > 0
              and (select coalesce(sum(p.monto), 0) from PagoVenta p where p.venta = v and p.recibo.anulado = false)
                  >= (select coalesce(sum(l.precio), 0) from VentaLote l where l.venta = v) - 0.01
            """)
    List<Venta> liquidadasSinAviso();

    /** Ventas con mensualidades a las que se les puede recordar el pago. */
    @Query("select v from Venta v where v.plazoMeses is not null and v.sinRecordatorioAutomatico = false")
    List<Venta> conRecordatorio();
}
