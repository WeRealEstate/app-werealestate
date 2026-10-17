package com.werealestate.backend.dto;

import com.werealestate.backend.model.CategoriaMaterial;
import com.werealestate.backend.model.Material;
import com.werealestate.backend.model.ProyectoMaterial;
import java.time.LocalDateTime;

/** DTOs del Material de venta (panel y plano público). */
public final class MaterialDtos {

    private MaterialDtos() {}

    public record Item(
            Long id, String titulo, String descripcion, CategoriaMaterial categoria, ProyectoMaterial proyecto,
            String texto, String archivoNombre, String archivoExtension, Long archivoTamano,
            LocalDateTime fechaActualizacion) {

        public static Item from(Material m) {
            return new Item(
                    m.getId(), m.getTitulo(), m.getDescripcion(), m.getCategoria(), m.getProyecto(), m.getTexto(),
                    m.getArchivoNombre(), m.getArchivoExtension(), m.getArchivoTamano(), m.getFechaActualizacion());
        }
    }
}
