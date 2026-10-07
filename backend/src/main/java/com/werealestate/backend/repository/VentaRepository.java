package com.werealestate.backend.repository;

import com.werealestate.backend.model.Venta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    Optional<Venta> findByNumero(Long numero);

    /** Serializa el reacomodo de números entre transacciones (dos ventas registradas a la vez). El
     * candado se suelta solo al terminar la transacción. */
    @Query(value = "select cast(pg_advisory_xact_lock(39001) as text)", nativeQuery = true)
    String bloquearNumeracion();

    /** Reasigna el número de TODAS las ventas según fecha de venta y, a igual fecha, orden de
     * registro (la más antigua es la 1). Solo toca las filas cuyo número cambia. Vacía el contexto de
     * persistencia: las entidades cargadas antes quedan con el número viejo, hay que volver a leerlas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "update venta v set numero = r.n from ("
                    + "select id, row_number() over (order by fecha_venta asc, id asc) as n from venta"
                    + ") r where v.id = r.id and v.numero is distinct from r.n",
            nativeQuery = true)
    int renumerar();

    boolean existsByUsuarioAsesorId(Long usuarioId);

    java.util.List<Venta> findByClienteRefId(Long clienteId);

    boolean existsByAsesorExternoId(Long asesorExternoId);
}
