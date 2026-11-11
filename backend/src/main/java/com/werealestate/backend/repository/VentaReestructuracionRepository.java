package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaReestructuracion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaReestructuracionRepository extends JpaRepository<VentaReestructuracion, Long> {

    List<VentaReestructuracion> findByVentaIdOrderByIdAsc(Long ventaId);
}
