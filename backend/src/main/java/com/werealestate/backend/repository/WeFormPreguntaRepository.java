package com.werealestate.backend.repository;

import com.werealestate.backend.model.WeFormPregunta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeFormPreguntaRepository extends JpaRepository<WeFormPregunta, Long> {

    List<WeFormPregunta> findByFormIdAndArchivadaFalseOrderByOrdenAsc(Long formId);

    List<WeFormPregunta> findByFormIdOrderByOrdenAsc(Long formId);

    @Query("select coalesce(max(p.orden), -1) from WeFormPregunta p where p.form.id = :formId")
    int maxOrden(@Param("formId") Long formId);
}
