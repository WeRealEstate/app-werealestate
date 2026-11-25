package com.werealestate.backend.repository;

import com.werealestate.backend.model.BienvenidaCliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BienvenidaClienteRepository extends JpaRepository<BienvenidaCliente, Long> {

    Optional<BienvenidaCliente> findFirstByVentaIdOrderByEnviadoEnDescIdDesc(Long ventaId);

    java.util.List<BienvenidaCliente> findByVentaId(Long ventaId);
}
