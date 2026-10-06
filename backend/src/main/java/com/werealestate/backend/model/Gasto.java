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
 * Un gasto registrado (ej. papelería, la renta de oficina, una comisión entregada). ticketExtension
 * solo se llena cuando se sube un comprobante (siempre opcional). El archivo en sí no se referencia por URL pública: se sirve por un endpoint
 * autenticado (ver GastoController.verTicket), así que aquí solo se guarda su extensión.
 */
@Entity
@Table(name = "gasto")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Qué se compró o pagó (texto libre). */
    @Column(nullable = false, length = 200)
    private String concepto;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenGasto origen = OrigenGasto.UNICO;

    /** Solo lo usa el gasto automático de las comisiones; el resto de los gastos no lleva tipo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id")
    private TipoGasto tipoGasto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(name = "ticket_extension", length = 10)
    private String ticketExtension;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Gasto() {
        // JPA
    }

    public Gasto(
            String concepto,
            OrigenGasto origen,
            TipoGasto tipoGasto,
            LocalDate fecha,
            BigDecimal monto,
            Usuario registradoPor) {
        this.concepto = concepto;
        this.origen = origen;
        this.tipoGasto = tipoGasto;
        this.fecha = fecha;
        this.monto = monto;
        this.registradoPor = registradoPor;
    }

    public Long getId() {
        return id;
    }

    public String getConcepto() {
        return concepto;
    }

    public OrigenGasto getOrigen() {
        return origen;
    }

    public TipoGasto getTipoGasto() {
        return tipoGasto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getTicketExtension() {
        return ticketExtension;
    }

    public void setTicketExtension(String ticketExtension) {
        this.ticketExtension = ticketExtension;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
