package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Fila única (id=1) con el porcentaje de comisión vigente para todos los asesores. */
@Entity
@Table(name = "configuracion_comision")
public class ConfiguracionComision {

    @Id
    private Long id;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    protected ConfiguracionComision() {
        // JPA
    }

    public ConfiguracionComision(Long id, BigDecimal porcentaje) {
        this.id = id;
        this.porcentaje = porcentaje;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public void setPorcentaje(BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }
}
