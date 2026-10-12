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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update venta set numero = :numero where id = :id", nativeQuery = true)
    int fijarNumero(@Param("id") long id, @Param("numero") long numero);

    boolean existsByUsuarioAsesorId(Long usuarioId);

    java.util.List<Venta> findByClienteRefId(Long clienteId);

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
}
