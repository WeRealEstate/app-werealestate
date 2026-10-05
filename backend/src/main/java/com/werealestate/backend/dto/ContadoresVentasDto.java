package com.werealestate.backend.dto;

/** lotesVendidos = lotes distintos que aparecen en alguna venta; ventasRegistradas = operaciones.
 * Un cliente que compra 2 lotes juntos suma 2 lotes pero 1 venta. */
public record ContadoresVentasDto(long lotesVendidos, long ventasRegistradas) {
}
