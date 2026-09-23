package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoteDto(
        Long id,
        DesarrolloDto desarrollo,
        String manzana,
        String numeroLote,
        BigDecimal superficie,
        EstadoLote estado,
        LocalDateTime fechaCambioEstado,
        UsuarioResumenDto cambiadoPor,
        List<PuntoMapaDto> mapaPoligono,
        LocalDateTime fechaExpiraApartado,
        BigDecimal montoApartado,
        boolean sinVentaRegistrada) {

    public static LoteDto from(Lote lote) {
        return new LoteDto(
                lote.getId(),
                DesarrolloDto.from(lote.getDesarrollo()),
                lote.getManzana(),
                lote.getNumeroLote(),
                lote.getSuperficie(),
                lote.getEstado(),
                lote.getFechaCambioEstado(),
                lote.getCambiadoPor() != null ? UsuarioResumenDto.from(lote.getCambiadoPor()) : null,
                PoligonoMapaJson.deserializar(lote.getMapaPoligonoJson()),
                lote.getFechaExpiraApartado(),
                lote.getMontoApartado(),
                false);
    }

    /** Para /panel/lotes: marca un lote VENDIDO que no tiene ningún VentaLote asociado, así el
     * inventario puede señalar cuáles ventas todavía faltan por registrar en el sistema (ver
     * LoteService.buscarPaginado). Falso en cualquier otro contexto — no vale la pena la consulta
     * extra fuera del listado principal. */
    public LoteDto conSinVentaRegistrada(boolean sinVentaRegistrada) {
        return new LoteDto(
                id, desarrollo, manzana, numeroLote, superficie, estado, fechaCambioEstado, cambiadoPor,
                mapaPoligono, fechaExpiraApartado, montoApartado, sinVentaRegistrada);
    }
}
