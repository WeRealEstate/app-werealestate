package com.werealestate.backend.repository;

import com.werealestate.backend.model.ClienteNumero;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteNumeroRepository extends JpaRepository<ClienteNumero, Long> {

    Optional<ClienteNumero> findByClienteIdAndDesarrolloId(Long clienteId, Long desarrolloId);

    @Query("select n from ClienteNumero n join fetch n.desarrollo where n.cliente.id = :clienteId")
    List<ClienteNumero> deCliente(@Param("clienteId") Long clienteId);

    /** Todos, con su desarrollo, para armar la lista de clientes de una sola vez. */
    @Query("select n from ClienteNumero n join fetch n.desarrollo")
    List<ClienteNumero> todosConDesarrollo();

    /** Quiénes tienen ese número en ese desarrollo (el titular y sus copropietarios, si los hay). */
    @Query("select n from ClienteNumero n join fetch n.cliente where n.desarrollo.id = :desarrolloId and n.numero = :numero")
    List<ClienteNumero> conNumero(@Param("desarrolloId") Long desarrolloId, @Param("numero") long numero);

    @Query("select coalesce(max(n.numero), 0) from ClienteNumero n where n.desarrollo.id = :desarrolloId")
    long maxNumero(@Param("desarrolloId") Long desarrolloId);

    @Query("select distinct n.numero from ClienteNumero n where n.desarrollo.id = :desarrolloId and n.numero >= :desde")
    List<Long> numerosDesde(@Param("desarrolloId") Long desarrolloId, @Param("desde") long desde);

    /** Un candado por desarrollo hasta que termine la transacción: dos ventas al mismo tiempo no toman el mismo
     * número nuevo ni se cruzan con un cambio a mano. */
    @Query(value = "select cast(pg_advisory_xact_lock(39004, cast(:desarrolloId as int)) as text)", nativeQuery = true)
    String bloquear(@Param("desarrolloId") Long desarrolloId);

    /** Recorre +1 los números de ese desarrollo entre desde y hasta (todos los clientes de cada número juntos), menos
     * los clientes que se están moviendo (dejan su lugar). Sin índice único sobre (desarrollo, numero) no hace falta el
     * truco de negarlos. */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            update cliente_numero set numero = numero + 1
            where desarrollo_id = :desarrolloId and numero between :desde and :hasta and cliente_id not in (:excepto)""",
            nativeQuery = true)
    int recorrer(
            @Param("desarrolloId") Long desarrolloId,
            @Param("desde") long desde,
            @Param("hasta") long hasta,
            @Param("excepto") List<Long> excepto);

    /** Pone el número a esos clientes en ese desarrollo (un titular y sus copropietarios se mueven juntos). */
    @Modifying(clearAutomatically = true)
    @Query(value = "update cliente_numero set numero = :numero where desarrollo_id = :desarrolloId and cliente_id in (:clientes)",
            nativeQuery = true)
    int fijar(@Param("desarrolloId") Long desarrolloId, @Param("clientes") List<Long> clientes, @Param("numero") long numero);
}
