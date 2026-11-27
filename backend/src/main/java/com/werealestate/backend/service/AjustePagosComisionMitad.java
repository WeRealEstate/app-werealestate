package com.werealestate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corre una sola vez (12.30.1, decisión del usuario): después de recalcular las comisiones con la regla de la mitad
 * ({@link ComisionService#recalcularConReglaUnica}), recorta lo que se les pagó de más a los asesores para que lo
 * pagado no pase de lo generado ({@link ComisionService#recortarPagosDeMas}). Queda anotado en {@code ajuste_unico}
 * para no repetirse: más adelante, un recibo anulado no debe recortar pagos ya hechos.
 */
@Component
public class AjustePagosComisionMitad {

    static final String CLAVE = "COMISIONES_PAGOS_A_LA_MITAD";
    private static final Logger log = LoggerFactory.getLogger(AjustePagosComisionMitad.class);

    private final JdbcTemplate jdbc;
    private final ComisionService comisionService;

    public AjustePagosComisionMitad(JdbcTemplate jdbc, ComisionService comisionService) {
        this.jdbc = jdbc;
        this.comisionService = comisionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    @Transactional
    public void aplicar() {
        Integer hecho = jdbc.queryForObject("select count(*) from ajuste_unico where clave = ?", Integer.class, CLAVE);
        if (hecho != null && hecho > 0) return;
        int cambiadas = comisionService.recortarPagosDeMas();
        jdbc.update("insert into ajuste_unico (clave, detalle) values (?, ?)", CLAVE,
                "Comisiones con pagos recortados: " + cambiadas);
        log.info("Pagos de comisión ajustados a la regla de la mitad: {} comisiones", cambiadas);
    }
}
