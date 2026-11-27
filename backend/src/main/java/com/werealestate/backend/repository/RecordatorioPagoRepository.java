package com.werealestate.backend.repository;

import com.werealestate.backend.model.RecordatorioPago;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordatorioPagoRepository extends JpaRepository<RecordatorioPago, Long> {

    Optional<RecordatorioPago> findFirstByVentaIdOrderByEnviadoEnDescIdDesc(Long ventaId);

    java.util.List<RecordatorioPago> findByVentaId(Long ventaId);

    java.util.List<RecordatorioPago> findByEnviadoEnGreaterThanEqualAndEnviadoEnLessThan(java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
}
