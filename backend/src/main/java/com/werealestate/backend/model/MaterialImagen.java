package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Una imagen de una galería del Material de venta (material de categoría GALERIA). El archivo vive en la carpeta
 * privada (ver MaterialService.rutaImagen); aquí solo van sus datos y su lugar en la galería. */
@Entity
@Table(name = "material_imagen")
public class MaterialImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    /** Posición en la galería (0 = portada). */
    @Column(nullable = false)
    private int orden;

    /** Nombre con el que se descarga. */
    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String extension;

    @Column(nullable = false)
    private long tamano;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected MaterialImagen() {
        // JPA
    }

    public MaterialImagen(Material material, int orden, String nombre, String extension, long tamano) {
        this.material = material;
        this.orden = orden;
        this.nombre = nombre;
        this.extension = extension;
        this.tamano = tamano;
    }

    public void cambiarOrden(int orden) {
        this.orden = orden;
    }

    public Long getId() { return id; }
    public Material getMaterial() { return material; }
    public int getOrden() { return orden; }
    public String getNombre() { return nombre; }
    public String getExtension() { return extension; }
    public long getTamano() { return tamano; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
