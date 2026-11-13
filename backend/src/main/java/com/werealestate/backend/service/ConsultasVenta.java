package com.werealestate.backend.service;

import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.model.VentaReestructuracion;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Lecturas básicas de una venta que comparten sus servicios: la entidad, su precio, lo abonado y su saldo. */
@Component
public class ConsultasVenta {

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final PagoVentaRepository pagoVentaRepository;

    public ConsultasVenta(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            PagoVentaRepository pagoVentaRepository) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.pagoVentaRepository = pagoVentaRepository;
    }

    public Venta obtenerEntidad(Long id) {
        return ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
    }

    public BigDecimal precioVenta(Long ventaId) {
        return ventaLoteRepository.findByVentaId(ventaId).stream()
                .map(VentaLote::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo abonado de verdad: los recibos anulados ya no cuentan. */
    public BigDecimal totalAbonado(Long ventaId) {
        return pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(ventaId).stream()
                .map(PagoVenta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal saldoPendiente(Long ventaId) {
        return precioVenta(ventaId).subtract(totalAbonado(ventaId));
    }

    /** La venta solo muestra (y ofrece para cobrar) las aportaciones de su plan vigente; las de un plan reemplazado
     * siguen en la base para los recibos que ya las pagaron. */
    static boolean delPlanVigente(Venta venta, VentaAportacion a) {
        List<VentaReestructuracion> rs = venta.getReestructuraciones();
        Long vigente = rs.isEmpty() ? null : rs.get(rs.size() - 1).getId();
        return Objects.equals(vigente, a.getReestructuracionId());
    }
}
