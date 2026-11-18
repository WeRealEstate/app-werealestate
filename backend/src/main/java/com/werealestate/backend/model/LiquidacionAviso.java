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

/** Un envío de los correos de liquidación de una venta (cliente + finanzas). Ver LiquidacionAvisoService. */
@Entity
@Table(name = "liquidacion_aviso")
public class LiquidacionAviso {

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

    /** Correo del titular al que se intentó escribir; null si no tenía (solo se avisó a finanzas). */
    @Column(name = "correo_cliente")
    private String correoCliente;

    @Column(name = "cliente_enviado", nullable = false)
    private boolean clienteEnviado;

    @Column(name = "finanzas_enviado", nullable = false)
    private boolean finanzasEnviado;

    protected LiquidacionAviso() {
    }

    public LiquidacionAviso(Venta venta, Usuario usuario, String correoCliente, boolean clienteEnviado,
            boolean finanzasEnviado) {
        this.venta = venta;
        this.usuario = usuario;
        this.enviadoEn = LocalDateTime.now();
        this.correoCliente = correoCliente;
        this.clienteEnviado = clienteEnviado;
        this.finanzasEnviado = finanzasEnviado;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public Usuario getUsuario() { return usuario; }
    public String getCorreoCliente() { return correoCliente; }
    public boolean isClienteEnviado() { return clienteEnviado; }
    public boolean isFinanzasEnviado() { return finanzasEnviado; }
}
