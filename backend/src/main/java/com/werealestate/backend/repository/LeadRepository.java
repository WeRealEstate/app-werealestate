package com.werealestate.backend.repository;

import com.werealestate.backend.model.Lead;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByAsesorIdOrderByFechaUltimoContactoAsc(Long asesorId);

    List<Lead> findAllByOrderByFechaUltimoContactoAsc();

    List<Lead> findByAsesorIdAndFechaUltimoContactoBeforeAndEstadoNotInOrderByFechaUltimoContactoAsc(
            Long asesorId, LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);

    List<Lead> findByFechaUltimoContactoBeforeAndEstadoNotInOrderByFechaUltimoContactoAsc(
            LocalDateTime limite, List<com.werealestate.backend.model.EstadoLead> estadosExcluidos);
}
