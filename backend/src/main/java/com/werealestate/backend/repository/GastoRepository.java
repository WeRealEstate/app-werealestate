package com.werealestate.backend.repository;

import com.werealestate.backend.model.Gasto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findAllByOrderByFechaDescIdDesc();

    boolean existsByTipoGastoId(Long tipoGastoId);
}
