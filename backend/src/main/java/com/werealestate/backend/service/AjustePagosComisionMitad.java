package com.werealestate.backend.service;

import java.math.BigDecimal;
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
    static final String CLAVE_RESTAURAR = "COMISIONES_RESTAURAR_RECORTE_S47";
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

    /**
     * 12.31.0: con la regla del enganche al 5 % el recorte de 12.30.1 ya no procede. En producción solo recortó una
     * comisión (registro del servidor: «Comisión 43 (venta S-47): lo pagado bajó de $9818.40 a $6734.00»); aquí se
     * regresa a su monto. Si el pago ya no está en el monto recortado (otra base de datos o se corrigió a mano), no hace
     * nada. Corre una sola vez.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(4)
    @Transactional
    public void restaurarRecorte() {
        Integer hecho = jdbc.queryForObject("select count(*) from ajuste_unico where clave = ?", Integer.class, CLAVE_RESTAURAR);
        if (hecho != null && hecho > 0) return;
        boolean cambio = comisionService.restaurarPagoRecortado(43L, new BigDecimal("6734.00"), new BigDecimal("9818.40"));
        jdbc.update("insert into ajuste_unico (clave, detalle) values (?, ?)", CLAVE_RESTAURAR,
                cambio ? "Comisión 43 (S-47) restaurada a 9818.40" : "Sin cambios: el pago no estaba en el monto recortado");
    }
}
