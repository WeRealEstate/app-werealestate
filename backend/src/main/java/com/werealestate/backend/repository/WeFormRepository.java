package com.werealestate.backend.repository;

import com.werealestate.backend.model.EstadoWeForm;
import com.werealestate.backend.model.WeForm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeFormRepository extends JpaRepository<WeForm, Long> {

    Optional<WeForm> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<WeForm> findAllByOrderByFechaCreacionDesc();

    List<WeForm> findByDesarrolloIdAndEstadoOrderByNombreAsc(Long desarrolloId, EstadoWeForm estado);
}
