package com.werealestate.backend.dto;

import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.TipoAbono;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Una línea de un recibo. Las líneas del mismo recibo comparten reciboId, folio, fecha y nota. */
public record PagoVentaDto(
        Long id,
        Long reciboId,
        LocalDate fecha,
        BigDecimal monto,
        // null en abonos anteriores a que se pidiera: se completan a mano.
        TipoAbono tipo,
        // Solo MENSUALIDAD: los meses que cubre, de más antiguo a más nuevo.
        List<MesCubiertoDto> meses,
        // Solo APORTACION: la aportación programada que paga.
        Long aportacionId,
        String folio,
        String notas,
        UsuarioResumenDto registradoPor,
        LocalDateTime fechaCreacion,
        // Recibo anulado: no cuenta en el saldo; se muestra con su motivo, quién y cuándo.
        boolean anulado,
        String anuladoMotivo,
        UsuarioResumenDto anuladoPor,
        LocalDate anuladoFecha) {

    public record MesCubiertoDto(int anio, int mes) {
    }

    public static PagoVentaDto from(PagoVenta pago) {
        return new PagoVentaDto(
                pago.getId(),
                pago.getRecibo().getId(),
                pago.getFecha(),
                pago.getMonto(),
                pago.getTipo(),
                pago.getMeses().stream()
                        .map(m -> new MesCubiertoDto(m.getAnio(), m.getMes()))
                        .sorted(Comparator.comparingInt(MesCubiertoDto::anio).thenComparingInt(MesCubiertoDto::mes))
                        .toList(),
                pago.getAportacion() != null ? pago.getAportacion().getId() : null,
                pago.getFolio(),
                pago.getNotas(),
                UsuarioResumenDto.from(pago.getRegistradoPor()),
                pago.getFechaCreacion(),
                pago.getRecibo().isAnulado(),
                pago.getRecibo().getAnuladoMotivo(),
                pago.getRecibo().getAnuladoPor() != null ? UsuarioResumenDto.from(pago.getRecibo().getAnuladoPor()) : null,
                pago.getRecibo().getAnuladoFecha());
    }
}
