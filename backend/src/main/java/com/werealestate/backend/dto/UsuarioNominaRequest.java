package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** nominaSemanal null o 0 = quitar la nómina. nominaDesde null = el próximo sábado; si cae entre
 * semana se empieza el primer sábado desde esa fecha. */
public record UsuarioNominaRequest(BigDecimal nominaSemanal, LocalDate nominaDesde) {
}
