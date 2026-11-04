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
        // Número de la venta en la que está el lote (null = en ninguna). Solo para admin y administración: con venta,
        // su estado lo manda la venta y no se cambia a mano (ver LoteService.cambiarEstado).
        Long ventaNumero,
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
                null,
                lote.getPrecioM2Efectivo(),
                lote.getPrecioM2(),
                lote.getPrecioM2Efectivo().multiply(lote.getSuperficie()).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Para /panel/lotes, /panel/plano y el formulario de Ventas (admin y administración): en qué venta está el lote
     * y si es un VENDIDO sin ninguna ("Reservado por We"). Ver LoteService.marcarVentas; en cualquier otro contexto
     * van en falso y null, no vale la pena la consulta extra. */
    public LoteDto conVenta(boolean sinVentaRegistrada, Long ventaNumero) {
        return new LoteDto(
                id, desarrollo, manzana, numeroLote, superficie, estado, fechaCambioEstado, cambiadoPor, mapaPoligono,
                fechaExpiraApartado, montoApartado, sinVentaRegistrada, ventaNumero, precioM2, precioM2Propio, precio);
    }
}
