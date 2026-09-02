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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comisión generada automáticamente al cerrar un lead como ganado. El monto y el porcentaje
 * quedan fijos al momento de la creación (no cambian si luego se edita la configuración general).
 */
@Entity
@Table(name = "comision")
public class Comision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(name = "porcentaje_aplicado", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeAplicado;

    @Column(nullable = false)
    private boolean pagada = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    protected Comision() {
        // JPA
    }

    public Comision(Lead lead, Usuario asesor, BigDecimal monto, BigDecimal porcentajeAplicado) {
        this.lead = lead;
        this.asesor = asesor;
        this.monto = monto;
        this.porcentajeAplicado = porcentajeAplicado;
    }

    public Long getId() {
        return id;
    }

    public Lead getLead() {
        return lead;
    }

    public Usuario getAsesor() {
        return asesor;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getPorcentajeAplicado() {
        return porcentajeAplicado;
    }

    public boolean isPagada() {
        return pagada;
    }

    public void setPagada(boolean pagada) {
        this.pagada = pagada;
        this.fechaPago = pagada ? LocalDateTime.now() : null;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }
}
