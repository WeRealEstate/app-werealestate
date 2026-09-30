package com.werealestate.backend.repository;

import com.werealestate.backend.model.TipoGasto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGastoRepository extends JpaRepository<TipoGasto, Long> {

    List<TipoGasto> findAllByOrderByNombreAsc();

    List<TipoGasto> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
