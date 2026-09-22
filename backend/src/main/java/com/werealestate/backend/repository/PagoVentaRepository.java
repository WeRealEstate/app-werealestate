package com.werealestate.backend.repository;

import com.werealestate.backend.model.PagoVenta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoVentaRepository extends JpaRepository<PagoVenta, Long> {

    List<PagoVenta> findByVentaIdOrderByFechaDesc(Long ventaId);
}
