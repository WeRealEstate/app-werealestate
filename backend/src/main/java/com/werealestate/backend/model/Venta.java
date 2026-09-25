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

/**
 * Registro de una venta cerrada. A propósito no referencia Lead (ver VentaService): cliente es
 * texto libre porque no todo comprador pasó por el CRM como lead. El asesor sí es una relación
 * real — a un Usuario interno o a un AsesorExterno registrado (gente que vende pero no tiene
 * cuenta en el sistema) — exactamente uno de los dos, nunca los dos ni ninguno (ver
 * VentaService.resolverAsesor y la migración V34).
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_asesor_id")
    private Usuario usuarioAsesor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_externo_id")
    private AsesorExterno asesorExterno;

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
            Usuario usuarioAsesor,
            AsesorExterno asesorExterno,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            String notas) {
        this.cliente = cliente;
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
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
            Usuario usuarioAsesor,
            AsesorExterno asesorExterno,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            String notas) {
        this.cliente = cliente;
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
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

    public Usuario getUsuarioAsesor() {
        return usuarioAsesor;
    }

    public AsesorExterno getAsesorExterno() {
        return asesorExterno;
    }

    /** Nombre a mostrar del asesor, sea interno o externo — para bitácoras/historiales de texto
     * (ver LoteService.marcarVendido) que no necesitan distinguir cuál de los dos es. */
    public String getAsesorNombre() {
        return usuarioAsesor != null ? usuarioAsesor.getNombre() : asesorExterno.getNombre();
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
