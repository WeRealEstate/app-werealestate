package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de una venta cerrada. A propósito no referencia Lead ni Usuario (ver VentaService):
 * cliente y asesor son texto libre porque no todo comprador pasó por el CRM como lead, y no todo
 * asesor que vende tiene cuenta en el sistema (hay asesores externos).
 *
 * <p>No referencia Lote directo: una venta puede incluir varios lotes (cliente que compra más de
 * uno en la misma operación, con una sola mensualidad/plazo/saldo combinado — ver VentaLote y
 * VentaService). El precio total de la venta es la suma de sus VentaLote, no se guarda aquí.
 */
@Entity
@Table(name = "venta")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String cliente;

    @Column(nullable = false, length = 200)
    private String asesor;

    @Column(name = "forma_pago", nullable = false, length = 50)
    private String formaPago;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    /** Términos de financiamiento fijados al momento de la venta; null en ventas de contado. */
    @Column(precision = 14, scale = 2)
    private BigDecimal mensualidad;

    @Column(name = "plazo_meses")
    private Integer plazoMeses;

    /** "Enganche" / "Pago inicial" / "Aportación anual" según el tipo de pago elegido (mismo
     * concepto que ya usa Cotizacion); ambos null cuando no aplica (Sin enganche / Contado). */
    @Column(name = "enganche_label", length = 30)
    private String engancheLabel;

    @Column(precision = 14, scale = 2)
    private BigDecimal enganche;

    @Column(length = 1000)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Venta() {
        // JPA
    }

    public Venta(
            String cliente,
            String asesor,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            String notas) {
        this.cliente = cliente;
        this.asesor = asesor;
        this.formaPago = formaPago;
        this.fechaVenta = fechaVenta;
        this.mensualidad = mensualidad;
        this.plazoMeses = plazoMeses;
        this.engancheLabel = engancheLabel;
        this.enganche = enganche;
        this.notas = notas;
    }

    /** Modifica los datos capturados de la venta (no toca sus lotes/precios, que se administran
     * aparte). Temporal: el botón que llama a esto en el frontend se va a quitar más adelante. */
    public void actualizar(
            String cliente,
            String asesor,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            String notas) {
        this.cliente = cliente;
        this.asesor = asesor;
        this.formaPago = formaPago;
        this.fechaVenta = fechaVenta;
        this.mensualidad = mensualidad;
        this.plazoMeses = plazoMeses;
        this.engancheLabel = engancheLabel;
        this.enganche = enganche;
        this.notas = notas;
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getAsesor() {
        return asesor;
    }

    public String getFormaPago() {
        return formaPago;
    }

    public LocalDate getFechaVenta() {
        return fechaVenta;
    }

    public BigDecimal getMensualidad() {
        return mensualidad;
    }

    public Integer getPlazoMeses() {
        return plazoMeses;
    }

    public String getEngancheLabel() {
        return engancheLabel;
    }

    public BigDecimal getEnganche() {
        return enganche;
    }

    public String getNotas() {
        return notas;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
