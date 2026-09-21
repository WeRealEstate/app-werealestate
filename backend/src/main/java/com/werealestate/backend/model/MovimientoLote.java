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

/** Bitácora de cada cambio de estado de un lote: quién lo hizo (usuario autenticado, o nadie si
 * fue la reversión automática por vencimiento) y, si vino de /cotizador-publico/lotes, el nombre
 * del asesor que capturó el visitante y el del cliente que apartó — ahí no hay una sesión real
 * detrás. */
@Entity
@Table(name = "movimiento_lote")
public class MovimientoLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 30)
    private EstadoLote estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private EstadoLote estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "nombre_asesor", length = 150)
    private String nombreAsesor;

    @Column(name = "nombre_cliente", length = 150)
    private String nombreCliente;

    @Column(length = 500)
    private String nota;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    protected MovimientoLote() {
        // JPA
    }

    public MovimientoLote(
            Lote lote,
            EstadoLote estadoAnterior,
            EstadoLote estadoNuevo,
            Usuario usuario,
            String nombreAsesor,
            String nombreCliente,
            String nota) {
        this.lote = lote;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.nombreAsesor = nombreAsesor;
        this.nombreCliente = nombreCliente;
        this.nota = nota;
    }

    public Long getId() {
        return id;
    }

    public Lote getLote() {
        return lote;
    }

    public EstadoLote getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoLote getEstadoNuevo() {
        return estadoNuevo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNombreAsesor() {
        return nombreAsesor;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public String getNota() {
        return nota;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
