package com.werealestate.backend.repository;

import com.werealestate.backend.model.Venta;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    /** Número de venta = posición según la fecha de venta (la más antigua es la 1); a igual fecha,
     * desempata el orden de registro. Se calcula sobre TODAS las ventas, no solo sobre las pedidas,
     * así que un número no cambia por filtrar, paginar ni ordenar la lista. */
    @Query(
            value = "select t.id as id, t.numero as numero from ("
                    + "select id, row_number() over (order by fecha_venta asc, id asc) as numero from venta"
                    + ") t where t.id in (:ids)",
            nativeQuery = true)
    List<NumeroVenta> numerosDe(@Param("ids") Collection<Long> ids);

    interface NumeroVenta {
        Long getId();

        Long getNumero();
    }

    boolean existsByUsuarioAsesorId(Long usuarioId);

    boolean existsByAsesorExternoId(Long asesorExternoId);
}
