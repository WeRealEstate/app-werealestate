package com.werealestate.backend.repository;

import com.werealestate.backend.model.Comercializadora;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComercializadoraRepository extends JpaRepository<Comercializadora, Long> {

    List<Comercializadora> findAllByOrderByNombreAsc();

    Optional<Comercializadora> findByToken(String token);

    boolean existsByLiderId(Long liderId);

    boolean existsByNombreIgnoreCase(String nombre);
}
