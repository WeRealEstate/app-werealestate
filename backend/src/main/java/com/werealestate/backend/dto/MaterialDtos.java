package com.werealestate.backend.dto;

import com.werealestate.backend.model.CategoriaMaterial;
import com.werealestate.backend.model.Material;
import com.werealestate.backend.model.MaterialImagen;
import com.werealestate.backend.model.ProyectoMaterial;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs del Material de venta (panel y plano público). */
public final class MaterialDtos {

    private MaterialDtos() {}

    /** imagenes: las de su galería en orden (la primera es la portada); vacía en cualquier otra categoría. */
    public record Item(
            Long id, String titulo, String descripcion, CategoriaMaterial categoria, ProyectoMaterial proyecto,
            String texto, String archivoNombre, String archivoExtension, Long archivoTamano,
            LocalDateTime fechaActualizacion, List<Imagen> imagenes) {

        public static Item from(Material m) {
            return from(m, List.of());
        }

        public static Item from(Material m, List<MaterialImagen> imagenes) {
            return new Item(
                    m.getId(), m.getTitulo(), m.getDescripcion(), m.getCategoria(), m.getProyecto(), m.getTexto(),
                    m.getArchivoNombre(), m.getArchivoExtension(), m.getArchivoTamano(), m.getFechaActualizacion(),
                    imagenes.stream().map(Imagen::from).toList());
        }
    }

    public record Imagen(Long id, String nombre, String extension, long tamano) {

        public static Imagen from(MaterialImagen i) {
            return new Imagen(i.getId(), i.getNombre(), i.getExtension(), i.getTamano());
        }
    }

    /** Nuevo orden de una galería: todos los ids de sus imágenes, del primero (portada) al último. */
    public record OrdenImagenes(List<Long> ids) {}
}
