package com.werealestate.backend.repository;

import com.werealestate.backend.model.CorreoAutomaticoConfig;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorreoAutomaticoConfigRepository extends JpaRepository<CorreoAutomaticoConfig, TipoCorreoAutomatico> {
}
