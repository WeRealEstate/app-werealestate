package com.werealestate.backend.repository;

import com.werealestate.backend.model.TipoGasto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGastoRepository extends JpaRepository<TipoGasto, Long> {

    List<TipoGasto> findAllByOrderByNombreAsc();

    List<TipoGasto> findByActivoTrueOrderByNombreAsc();

    Optional<TipoGasto> findFirstByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
