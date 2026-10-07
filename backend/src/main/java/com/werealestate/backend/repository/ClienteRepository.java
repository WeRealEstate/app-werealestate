package com.werealestate.backend.repository;

import com.werealestate.backend.model.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCurpIgnoreCase(String curp);

    List<Cliente> findAllByOrderByNombreAscApellidoPaternoAsc();

    /** Para no borrar a quien figura como "captó" a un cliente (ver ficha). */
    boolean existsByCaptadoPorUsuarioId(Long usuarioId);

    boolean existsByCaptadoPorAsesorId(Long asesorId);
}
