package com.werealestate.backend.dto;

import java.util.List;

/**
 * Lo que se borra al eliminar un cliente (ver EliminacionClienteService), para mostrarlo antes de confirmar.
 * ventas: las que tiene como titular (se eliminan completas, igual que al eliminar una venta: sus recibos se borran,
 * sus lotes vuelven a Disponible y sus comisiones se conservan como «Venta eliminada»). copropiedades: las ventas de
 * otro titular de las que solo se quita. numeros: sus números de cliente ("S-16"); los mayores de ese desarrollo
 * bajan 1 si nadie más comparte el número. registrosWeForms y archivosEnServidor: sus registros de venta de We Forms y
 * los archivos que se borran del servidor (lo que ya está en Google Drive no se toca).
 */
public record ClienteEliminacionDto(
        Long clienteId,
        String nombre,
        List<String> numeros,
        List<Venta> ventas,
        List<String> copropiedades,
        int registrosWeForms,
        int archivosEnServidor) {

    public record Venta(String etiqueta, List<String> lotes, int recibos, int comisiones) {
    }
}
