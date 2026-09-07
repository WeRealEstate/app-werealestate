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

/**
 * Etiqueta que un asesor arma para organizar sus propios leads. Es privada: no se comparte entre
 * asesores, cada quien tiene su propio catálogo.
 */
@Entity
@Table(name = "etiqueta")
public class Etiqueta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtiquetaColor color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    protected Etiqueta() {
        // JPA
    }

    public Etiqueta(String nombre, EtiquetaColor color, Usuario asesor) {
        this.nombre = nombre;
        this.color = color;
        this.asesor = asesor;
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

    public EtiquetaColor getColor() {
        return color;
    }

    public void setColor(EtiquetaColor color) {
        this.color = color;
    }

    public Usuario getAsesor() {
        return asesor;
    }
}
