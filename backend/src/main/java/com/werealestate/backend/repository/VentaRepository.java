package com.werealestate.backend.repository;

import com.werealestate.backend.model.Venta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    Optional<Venta> findByNumero(Long numero);

    /** La venta con ese número dentro de un desarrollo ("S-12" = SAMAI, 12). */
    Optional<Venta> findByDesarrolloIdAndNumeroDesarrollo(Long desarrolloId, Long numeroDesarrollo);

    /** Igual, buscando el desarrollo por su nombre (para abrir una venta desde su dirección /panel/ventas/S-12). */
    Optional<Venta> findByDesarrolloNombreAndNumeroDesarrollo(String desarrollo, Long numeroDesarrollo);

    /** La venta que ya lleva ese número de contrato (sin importar mayúsculas), si hay alguna. */
    Optional<Venta> findFirstByNumeroContratoIgnoreCase(String numeroContrato);

    /** Serializa el reacomodo de números entre transacciones (dos ventas registradas a la vez). El
     * candado se suelta solo al terminar la transacción. */
    @Query(value = "select cast(pg_advisory_xact_lock(39001) as text)", nativeQuery = true)
    String bloquearNumeracion();

    /** El número más alto en uso (0 si no hay ventas): el siguiente automático es este + 1. */
    @Query("select coalesce(max(v.numero), 0) from Venta v")
    long maxNumero();

    /** Los números en uso desde "desde" en adelante, de menor a mayor. */
    @Query(value = "select numero from venta where numero >= :desde order by numero", nativeQuery = true)
    java.util.List<Long> numerosDesde(@Param("desde") long desde);

    /** Suma 1 al número de las ventas entre desde y hasta (menos la que se está moviendo, exceptoId; 0 =
     * ninguna). El índice único es diferido, así que el corrimiento no choca a media operación. Vacía el
     * contexto de persistencia: lo cargado antes queda con el número viejo, hay que volver a leerlo. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update venta set numero = numero + 1 where numero between :desde and :hasta and id <> :exceptoId",
            nativeQuery = true)
    int recorrer(@Param("desde") long desde, @Param("hasta") long hasta, @Param("exceptoId") long exceptoId);

    /** Resta 1 al número de las ventas posteriores a la que se eliminó (numero > desde): así no queda hueco.
     * Mismo motivo que recorrer: el índice único es diferido. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update venta set numero = numero - 1 where numero > :desde", nativeQuery = true)
    int bajarDesde(@Param("desde") long desde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update venta set numero = :numero where id = :id", nativeQuery = true)
    int fijarNumero(@Param("id") long id, @Param("numero") long numero);

    // ---- Numeración por desarrollo (V77): lo mismo que el número global de arriba, acotado a un desarrollo.

    /** El número más alto de un desarrollo (0 si aún no tiene ventas): el siguiente automático es este + 1. */
    @Query("select coalesce(max(v.numeroDesarrollo), 0) from Venta v where v.desarrollo.id = :desarrolloId")
    long maxNumeroDesarrollo(@Param("desarrolloId") long desarrolloId);

    @Query(
            value = "select numero_desarrollo from venta where desarrollo_id = :desarrolloId and numero_desarrollo >= :desde order by numero_desarrollo",
            nativeQuery = true)
    java.util.List<Long> numerosDesarrolloDesde(@Param("desarrolloId") long desarrolloId, @Param("desde") long desde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update venta set numero_desarrollo = numero_desarrollo + 1 where desarrollo_id = :desarrolloId "
                    + "and numero_desarrollo between :desde and :hasta and id <> :exceptoId",
            nativeQuery = true)
    int recorrerDesarrollo(
            @Param("desarrolloId") long desarrolloId, @Param("desde") long desde, @Param("hasta") long hasta,
            @Param("exceptoId") long exceptoId);

    /** Resta 1 a las ventas de ese desarrollo posteriores a la eliminada: la numeración no queda con hueco. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update venta set numero_desarrollo = numero_desarrollo - 1 where desarrollo_id = :desarrolloId and numero_desarrollo > :desde",
            nativeQuery = true)
    int bajarDesarrolloDesde(@Param("desarrolloId") long desarrolloId, @Param("desde") long desde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update venta set numero_desarrollo = :numero where id = :id", nativeQuery = true)
    int fijarNumeroDesarrollo(@Param("id") long id, @Param("numero") long numero);

    boolean existsByUsuarioAsesorId(Long usuarioId);

    java.util.List<Venta> findByClienteRefId(Long clienteId);

    java.util.List<Venta> findByClienteRefIdIn(java.util.Collection<Long> clienteIds);

    java.util.List<Venta> findByFechaVentaBetween(java.time.LocalDate desde, java.time.LocalDate hasta);

    boolean existsByAsesorExternoId(Long asesorExternoId);

    /** Ventas registradas desde "desde" por otra persona (las anteriores a que se guardara quién las registró no avisan). */
    @Query("""
            select v from Venta v left join fetch v.registradaPor r
            where v.fechaCreacion >= :desde and (r is not null and r.id <> :usuarioId)
            order by v.fechaCreacion desc
            """)
    java.util.List<Venta> recientesDeOtros(
            @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde,
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);

    /** Todas las ventas con sus pausas ya cargadas, en una sola consulta (no una por cada bloque de ventas): para
     * cálculos de toda la cartera, ver FinanzasService.Datos. Deja las colecciones listas en la sesión. */
    @Query("select v from Venta v left join fetch v.pausas")
    java.util.List<Venta> findAllConPausas();

    /** Igual que {@link #findAllConPausas()} con las reestructuraciones (van aparte: dos colecciones List no se pueden
     * traer en la misma consulta). */
    @Query("select v from Venta v left join fetch v.reestructuraciones")
    java.util.List<Venta> findAllConReestructuraciones();
}
