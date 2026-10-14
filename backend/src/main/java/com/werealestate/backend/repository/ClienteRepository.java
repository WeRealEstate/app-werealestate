package com.werealestate.backend.repository;

import com.werealestate.backend.model.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCurpIgnoreCase(String curp);

    Optional<Cliente> findByNumero(Long numero);

    /** Serializa el reacomodo de números entre transacciones (dos clientes registrados a la vez). El
     * candado se suelta solo al terminar la transacción. */
    @Query(value = "select cast(pg_advisory_xact_lock(39003) as text)", nativeQuery = true)
    String bloquearNumeracion();

    /** El número más alto en uso (0 si no hay clientes): el siguiente automático es este + 1. */
    @Query("select coalesce(max(c.numero), 0) from Cliente c")
    long maxNumero();

    /** Los números en uso desde "desde" en adelante, de menor a mayor. */
    @Query(value = "select numero from cliente where numero >= :desde order by numero", nativeQuery = true)
    List<Long> numerosDesde(@Param("desde") long desde);

    // El índice único de numero no es diferido: para moverlo sin chocar a media operación, el cliente que
    // se mueve sale primero de su lugar (a un número negativo propio) y el rango se recorre en dos pasos
    // (a negativos y de regreso sumando 1). Todas vacían el contexto de persistencia: lo cargado antes
    // queda con el número viejo, hay que volver a leerlo.

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update cliente set numero = -1000000000 - id where id = :id", nativeQuery = true)
    int apartarDeSuLugar(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update cliente set numero = -numero where numero between :desde and :hasta and id <> :exceptoId",
            nativeQuery = true)
    int negarRango(@Param("desde") long desde, @Param("hasta") long hasta, @Param("exceptoId") long exceptoId);

    /** Paso final del recorrido: los negados (de -hasta a -desde) vuelven a positivo con +1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update cliente set numero = 1 - numero where numero between :negHasta and :negDesde",
            nativeQuery = true)
    int subirNegados(@Param("negHasta") long negHasta, @Param("negDesde") long negDesde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update cliente set numero = :numero where id = :id", nativeQuery = true)
    int fijarNumero(@Param("id") long id, @Param("numero") long numero);

    List<Cliente> findAllByOrderByNombreAscApellidoPaternoAsc();

    /** Para no borrar a quien figura como "captó" a un cliente (ver ficha). */
    boolean existsByCaptadoPorUsuarioId(Long usuarioId);

    boolean existsByCaptadoPorAsesorId(Long asesorId);
}
