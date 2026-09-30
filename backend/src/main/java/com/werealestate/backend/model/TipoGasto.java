package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Categoría de gasto (ej. "Comisiones", "Renta de oficina") definida por un admin. requiereTicket
 * decide si al registrar un gasto de este tipo el comprobante es obligatorio (ver
 * GastoService.crear).
 */
@Entity
@Table(name = "tipo_gasto")
public class TipoGasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "requiere_ticket", nullable = false)
    private boolean requiereTicket;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected TipoGasto() {
        // JPA
    }

    public TipoGasto(String nombre, boolean requiereTicket) {
        this.nombre = nombre;
        this.requiereTicket = requiereTicket;
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

    public boolean isRequiereTicket() {
        return requiereTicket;
    }

    public void setRequiereTicket(boolean requiereTicket) {
        this.requiereTicket = requiereTicket;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
