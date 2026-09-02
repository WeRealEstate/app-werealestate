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

/**
 * Columna extra que un asesor agrega a su tablero de tarjetas, además de los estados fijos del
 * lead. Es solo una posición visual: mover un lead aquí no cambia su estado real.
 */
@Entity
@Table(name = "columna_personalizada")
public class ColumnaPersonalizada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Column(nullable = false)
    private int orden;

    protected ColumnaPersonalizada() {
        // JPA
    }

    public ColumnaPersonalizada(String nombre, Usuario asesor, int orden) {
        this.nombre = nombre;
        this.asesor = asesor;
        this.orden = orden;
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

    public Usuario getAsesor() {
        return asesor;
    }

    public int getOrden() {
        return orden;
    }
}
