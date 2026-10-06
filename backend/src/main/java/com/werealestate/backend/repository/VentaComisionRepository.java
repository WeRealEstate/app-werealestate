package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionRepository extends JpaRepository<VentaComision, Long> {

    Optional<VentaComision> findByVentaId(Long ventaId);

    boolean existsByVentaId(Long ventaId);

    List<VentaComision> findAllByOrderByIdDesc();
}
