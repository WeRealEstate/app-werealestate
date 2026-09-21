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
import java.time.LocalDateTime;

/**
 * Registro de una venta cerrada. A propósito no referencia Lead ni Usuario (ver VentaService):
 * cliente y asesor son texto libre porque no todo comprador pasó por el CRM como lead, y no todo
 * asesor que vende tiene cuenta en el sistema (hay asesores externos).
 */
@Entity
@Table(name = "venta")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(nullable = false, length = 200)
    private String cliente;

    @Column(nullable = false, length = 200)
    private String asesor;

    @Column(name = "precio_venta", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "forma_pago", nullable = false, length = 50)
    private String formaPago;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    @Column(length = 1000)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Venta() {
        // JPA
    }

    public Venta(
            Lote lote,
            String cliente,
            String asesor,
            BigDecimal precioVenta,
            String formaPago,
            LocalDate fechaVenta,
            String notas) {
        this.lote = lote;
        this.cliente = cliente;
        this.asesor = asesor;
        this.precioVenta = precioVenta;
        this.formaPago = formaPago;
        this.fechaVenta = fechaVenta;
        this.notas = notas;
    }

    public Long getId() {
        return id;
    }

    public Lote getLote() {
        return lote;
    }

    public String getCliente() {
        return cliente;
    }

    public String getAsesor() {
        return asesor;
    }

    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }

    public String getFormaPago() {
        return formaPago;
    }

    public LocalDate getFechaVenta() {
        return fechaVenta;
    }

    public String getNotas() {
        return notas;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
