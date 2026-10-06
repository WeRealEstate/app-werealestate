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

/** Dinero de la comisión efectivamente entregado al asesor. Cada entrega genera un gasto de tipo
 * "Comisiones" (gasto) con el mismo monto y fecha; anular la entrega lo borra. */
@Entity
@Table(name = "venta_comision_entrega")
public class VentaComisionEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comision_id", nullable = false)
    private VentaComision comision;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(length = 500)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrada_por_id", nullable = false)
    private Usuario registradaPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected VentaComisionEntrega() {
        // JPA
    }

    public VentaComisionEntrega(
            VentaComision comision, LocalDate fecha, BigDecimal monto, String notas, Gasto gasto, Usuario registradaPor) {
        this.comision = comision;
        this.fecha = fecha;
        this.monto = monto;
        this.notas = notas;
        this.gasto = gasto;
        this.registradaPor = registradaPor;
    }

    public Long getId() {
        return id;
    }

    public VentaComision getComision() {
        return comision;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getNotas() {
        return notas;
    }

    public Gasto getGasto() {
        return gasto;
    }

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
