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

/**
 * Una aportación programada de una venta ("Con aportaciones"): un mes y año concretos y un monto.
 * Una venta puede tener varias, incluso varias en el mismo año, cada una con su propio monto. Es
 * información de lo acordado con el cliente; lo que de verdad se paga se registra como PagoVenta.
 */
@Entity
@Table(name = "venta_aportacion")
public class VentaAportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false)
    private int anio;

    /** 1 = enero … 12 = diciembre. */
    @Column(nullable = false)
    private int mes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    protected VentaAportacion() {
        // JPA
    }

    public VentaAportacion(Venta venta, int anio, int mes, BigDecimal monto) {
        this.venta = venta;
        this.anio = anio;
        this.mes = mes;
        this.monto = monto;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public int getAnio() {
        return anio;
    }

    public int getMes() {
        return mes;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}
