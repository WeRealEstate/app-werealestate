package com.werealestate.backend.repository;

import com.werealestate.backend.model.MovimientoLote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoLoteRepository extends JpaRepository<MovimientoLote, Long> {

    List<MovimientoLote> findByLoteIdOrderByFechaDesc(Long loteId);
}
