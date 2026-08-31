package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "desarrollo")
public class Desarrollo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String ubicacion;

    @Column(name = "precio_m2", nullable = false)
    private BigDecimal precioM2;

    @Column(name = "area_minima", nullable = false)
    private BigDecimal areaMinima;

    protected Desarrollo() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public BigDecimal getPrecioM2() {
        return precioM2;
    }

    public BigDecimal getAreaMinima() {
        return areaMinima;
    }
}
