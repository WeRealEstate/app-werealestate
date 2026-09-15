package com.werealestate.backend.repository;

import com.werealestate.backend.model.MovimientoLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MovimientoLoteRepository
        extends JpaRepository<MovimientoLote, Long>, JpaSpecificationExecutor<MovimientoLote> {
}
