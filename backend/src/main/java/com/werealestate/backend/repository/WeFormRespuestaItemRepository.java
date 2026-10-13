package com.werealestate.backend.repository;

import com.werealestate.backend.model.WeFormRespuestaItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeFormRespuestaItemRepository extends JpaRepository<WeFormRespuestaItem, Long> {

    List<WeFormRespuestaItem> findByRespuestaIdOrderByOrdenAsc(Long respuestaId);

    List<WeFormRespuestaItem> findByRespuestaIdInOrderByOrdenAsc(Collection<Long> respuestaIds);
}
