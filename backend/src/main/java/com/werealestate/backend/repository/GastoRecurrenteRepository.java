package com.werealestate.backend.repository;

import com.werealestate.backend.model.GastoRecurrente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRecurrenteRepository extends JpaRepository<GastoRecurrente, Long> {

    List<GastoRecurrente> findAllByOrderByNombreAsc();

    List<GastoRecurrente> findByActivoTrue();

    Optional<GastoRecurrente> findByUsuarioId(Long usuarioId);
}
