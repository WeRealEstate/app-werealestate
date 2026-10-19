package com.werealestate.backend.repository;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LoteRepository extends JpaRepository<Lote, Long>, JpaSpecificationExecutor<Lote> {

    boolean existsByDesarrolloIdAndManzanaIgnoreCaseAndNumeroLoteIgnoreCase(
            Long desarrolloId, String manzana, String numeroLote);

    List<Lote> findByEstadoAndFechaCambioEstadoBefore(EstadoLote estado, LocalDateTime limite);

    List<Lote> findByEstadoAndFechaExpiraApartadoBefore(EstadoLote estado, LocalDateTime limite);

    List<Lote> findByDesarrolloIdAndEstadoOrderByManzanaAscNumeroLoteAsc(Long desarrolloId, EstadoLote estado);

    List<Lote> findByDesarrolloId(Long desarrolloId);

    /** Conteo de lotes por desarrollo y estado, resuelto en SQL (no carga los lotes), para el resumen del
     * inicio del admin. */
    @Query("select l.desarrollo.id as desarrolloId, l.estado as estado, count(l) as total "
            + "from Lote l group by l.desarrollo.id, l.estado")
    List<ConteoPorEstado> contarPorDesarrolloYEstado();

    interface ConteoPorEstado {
        Long getDesarrolloId();

        EstadoLote getEstado();

        Long getTotal();
    }
}
