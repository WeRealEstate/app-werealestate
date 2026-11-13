package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionDevolucion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaComisionDevolucionRepository extends JpaRepository<VentaComisionDevolucion, Long> {

    List<VentaComisionDevolucion> findByComisionIdOrderByFechaAscIdAsc(Long comisionId);

    boolean existsByComisionId(Long comisionId);

    @Query("select coalesce(sum(d.monto), 0) from VentaComisionDevolucion d where d.fecha between :desde and :hasta")
    BigDecimal sumarEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
