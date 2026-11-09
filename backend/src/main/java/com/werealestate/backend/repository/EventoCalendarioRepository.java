package com.werealestate.backend.repository;

import com.werealestate.backend.model.EventoCalendario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoCalendarioRepository extends JpaRepository<EventoCalendario, Long> {

    List<EventoCalendario> findByUsuarioIdOrderByFechaAsc(Long usuarioId);

    /** Solo los eventos con recordatorio activo cuya fecha es mañana o anterior (para la campana de notificaciones). */
    List<EventoCalendario> findByUsuarioIdAndRecordatorioTrueAndFechaLessThanEqualOrderByFechaAsc(
            Long usuarioId, java.time.LocalDate hasta);

    boolean existsByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
