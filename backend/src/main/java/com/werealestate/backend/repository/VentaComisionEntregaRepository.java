package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionEntrega;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionEntregaRepository extends JpaRepository<VentaComisionEntrega, Long> {

    List<VentaComisionEntrega> findByComisionIdOrderByFechaDescIdDesc(Long comisionId);

    List<VentaComisionEntrega> findAll();

    boolean existsByGastoId(Long gastoId);

    boolean existsByComisionId(Long comisionId);
}
