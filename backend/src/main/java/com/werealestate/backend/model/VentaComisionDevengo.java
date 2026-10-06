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
import java.time.LocalDate;

/** Una porción de la comisión ya ganada: la mitad de un abono (o la comisión completa en una
 * exhibición), con el sábado en que toca entregarla. Se regenera completa cada vez que cambia algo
 * de la venta (ver ComisionService.reconstruirDevengos). */
@Entity
@Table(name = "venta_comision_devengo")
public class VentaComisionDevengo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comision_id", nullable = false)
    private VentaComision comision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_venta_id")
    private PagoVenta pagoVenta;

    @Column(name = "fecha_origen", nullable = false)
    private LocalDate fechaOrigen;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    protected VentaComisionDevengo() {
        // JPA
    }

    public VentaComisionDevengo(
            VentaComision comision, PagoVenta pagoVenta, LocalDate fechaOrigen, LocalDate fechaEntrega, BigDecimal monto) {
        this.comision = comision;
        this.pagoVenta = pagoVenta;
        this.fechaOrigen = fechaOrigen;
        this.fechaEntrega = fechaEntrega;
        this.monto = monto;
    }

    public Long getId() {
        return id;
    }

    public VentaComision getComision() {
        return comision;
    }

    public LocalDate getFechaOrigen() {
        return fechaOrigen;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}
