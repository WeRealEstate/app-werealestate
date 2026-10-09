package com.werealestate.backend.repository;

import com.werealestate.backend.model.VentaCopropietario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaCopropietarioRepository extends JpaRepository<VentaCopropietario, Long> {

    List<VentaCopropietario> findByVentaIdOrderByOrdenAsc(Long ventaId);
}
