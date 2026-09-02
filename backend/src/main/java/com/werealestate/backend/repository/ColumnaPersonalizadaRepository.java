package com.werealestate.backend.repository;

import com.werealestate.backend.model.ColumnaPersonalizada;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColumnaPersonalizadaRepository extends JpaRepository<ColumnaPersonalizada, Long> {

    List<ColumnaPersonalizada> findByAsesorIdOrderByOrdenAsc(Long asesorId);

    long countByAsesorId(Long asesorId);
}
