package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaLote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaLoteRepository extends JpaRepository<VentaLote, Long> {

    List<VentaLote> findByVentaId(Long ventaId);

    /** Más reciente primero: si un lote llegó a estar en más de una venta (ej. una venta cancelada
     * a mano y vuelto a vender), nos interesa la última. */
    Optional<VentaLote> findFirstByLoteIdOrderByIdDesc(Long loteId);
}
