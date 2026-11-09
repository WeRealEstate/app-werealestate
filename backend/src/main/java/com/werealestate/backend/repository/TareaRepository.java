package com.werealestate.backend.repository;

import com.werealestate.backend.model.Tarea;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByAsignadoAIdOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc(Long asignadoAId);

    /** Solo las tareas incompletas con fecha límite vencida o para hoy (evita cargar tareas completadas o futuras en la campana). */
    List<Tarea> findByAsignadoAIdAndCompletadaFalseAndFechaLimiteLessThanEqualOrderByFechaLimiteAscFechaCreacionDesc(
            Long asignadoAId, java.time.LocalDate hasta);

    List<Tarea> findByCreadoPorIdOrderByFechaCreacionDesc(Long creadoPorId);

    boolean existsByAsignadoAId(Long asignadoAId);

    boolean existsByCreadoPorId(Long creadoPorId);

    List<Tarea> findAllByOrderByCompletadaAscFechaLimiteAscFechaCreacionDesc();
}
