package com.werealestate.backend.repository;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoteRepository extends JpaRepository<Lote, Long>, JpaSpecificationExecutor<Lote> {

    boolean existsByDesarrolloIdAndManzanaIgnoreCaseAndNumeroLoteIgnoreCase(
            Long desarrolloId, String manzana, String numeroLote);

    List<Lote> findByEstadoAndFechaCambioEstadoBefore(EstadoLote estado, LocalDateTime limite);

    List<Lote> findByEstadoAndFechaExpiraApartadoBefore(EstadoLote estado, LocalDateTime limite);

    List<Lote> findByDesarrolloIdAndEstadoOrderByManzanaAscNumeroLoteAsc(Long desarrolloId, EstadoLote estado);

    List<Lote> findByDesarrolloId(Long desarrolloId);
}
