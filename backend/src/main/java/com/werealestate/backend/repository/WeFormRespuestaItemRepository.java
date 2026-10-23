package com.werealestate.backend.repository;

import com.werealestate.backend.model.WeFormRespuestaItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeFormRespuestaItemRepository extends JpaRepository<WeFormRespuestaItem, Long> {

    List<WeFormRespuestaItem> findByRespuestaIdOrderByOrdenAsc(Long respuestaId);

    List<WeFormRespuestaItem> findByRespuestaIdInOrderByOrdenAsc(Collection<Long> respuestaIds);

    // ---- Subida a Google Drive (ver DriveService) ----

    /** Archivos que faltan por subir y no se han rendido, primero los que menos han fallado. */
    @Query("""
            select i.id from WeFormRespuestaItem i
            where i.archivoExtension is not null and i.driveArchivoId is null and i.driveIntentos < :maxIntentos
            order by i.driveIntentos asc, i.id asc""")
    List<Long> idsPendientesDeDrive(@Param("maxIntentos") int maxIntentos, Pageable pagina);

    @Query("select count(i) from WeFormRespuestaItem i where i.archivoExtension is not null and i.driveArchivoId is null and i.driveIntentos < :maxIntentos")
    long contarPendientesDeDrive(@Param("maxIntentos") int maxIntentos);

    @Query("select count(i) from WeFormRespuestaItem i where i.archivoExtension is not null and i.driveArchivoId is null and i.driveIntentos >= :maxIntentos")
    long contarFallidosDeDrive(@Param("maxIntentos") int maxIntentos);

    @Query("select count(i) from WeFormRespuestaItem i where i.driveArchivoId is not null")
    long contarSubidosADrive();

    /** "Reintentar": los que se rindieron vuelven a la fila de pendientes. */
    @Modifying
    @Query("update WeFormRespuestaItem i set i.driveIntentos = 0 where i.archivoExtension is not null and i.driveArchivoId is null")
    int reiniciarIntentosDeDrive();
}
