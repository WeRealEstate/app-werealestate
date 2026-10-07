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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Comisión de una venta (ver ComisionService). Sobrevive a la venta: si esta se elimina, ventaId
 * queda en null y se conservan el cliente y el número que tenía. */
@Entity
@Table(name = "venta_comision")
public class VentaComision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Column(name = "venta_numero")
    private Long ventaNumero;

    @Column(nullable = false, length = 200)
    private String cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_asesor_id")
    private Usuario usuarioAsesor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_externo_id")
    private AsesorExterno asesorExterno;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal base;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal porcentaje;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    /** true cuando un admin/administración cambió el monto o el porcentaje a mano. */
    @Column(name = "monto_manual", nullable = false)
    private boolean montoManual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModalidadComision modalidad;

    @Column(nullable = false)
    private boolean cancelada;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected VentaComision() {
        // JPA
    }

    public VentaComision(
            Venta venta,
            String cliente,
            Usuario usuarioAsesor,
            AsesorExterno asesorExterno,
            BigDecimal base,
            BigDecimal porcentaje,
            BigDecimal monto,
            ModalidadComision modalidad) {
        this.venta = venta;
        this.cliente = cliente;
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
        this.base = base;
        this.porcentaje = porcentaje;
        this.monto = monto;
        this.modalidad = modalidad;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    /** La venta se eliminó: la comisión se queda en el historial con el número y cliente que tenía. */
    public void desvincularVenta(Long numeroDeLaVenta) {
        this.ventaNumero = numeroDeLaVenta;
        this.venta = null;
    }

    public Long getVentaNumero() {
        return ventaNumero;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public Usuario getUsuarioAsesor() {
        return usuarioAsesor;
    }

    public AsesorExterno getAsesorExterno() {
        return asesorExterno;
    }

    public void setAsesor(Usuario usuarioAsesor, AsesorExterno asesorExterno) {
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
    }

    public BigDecimal getBase() {
        return base;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMontoYPorcentaje(BigDecimal monto, BigDecimal porcentaje, boolean manual) {
        this.monto = monto;
        this.porcentaje = porcentaje;
        this.montoManual = manual;
    }

    public boolean isMontoManual() {
        return montoManual;
    }

    public ModalidadComision getModalidad() {
        return modalidad;
    }

    public void setModalidad(ModalidadComision modalidad) {
        this.modalidad = modalidad;
    }

    public boolean isCancelada() {
        return cancelada;
    }

    public void setCancelada(boolean cancelada) {
        this.cancelada = cancelada;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
