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

/** Un vencimiento de un gasto recurrente: pagado cuando tiene gasto, omitido si se saltó ese
 * periodo; si no, pendiente o vencido según su fecha. */
@Entity
@Table(name = "gasto_recurrente_pago")
public class GastoRecurrentePago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurrente_id", nullable = false)
    private GastoRecurrente recurrente;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_estimado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoEstimado;

    @Column(nullable = false)
    private boolean omitido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;

    protected GastoRecurrentePago() {
        // JPA
    }

    public GastoRecurrentePago(GastoRecurrente recurrente, LocalDate fechaVencimiento, BigDecimal montoEstimado) {
        this.recurrente = recurrente;
        this.fechaVencimiento = fechaVencimiento;
        this.montoEstimado = montoEstimado;
    }

    public Long getId() {
        return id;
    }

    public GastoRecurrente getRecurrente() {
        return recurrente;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getMontoEstimado() {
        return montoEstimado;
    }

    public boolean isOmitido() {
        return omitido;
    }

    public void setOmitido(boolean omitido) {
        this.omitido = omitido;
    }

    public Gasto getGasto() {
        return gasto;
    }

    public void setGasto(Gasto gasto) {
        this.gasto = gasto;
    }
}
