package com.werealestate.backend.repository;

import com.werealestate.backend.model.LiquidacionAviso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiquidacionAvisoRepository extends JpaRepository<LiquidacionAviso, Long> {

    Optional<LiquidacionAviso> findFirstByVentaIdOrderByEnviadoEnDescIdDesc(Long ventaId);
}
