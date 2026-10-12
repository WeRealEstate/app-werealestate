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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un recibo de una venta: el folio (capturado a mano, único entre recibos), la fecha, una nota y
 * quién lo registró. Sus líneas son los {@link PagoVenta} (una por concepto: mensualidad, enganche,
 * apartado o aportación), así un mismo recibo puede cubrir varios conceptos con un solo folio. El
 * folio puede faltar solo en recibos anteriores a que se pidiera: se completa a mano.
 */
@Entity
@Table(name = "recibo_venta")
public class ReciboVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(length = 50)
    private String folio;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 500)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected ReciboVenta() {
        // JPA
    }

    public ReciboVenta(Venta venta, String folio, LocalDate fecha, String notas, Usuario registradoPor) {
        this.venta = venta;
        this.folio = folio;
        this.fecha = fecha;
        this.notas = notas;
        this.registradoPor = registradoPor;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public String getFolio() {
        return folio;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getNotas() {
        return notas;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
