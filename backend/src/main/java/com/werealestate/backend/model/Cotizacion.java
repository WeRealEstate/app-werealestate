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
import java.time.LocalDateTime;

/**
 * Registro histórico de una cotización generada en el Cotizador (PDF descargado o compartido).
 * Se crea una fila por cada cotización, con los datos que se usaron para armarla; nunca se edita
 * ni se borra desde la app, es solo bitácora para que el admin vea la actividad del equipo.
 */
@Entity
@Table(name = "cotizacion")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Column(nullable = false, length = 50)
    private String proyecto;

    @Column(name = "nombre_cliente", nullable = false, length = 150)
    private String nombreCliente;

    @Column(length = 30)
    private String manzana;

    @Column(length = 30)
    private String lote;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal superficie;

    @Column(name = "precio_m2", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioM2;

    @Column(name = "precio_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioTotal;

    @Column(name = "forma_pago", nullable = false, length = 100)
    private String formaPago;

    @Column(name = "enganche_label", nullable = false, length = 30)
    private String engancheLabel;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal enganche;

    @Column(name = "monto_financiado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoFinanciado;

    @Column(nullable = false)
    private int meses;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal mensualidad;

    @Column(name = "interes_porcentaje", nullable = false, precision = 6, scale = 2)
    private BigDecimal interesPorcentaje;

    @Column(name = "interes_monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal interesMonto;

    @Column(name = "total_inversion", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInversion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Cotizacion() {
        // JPA
    }

    public Cotizacion(
            Usuario asesor,
            String proyecto,
            String nombreCliente,
            String manzana,
            String lote,
            BigDecimal superficie,
            BigDecimal precioM2,
            BigDecimal precioTotal,
            String formaPago,
            String engancheLabel,
            BigDecimal enganche,
            BigDecimal montoFinanciado,
            int meses,
            BigDecimal mensualidad,
            BigDecimal interesPorcentaje,
            BigDecimal interesMonto,
            BigDecimal totalInversion) {
        this.asesor = asesor;
        this.proyecto = proyecto;
        this.nombreCliente = nombreCliente;
        this.manzana = manzana;
        this.lote = lote;
        this.superficie = superficie;
        this.precioM2 = precioM2;
        this.precioTotal = precioTotal;
        this.formaPago = formaPago;
        this.engancheLabel = engancheLabel;
        this.enganche = enganche;
        this.montoFinanciado = montoFinanciado;
        this.meses = meses;
        this.mensualidad = mensualidad;
        this.interesPorcentaje = interesPorcentaje;
        this.interesMonto = interesMonto;
        this.totalInversion = totalInversion;
    }

    public Long getId() {
        return id;
    }

    public Usuario getAsesor() {
        return asesor;
    }

    public String getProyecto() {
        return proyecto;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public String getManzana() {
        return manzana;
    }

    public String getLote() {
        return lote;
    }

    public BigDecimal getSuperficie() {
        return superficie;
    }

    public BigDecimal getPrecioM2() {
        return precioM2;
    }

    public BigDecimal getPrecioTotal() {
        return precioTotal;
    }

    public String getFormaPago() {
        return formaPago;
    }

    public String getEngancheLabel() {
        return engancheLabel;
    }

    public BigDecimal getEnganche() {
        return enganche;
    }

    public BigDecimal getMontoFinanciado() {
        return montoFinanciado;
    }

    public int getMeses() {
        return meses;
    }

    public BigDecimal getMensualidad() {
        return mensualidad;
    }

    public BigDecimal getInteresPorcentaje() {
        return interesPorcentaje;
    }

    public BigDecimal getInteresMonto() {
        return interesMonto;
    }

    public BigDecimal getTotalInversion() {
        return totalInversion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
