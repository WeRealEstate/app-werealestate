package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionDevengo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionDevengoRepository extends JpaRepository<VentaComisionDevengo, Long> {

    List<VentaComisionDevengo> findByComisionIdOrderByFechaEntregaAscIdAsc(Long comisionId);

    List<VentaComisionDevengo> findAllByOrderByFechaEntregaAscIdAsc();

    void deleteByComisionId(Long comisionId);
}
