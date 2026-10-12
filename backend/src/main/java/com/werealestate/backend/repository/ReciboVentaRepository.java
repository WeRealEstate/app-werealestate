package com.werealestate.backend.repository;

import com.werealestate.backend.model.ReciboVenta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReciboVentaRepository extends JpaRepository<ReciboVenta, Long> {

    boolean existsByFolioIgnoreCase(String folio);

    List<ReciboVenta> findByVentaId(Long ventaId);
}
