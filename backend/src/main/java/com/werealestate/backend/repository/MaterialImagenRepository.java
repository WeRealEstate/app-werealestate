package com.werealestate.backend.repository;

import com.werealestate.backend.model.MaterialImagen;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialImagenRepository extends JpaRepository<MaterialImagen, Long> {

    List<MaterialImagen> findByMaterialIdOrderByOrdenAscIdAsc(Long materialId);

    /** Las imágenes de varios materiales en una sola consulta (para listar sin una consulta por tarjeta). */
    List<MaterialImagen> findByMaterialIdInOrderByOrdenAscIdAsc(Collection<Long> materialIds);

    long countByMaterialId(Long materialId);
}
