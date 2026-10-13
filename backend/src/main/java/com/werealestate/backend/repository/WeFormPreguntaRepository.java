package com.werealestate.backend.repository;

import com.werealestate.backend.model.WeFormPregunta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeFormPreguntaRepository extends JpaRepository<WeFormPregunta, Long> {

    List<WeFormPregunta> findByFormIdAndArchivadaFalseOrderByOrdenAsc(Long formId);

    List<WeFormPregunta> findByFormIdOrderByOrdenAsc(Long formId);
}
