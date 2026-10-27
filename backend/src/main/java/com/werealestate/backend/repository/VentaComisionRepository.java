package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionRepository extends JpaRepository<VentaComision, Long> {

    boolean existsByAsesorExternoId(Long asesorExternoId);

    /** La comisión del asesor principal de la venta. */
    Optional<VentaComision> findByVentaIdAndSecundariaFalse(Long ventaId);

    /** La comisión del copropietario (segundo asesor), si la venta lo tiene. */
    Optional<VentaComision> findByVentaIdAndSecundariaTrue(Long ventaId);

    /** Comisiones vigentes de los copropietarios (segundo asesor) de esas ventas (para el Score de ventas). */
    List<VentaComision> findByVentaIdInAndSecundariaTrueAndCanceladaFalse(java.util.Collection<Long> ventaIds);

    /** Las comisiones de la venta (principal y, si hay, la del copropietario). */
    List<VentaComision> findAllByVentaId(Long ventaId);

    boolean existsByVentaIdAndSecundariaFalse(Long ventaId);

    List<VentaComision> findAllByOrderByIdDesc();
}
