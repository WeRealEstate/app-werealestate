package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaLote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaLoteRepository extends JpaRepository<VentaLote, Long> {

    List<VentaLote> findByVentaId(Long ventaId);
}
