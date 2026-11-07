package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaAportacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaAportacionRepository extends JpaRepository<VentaAportacion, Long> {

    List<VentaAportacion> findByVentaIdOrderByAnioAscMesAsc(Long ventaId);

    /** Las de varias ventas de una sola vez (lista de Ventas, ver VentaService.toDtos). */
    List<VentaAportacion> findByVentaIdInOrderByAnioAscMesAsc(java.util.Collection<Long> ventaIds);
}
