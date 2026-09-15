package com.werealestate.backend.repository;

import com.werealestate.backend.model.Desarrollo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesarrolloRepository extends JpaRepository<Desarrollo, Long> {

    Optional<Desarrollo> findByNombre(String nombre);
}
