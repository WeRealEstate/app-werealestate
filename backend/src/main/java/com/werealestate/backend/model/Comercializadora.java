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

/**
 * Una comercializadora: empresa que vende con su propio equipo. Su líder es un asesor externo de tipo LIDER (así
 * su equipo aparece en Comunidades We) y su {@code token} es el secreto del enlace que la líder comparte para que
 * cada integrante se dé de alta solo (ver ComercializadoraPublicoService).
 */
@Entity
@Table(name = "comercializadora")
public class Comercializadora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lider_id", nullable = false)
    private AsesorExterno lider;

    @Column(length = 1000)
    private String notas;

    /** Desactivada: su enlace deja de recibir altas (sus integrantes siguen donde están). */
    @Column(nullable = false)
    private boolean activa = true;

    @Column(nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Comercializadora() {
        // JPA
    }

    public Comercializadora(String nombre, AsesorExterno lider, String notas, String token) {
        this.nombre = nombre;
        this.lider = lider;
        this.notas = notas;
        this.token = token;
    }

    public void actualizar(String nombre, AsesorExterno lider, String notas, boolean activa) {
        this.nombre = nombre;
        this.lider = lider;
        this.notas = notas;
        this.activa = activa;
    }

    public void regenerarToken(String nuevo) {
        this.token = nuevo;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public AsesorExterno getLider() { return lider; }
    public String getNotas() { return notas; }
    public boolean isActiva() { return activa; }
    public String getToken() { return token; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
