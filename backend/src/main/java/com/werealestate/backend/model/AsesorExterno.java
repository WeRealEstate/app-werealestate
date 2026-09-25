package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol): solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un Usuario interno (ver
 * Venta.usuarioAsesor/asesorExterno).
 */
@Entity
@Table(name = "asesor_externo")
public class AsesorExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    // Nullable: se piden al crear uno nuevo (ver AsesorExternoCreateRequest), pero los ya
    // registrados antes de este campo no los tienen todavía.
    @Column(length = 20)
    private String celular;

    @Column(length = 150)
    private String correo;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected AsesorExterno() {
        // JPA
    }

    public AsesorExterno(String nombre, String celular, String correo) {
        this.nombre = nombre;
        this.celular = celular;
        this.correo = correo;
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

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
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
