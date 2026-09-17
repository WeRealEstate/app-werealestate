package com.werealestate.backend.repository;

import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromocionRepository extends JpaRepository<Promocion, Long> {

    List<Promocion> findAllByOrderByFechaCreacionDesc();

    List<Promocion> findByActivaTrue();

    List<Promocion> findByProyectoAndTipoPrecioAndActivaTrue(String proyecto, TipoPrecioPromocion tipoPrecio);
}
