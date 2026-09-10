package com.werealestate.backend.repository;

import com.werealestate.backend.model.Cotizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    List<Cotizacion> findAllByOrderByFechaCreacionDesc();

    boolean existsByAsesorId(Long asesorId);
}
