package com.werealestate.backend.model;

/** Secciones del panel que un admin puede activar o quitar por usuario (ver ModulosAcceso). Solo
 * incluye las que ya dependen del rol de uso diario, más Asesores externos y Comunidades We, que
 * administración (líder de área) también puede tener. Usuarios y Promociones siguen siendo
 * exclusivas de admin, y Inicio lo ve todo el mundo. */
public enum Modulo {
    LEADS("Leads"),
    PIPELINE("Pipeline"),
    COTIZADOR("Cotizador"),
    LOTES("Lotes"),
    PLANO("Plano"),
    VENTAS("Ventas"),
    FINANZAS("Finanzas"),
    GASTOS("Gastos"),
    CALENDARIO("Calendario"),
    ASESORES_EXTERNOS("Asesores externos"),
    COMUNIDADES("Comunidades We");

    private final String etiqueta;

    Modulo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
