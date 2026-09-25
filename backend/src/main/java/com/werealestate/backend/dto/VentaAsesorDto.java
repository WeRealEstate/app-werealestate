package com.werealestate.backend.dto;

import com.werealestate.backend.model.Venta;

/** Asesor de una venta, sea interno o externo: el frontend necesita saber cuál de los dos es para
 * preseleccionar la opción correcta al editar (ver Venta.usuarioAsesor/asesorExterno). */
public record VentaAsesorDto(Long id, String nombre, boolean externo) {

    public static VentaAsesorDto from(Venta venta) {
        if (venta.getUsuarioAsesor() != null) {
            return new VentaAsesorDto(venta.getUsuarioAsesor().getId(), venta.getUsuarioAsesor().getNombre(), false);
        }
        return new VentaAsesorDto(venta.getAsesorExterno().getId(), venta.getAsesorExterno().getNombre(), true);
    }
}
