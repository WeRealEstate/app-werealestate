package com.werealestate.backend.repository;

import com.werealestate.backend.model.Cotizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long>, JpaSpecificationExecutor<Cotizacion> {

    List<Cotizacion> findAllByOrderByFechaCreacionDesc();

    boolean existsByAsesorId(Long asesorId);
}
