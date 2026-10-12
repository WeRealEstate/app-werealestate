package com.werealestate.backend.repository;

import com.werealestate.backend.model.ReciboVentaHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReciboVentaHistorialRepository extends JpaRepository<ReciboVentaHistorial, Long> {

    @Query("select h from ReciboVentaHistorial h join fetch h.recibo r where r.venta.id = :ventaId order by h.fechaHora desc, h.id desc")
    List<ReciboVentaHistorial> findByVentaId(@Param("ventaId") Long ventaId);

    List<ReciboVentaHistorial> findByReciboId(Long reciboId);
}
