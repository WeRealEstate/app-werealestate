package com.werealestate.backend.repository;

import com.werealestate.backend.model.Comision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComisionRepository extends JpaRepository<Comision, Long> {

    List<Comision> findByAsesorIdOrderByFechaCreacionDesc(Long asesorId);

    List<Comision> findAllByOrderByFechaCreacionDesc();

    boolean existsByLeadId(Long leadId);
}
