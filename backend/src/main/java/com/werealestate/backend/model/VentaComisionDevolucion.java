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

/** Dinero que el asesor devolvió de una comisión ya entregada (total o parcial). Se captura a mano y
 * cuenta como ingreso en Finanzas; la entrega original (y su gasto) no se toca. */
@Entity
@Table(name = "venta_comision_devolucion")
public class VentaComisionDevolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comision_id", nullable = false)
    private VentaComision comision;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(length = 500)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por_id", nullable = false)
    private Usuario registradaPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected VentaComisionDevolucion() {
        // JPA
    }

    public VentaComisionDevolucion(
            VentaComision comision, LocalDate fecha, BigDecimal monto, String notas, Usuario registradaPor) {
        this.comision = comision;
        this.fecha = fecha;
        this.monto = monto;
        this.notas = notas;
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

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
