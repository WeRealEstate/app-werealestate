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

/** Un correo de bienvenida que salió bien al cliente titular de una venta. Ver BienvenidaClienteService. */
@Entity
@Table(name = "bienvenida_cliente")
public class BienvenidaCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "correo_destino", nullable = false)
    private String correoDestino;

    protected BienvenidaCliente() {
    }

    public BienvenidaCliente(Venta venta, Usuario usuario, String correoDestino) {
        this.venta = venta;
        this.usuario = usuario;
        this.enviadoEn = LocalDateTime.now();
        this.correoDestino = correoDestino;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public Usuario getUsuario() { return usuario; }
    public String getCorreoDestino() { return correoDestino; }
}
