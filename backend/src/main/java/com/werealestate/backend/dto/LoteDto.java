package com.werealestate.backend.dto;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.VisibilidadLote;
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
        boolean sinVentaRegistrada,
        // Precio por m² que aplica (el propio del lote o el del desarrollo), el propio (null si no tiene) y el total.
        BigDecimal precioM2,
        BigDecimal precioM2Propio,
        BigDecimal precio) {

    public static LoteDto from(Lote lote) {
        return de(lote, VisibilidadLote.visible(lote.getEstado()));
    }

    /** Para los endpoints públicos (/api/lotes/publico/**): el estado siempre como lo ve el público, aunque la petición
     * traiga la sesión de un admin (ver {@link VisibilidadLote#comoPublico}). */
    public static LoteDto paraPublico(Lote lote) {
        return de(lote, VisibilidadLote.comoPublico(lote.getEstado()));
    }

    private static LoteDto de(Lote lote, EstadoLote estadoVisible) {
        return new LoteDto(
                lote.getId(),
                DesarrolloDto.from(lote.getDesarrollo()),
                lote.getManzana(),
                lote.getNumeroLote(),
                lote.getSuperficie(),
                estadoVisible,
                lote.getFechaCambioEstado(),
                lote.getCambiadoPor() != null ? UsuarioResumenDto.from(lote.getCambiadoPor()) : null,
                PoligonoMapaJson.deserializar(lote.getMapaPoligonoJson()),
                lote.getFechaExpiraApartado(),
                lote.getMontoApartado(),
                false,
                lote.getPrecioM2Efectivo(),
                lote.getPrecioM2(),
                lote.getPrecioM2Efectivo().multiply(lote.getSuperficie()).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Para /panel/lotes: marca un lote VENDIDO que no tiene ningún VentaLote asociado, así el
     * inventario puede señalar cuáles ventas todavía faltan por registrar en el sistema (ver
     * LoteService.buscarPaginado). Falso en cualquier otro contexto — no vale la pena la consulta
     * extra fuera del listado principal. */
    public LoteDto conSinVentaRegistrada(boolean sinVentaRegistrada) {
        return new LoteDto(
                id, desarrollo, manzana, numeroLote, superficie, estado, fechaCambioEstado, cambiadoPor,
                mapaPoligono, fechaExpiraApartado, montoApartado, sinVentaRegistrada, precioM2, precioM2Propio, precio);
    }
}
