package com.werealestate.backend.repository;

import com.werealestate.backend.model.NotificacionLeida;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionLeidaRepository extends JpaRepository<NotificacionLeida, Long> {

    List<NotificacionLeida> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndTipoAndEntidadIdAndFirma(Long usuarioId, String tipo, Long entidadId, String firma);
}
