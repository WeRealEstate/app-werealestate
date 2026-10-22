package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaComisionEntregaAbono;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaComisionEntregaAbonoRepository extends JpaRepository<VentaComisionEntregaAbono, Long> {

    List<VentaComisionEntregaAbono> findByEntregaIdOrderByFechaAbonoAscIdAsc(Long entregaId);
}
