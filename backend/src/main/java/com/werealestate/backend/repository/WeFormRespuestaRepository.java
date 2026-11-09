package com.werealestate.backend.repository;

import com.werealestate.backend.model.WeFormRespuesta;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeFormRespuestaRepository extends JpaRepository<WeFormRespuesta, Long> {

    boolean existsByAsesorExternoId(Long asesorExternoId);

    Page<WeFormRespuesta> findByFormIdOrderByFechaEnvioDesc(Long formId, Pageable pageable);

    List<WeFormRespuesta> findByFormIdOrderByFechaEnvioDesc(Long formId);

    long countByFormId(Long formId);

    /** Total de respuestas por formulario en una sola consulta: filas [formId, total]. */
    @Query("select r.form.id, count(r) from WeFormRespuesta r group by r.form.id")
    List<Object[]> contarPorForm();

    /** Conteo de respuestas agrupado por nombre de desarrollo y nombre de asesor: filas [desarrolloNombre, asesorNombre, total]. */
    @Query("select r.form.desarrollo.nombre, r.asesorNombre, count(r) from WeFormRespuesta r group by r.form.desarrollo.nombre, r.asesorNombre")
    List<Object[]> contarPorDesarrolloYAsesor();

    /** Serializa los envíos de registros de venta (dos asesores tomando el mismo lote a la vez). Se suelta al terminar la transacción. */
    @Query(value = "select cast(pg_advisory_xact_lock(39002) as text)", nativeQuery = true)
    String bloquearEnvios();

    boolean existsByVentaId(Long ventaId);

    /** De un conjunto de ventas, cuáles tienen registro en We Forms (una sola consulta para NotificacionService). */
    @Query("select distinct r.venta.id from WeFormRespuesta r where r.venta.id in :ventaIds")
    List<Long> ventaIdsConRespuesta(@Param("ventaIds") java.util.Collection<Long> ventaIds);

    /** Al eliminar una venta, el registro de venta (y sus documentos) se conserva pero ya no apunta a ella. */
    @Modifying(flushAutomatically = true)
    @Query("update WeFormRespuesta r set r.venta = null where r.venta.id = :ventaId")
    void desvincularVenta(@Param("ventaId") Long ventaId);

    @Query("select r from WeFormRespuesta r left join fetch r.venta left join fetch r.cliente where r.fechaEnvio >= :desde order by r.fechaEnvio desc")
    List<WeFormRespuesta> recientes(@Param("desde") LocalDateTime desde);
}
