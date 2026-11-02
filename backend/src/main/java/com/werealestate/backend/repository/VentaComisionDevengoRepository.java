package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionDevengo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VentaComisionDevengoRepository extends JpaRepository<VentaComisionDevengo, Long> {

    List<VentaComisionDevengo> findByComisionIdOrderByFechaEntregaAscIdAsc(Long comisionId);

    /** Igual, pero con el abono de cada uno ya cargado (para mostrar su tipo y monto sin una consulta por renglón). */
    @Query("select d from VentaComisionDevengo d left join fetch d.pagoVenta order by d.fechaEntrega asc, d.id asc")
    List<VentaComisionDevengo> findAllConPagoOrdenados();

    void deleteByComisionId(Long comisionId);
}
