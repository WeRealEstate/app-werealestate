package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaCopropietario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaCopropietarioRepository extends JpaRepository<VentaCopropietario, Long> {

    List<VentaCopropietario> findByVentaIdOrderByOrdenAsc(Long ventaId);

    /** Los de varias ventas de una sola vez (lista de Ventas, ver VentaService.toDtos). */
    List<VentaCopropietario> findByVentaIdInOrderByOrdenAsc(java.util.Collection<Long> ventaIds);

    /** Las ventas en las que el cliente es copropietario (no el titular). */
    List<VentaCopropietario> findByClienteId(Long clienteId);
}
