package com.werealestate.backend.dto;

import com.werealestate.backend.model.Cotizacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CotizacionDto(
        Long id,
        UsuarioResumenDto asesor,
        String proyecto,
        String nombreCliente,
        String manzana,
        String lote,
        BigDecimal superficie,
        BigDecimal precioM2,
        BigDecimal precioTotal,
        String formaPago,
        String engancheLabel,
        BigDecimal enganche,
        BigDecimal montoFinanciado,
        int meses,
        BigDecimal mensualidad,
        BigDecimal interesPorcentaje,
        BigDecimal interesMonto,
        BigDecimal totalInversion,
        LocalDateTime fechaCreacion) {

    public static CotizacionDto from(Cotizacion cotizacion) {
        return new CotizacionDto(
                cotizacion.getId(),
                UsuarioResumenDto.from(cotizacion.getAsesor()),
                cotizacion.getProyecto(),
                cotizacion.getNombreCliente(),
                cotizacion.getManzana(),
                cotizacion.getLote(),
                cotizacion.getSuperficie(),
                cotizacion.getPrecioM2(),
                cotizacion.getPrecioTotal(),
                cotizacion.getFormaPago(),
                cotizacion.getEngancheLabel(),
                cotizacion.getEnganche(),
                cotizacion.getMontoFinanciado(),
                cotizacion.getMeses(),
                cotizacion.getMensualidad(),
                cotizacion.getInteresPorcentaje(),
                cotizacion.getInteresMonto(),
                cotizacion.getTotalInversion(),
                cotizacion.getFechaCreacion());
    }
}
