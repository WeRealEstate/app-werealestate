package com.werealestate.backend.repository;

import com.werealestate.backend.model.LiquidacionAviso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiquidacionAvisoRepository extends JpaRepository<LiquidacionAviso, Long> {

    Optional<LiquidacionAviso> findFirstByVentaIdOrderByEnviadoEnDescIdDesc(Long ventaId);

    java.util.List<LiquidacionAviso> findByVentaId(Long ventaId);

    java.util.List<LiquidacionAviso> findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
}
