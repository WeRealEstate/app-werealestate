package com.werealestate.backend.repository;

import com.werealestate.backend.model.Material;
import com.werealestate.backend.model.ProyectoMaterial;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findAllByOrderByFechaActualizacionDesc();

    List<Material> findByProyectoInOrderByFechaActualizacionDesc(Collection<ProyectoMaterial> proyectos);
}
