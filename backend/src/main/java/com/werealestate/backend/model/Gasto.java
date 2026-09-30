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
 * Un gasto registrado (ej. la comisión de un mes, la renta de oficina). ticketExtension solo se
 * llena cuando se sube un comprobante — obligatorio si TipoGasto.requiereTicket (ver
 * GastoService.crear). El archivo en sí no se referencia por URL pública: se sirve por un endpoint
 * autenticado (ver GastoController.verTicket), así que aquí solo se guarda su extensión.
 */
@Entity
@Table(name = "gasto")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id", nullable = false)
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

    public Gasto(TipoGasto tipoGasto, LocalDate fecha, BigDecimal monto, Usuario registradoPor) {
        this.tipoGasto = tipoGasto;
        this.fecha = fecha;
        this.monto = monto;
        this.registradoPor = registradoPor;
    }

    public Long getId() {
        return id;
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
