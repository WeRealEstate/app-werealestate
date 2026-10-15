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

/** Un formulario de We Forms (hoy: el registro de venta de un desarrollo) con su link público (slug). */
@Entity
@Table(name = "we_form")
public class WeForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 40, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "desarrollo_id", nullable = false)
    private Desarrollo desarrollo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoWeForm estado = EstadoWeForm.ACTIVO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected WeForm() {
        // JPA
    }

    public WeForm(String nombre, String descripcion, String slug, Desarrollo desarrollo, Usuario creadoPor) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.slug = slug;
        this.desarrollo = desarrollo;
        this.creadoPor = creadoPor;
    }

    public void actualizar(String nombre, String descripcion, EstadoWeForm estado) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getSlug() { return slug; }
    public Desarrollo getDesarrollo() { return desarrollo; }
    public EstadoWeForm getEstado() { return estado; }
    public Usuario getCreadoPor() { return creadoPor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
