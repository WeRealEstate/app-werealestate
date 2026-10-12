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

/** Una línea de la bitácora de un recibo: quién hizo qué y cuándo, con el detalle de lo que cambió. */
@Entity
@Table(name = "recibo_venta_historial")
public class ReciboVentaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_id", nullable = false)
    private ReciboVenta recibo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionRecibo accion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(length = 2000)
    private String detalle;

    protected ReciboVentaHistorial() {
        // JPA
    }

    public ReciboVentaHistorial(ReciboVenta recibo, AccionRecibo accion, Usuario usuario, String detalle) {
        this.recibo = recibo;
        this.accion = accion;
        this.usuario = usuario;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public ReciboVenta getRecibo() {
        return recibo;
    }

    public AccionRecibo getAccion() {
        return accion;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getDetalle() {
        return detalle;
    }
}
