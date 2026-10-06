package com.werealestate.backend.repository;

import com.werealestate.backend.model.GastoRecurrentePago;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GastoRecurrentePagoRepository extends JpaRepository<GastoRecurrentePago, Long> {

    boolean existsByGastoId(Long gastoId);

    boolean existsByRecurrenteIdAndFechaVencimiento(Long recurrenteId, LocalDate fechaVencimiento);

    Optional<GastoRecurrentePago> findByGastoId(Long gastoId);

    boolean existsByRecurrenteIdAndGastoIsNotNull(Long recurrenteId);

    /** Los que siguen sin pagar ni omitir (pendientes o vencidos), del más próximo al más lejano. */
    @Query("select p from GastoRecurrentePago p where p.gasto is null and p.omitido = false order by p.fechaVencimiento asc, p.id asc")
    List<GastoRecurrentePago> findSinPagar();

    @Query("select p from GastoRecurrentePago p where p.recurrente.id = :id and p.gasto is null")
    List<GastoRecurrentePago> findSinGastoDe(@Param("id") Long recurrenteId);

    @Modifying
    @Query("delete from GastoRecurrentePago p where p.recurrente.id = :id and p.gasto is null and p.omitido = false")
    void borrarSinPagarDe(@Param("id") Long recurrenteId);
}
