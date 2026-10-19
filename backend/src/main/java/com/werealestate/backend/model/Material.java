package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Una pieza de material de venta: un texto (copy) y/o un archivo (imagen, PDF o video). */
@Entity
@Table(name = "material")
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaMaterial categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProyectoMaterial proyecto;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(name = "archivo_nombre", length = 200)
    private String archivoNombre;

    @Column(name = "archivo_extension", length = 10)
    private String archivoExtension;

    @Column(name = "archivo_tamano")
    private Long archivoTamano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    protected Material() {
        // JPA
    }

    public Material(Usuario creadoPor) {
        this.creadoPor = creadoPor;
    }

    public void actualizarDatos(
            String titulo, String descripcion, CategoriaMaterial categoria, ProyectoMaterial proyecto, String texto) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.proyecto = proyecto;
        this.texto = texto;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void asignarArchivo(String nombre, String extension, long tamano) {
        this.archivoNombre = nombre;
        this.archivoExtension = extension;
        this.archivoTamano = tamano;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void quitarArchivo() {
        this.archivoNombre = null;
        this.archivoExtension = null;
        this.archivoTamano = null;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /** Marca el material como actualizado (p. ej. al cambiar las imágenes de su galería) para que suba en la lista. */
    public void tocar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    public boolean tieneArchivo() {
        return archivoExtension != null;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public CategoriaMaterial getCategoria() { return categoria; }
    public ProyectoMaterial getProyecto() { return proyecto; }
    public String getTexto() { return texto; }
    public String getArchivoNombre() { return archivoNombre; }
    public String getArchivoExtension() { return archivoExtension; }
    public Long getArchivoTamano() { return archivoTamano; }
    public Usuario getCreadoPor() { return creadoPor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
