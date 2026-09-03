package com.werealestate.backend.repository;

import com.werealestate.backend.model.EventoCalendario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoCalendarioRepository extends JpaRepository<EventoCalendario, Long> {

    List<EventoCalendario> findByUsuarioIdOrderByFechaAsc(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
