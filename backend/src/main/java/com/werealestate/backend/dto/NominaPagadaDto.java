package com.werealestate.backend.dto;

import java.math.BigDecimal;

/** Resultado de pagar la nómina de un sábado: cuántas personas y cuánto en total. */
public record NominaPagadaDto(int pagados, BigDecimal total) {
}
