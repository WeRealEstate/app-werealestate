package com.werealestate.backend.repository;

import com.werealestate.backend.model.ReciboVenta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReciboVentaRepository extends JpaRepository<ReciboVenta, Long> {

    /** El folio de un recibo anulado queda libre para reutilizarse. */
    boolean existsByFolioIgnoreCaseAndAnuladoFalse(String folio);

    java.util.Optional<ReciboVenta> findByFolioIgnoreCaseAndAnuladoFalse(String folio);

    List<ReciboVenta> findByVentaId(Long ventaId);
}
