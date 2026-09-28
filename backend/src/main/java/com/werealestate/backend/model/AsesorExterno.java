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

    // Teams: INDEPENDIENTE (default, trabaja solo) / LIDER (encabeza un equipo) / LINEA (reporta a
    // liderDirecto). Ver TipoAsesorExterno y AsesorExternoService.resolverJerarquia.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAsesorExterno tipo = TipoAsesorExterno.INDEPENDIENTE;

    /** Solo se llena cuando tipo = LINEA: el LIDER (línea 1) o LINEA de línea 1 (línea 2) al que
     * reporta. Null en INDEPENDIENTE/LIDER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lider_directo_id")
    private AsesorExterno liderDirecto;

    /** Solo se llena cuando tipo = LINEA: 1 si liderDirecto es un LIDER, 2 si liderDirecto es un
     * LINEA de línea 1 — nunca hay línea 3 (ver AsesorExternoService.resolverJerarquia). */
    @Column(name = "nivel_linea")
    private Integer nivelLinea;

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

    public TipoAsesorExterno getTipo() {
        return tipo;
    }

    public void setTipo(TipoAsesorExterno tipo) {
        this.tipo = tipo;
    }

    public AsesorExterno getLiderDirecto() {
        return liderDirecto;
    }

    public void setLiderDirecto(AsesorExterno liderDirecto) {
        this.liderDirecto = liderDirecto;
    }

    public Integer getNivelLinea() {
        return nivelLinea;
    }

    public void setNivelLinea(Integer nivelLinea) {
        this.nivelLinea = nivelLinea;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
