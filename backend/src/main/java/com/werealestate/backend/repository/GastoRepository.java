package com.werealestate.backend.repository;

import com.werealestate.backend.model.Gasto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findAllByOrderByFechaDescIdDesc();

    boolean existsByTipoGastoId(Long tipoGastoId);

    @Query("select coalesce(sum(g.monto), 0) from Gasto g where g.fecha between :desde and :hasta")
    BigDecimal sumarEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
