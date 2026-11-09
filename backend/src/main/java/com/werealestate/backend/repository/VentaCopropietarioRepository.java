package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaCopropietario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaCopropietarioRepository extends JpaRepository<VentaCopropietario, Long> {

    List<VentaCopropietario> findByVentaIdOrderByOrdenAsc(Long ventaId);

    /** Los de varias ventas de una sola vez (lista de Ventas, ver VentaService.toDtos). */
    List<VentaCopropietario> findByVentaIdInOrderByOrdenAsc(java.util.Collection<Long> ventaIds);

    /** Las ventas en las que el cliente es copropietario (no el titular). */
    @Query("select cp from VentaCopropietario cp join fetch cp.venta where cp.cliente.id = :clienteId")
    List<VentaCopropietario> findByClienteId(@Param("clienteId") Long clienteId);

    /** Las copropiedades de un subconjunto de clientes, con su venta y cliente cargados en una sola consulta. */
    @Query("select cp from VentaCopropietario cp join fetch cp.venta join fetch cp.cliente where cp.cliente.id in :clienteIds")
    List<VentaCopropietario> findByClienteIdIn(@Param("clienteIds") java.util.Collection<Long> clienteIds);
}
