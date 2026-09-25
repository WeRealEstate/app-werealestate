package com.werealestate.backend.repository;

import com.werealestate.backend.model.AsesorExterno;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsesorExternoRepository extends JpaRepository<AsesorExterno, Long> {

    List<AsesorExterno> findByActivoTrueOrderByNombreAsc();

    List<AsesorExterno> findAllByOrderByNombreAsc();
}
