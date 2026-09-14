package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReporteCotizacionesDto(long total, BigDecimal montoTotal, List<ReporteConteoDto> porProyecto) {
}
