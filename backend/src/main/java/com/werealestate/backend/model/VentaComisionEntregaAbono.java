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

/** Renglón del "Desglose por abono" del ticket de una entrega: qué parte de la entrega salió de cada abono
 * del cliente. Se guarda al entregar (no se recalcula), así el ticket no cambia aunque después se anulen
 * recibos. Se borra con la entrega (ON DELETE CASCADE). */
@Entity
@Table(name = "venta_comision_entrega_abono")
public class VentaComisionEntregaAbono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrega_id", nullable = false)
    private VentaComisionEntrega entrega;

    @Column(name = "fecha_abono", nullable = false)
    private LocalDate fechaAbono;

    /** Enganche, Mensualidad, Aportación, Apartado, Abono (sin tipo) o Venta (comisión de una exhibición). */
    @Column(nullable = false, length = 30)
    private String concepto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    protected VentaComisionEntregaAbono() {
        // JPA
    }

    public VentaComisionEntregaAbono(VentaComisionEntrega entrega, LocalDate fechaAbono, String concepto, BigDecimal monto) {
        this.entrega = entrega;
        this.fechaAbono = fechaAbono;
        this.concepto = concepto;
        this.monto = monto;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFechaAbono() {
        return fechaAbono;
    }

    public String getConcepto() {
        return concepto;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}
