package com.werealestate.backend.repository;

import com.werealestate.backend.model.Tarea;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByAsignadoAIdOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc(Long asignadoAId);

    List<Tarea> findByCreadoPorIdOrderByFechaCreacionDesc(Long creadoPorId);

    boolean existsByAsignadoAId(Long asignadoAId);

    boolean existsByCreadoPorId(Long creadoPorId);
}
