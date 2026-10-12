package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionDevolucion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionDevolucionRepository extends JpaRepository<VentaComisionDevolucion, Long> {

    List<VentaComisionDevolucion> findByComisionIdOrderByFechaAscIdAsc(Long comisionId);
}
