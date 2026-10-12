package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Un mes (año y 1-12) que cubre una mensualidad. */
@Embeddable
public class MesCubierto {

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int mes;

    protected MesCubierto() {
        // JPA
    }

    public MesCubierto(int anio, int mes) {
        this.anio = anio;
        this.mes = mes;
    }

    public int getAnio() {
        return anio;
    }

    public int getMes() {
        return mes;
    }
}
