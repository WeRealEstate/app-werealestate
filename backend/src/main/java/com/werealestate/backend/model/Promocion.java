package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Promoción vigente del Cotizador (ej. "Mes Patrio"): fija la mensualidad de un proyecto y deja
 * que el cotizador calcule las aportaciones anuales necesarias para cuadrar con el precio del
 * terreno de cada cotización. Puede haber una promoción activa por proyecto al mismo tiempo.
 */
@Entity
@Table(name = "promocion")
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** 'samai' o 'nanuu', igual que el ProjectId del frontend. */
    @Column(nullable = false, length = 20)
    private String proyecto;

    @Column(name = "mensualidad_fija", nullable = false, precision = 14, scale = 2)
    private BigDecimal mensualidadFija;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Promocion() {
        // JPA
    }

    public Promocion(String nombre, String proyecto, BigDecimal mensualidadFija, String descripcion) {
        this.nombre = nombre;
        this.proyecto = proyecto;
        this.mensualidadFija = mensualidadFija;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getProyecto() {
        return proyecto;
    }

    public BigDecimal getMensualidadFija() {
        return mensualidadFija;
    }

    public void setMensualidadFija(BigDecimal mensualidadFija) {
        this.mensualidadFija = mensualidadFija;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
