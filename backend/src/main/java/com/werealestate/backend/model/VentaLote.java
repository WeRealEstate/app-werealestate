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
 * Un lote dentro de una venta, con el precio negociado para ese lote en particular. Una venta
 * puede tener varias de estas (cliente que compra más de un lote en la misma operación, ver
 * VentaService); el precio total de la venta es la suma de sus VentaLote.
 */
@Entity
@Table(name = "venta_lote")
public class VentaLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    protected VentaLote() {
        // JPA
    }

    public VentaLote(Venta venta, Lote lote, BigDecimal precio) {
        this.venta = venta;
        this.lote = lote;
        this.precio = precio;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public Lote getLote() {
        return lote;
    }

    public BigDecimal getPrecio() {
        return precio;
    }
}
