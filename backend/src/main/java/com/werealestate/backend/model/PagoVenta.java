package com.werealestate.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Una línea de un recibo de abono de una venta (ver {@link ReciboVenta}): un monto de un tipo
 * (mensualidad, enganche, apartado o aportación). Cada pago queda como su propio registro (a
 * diferencia de un campo "saldo" editable a mano): el saldo pendiente de la venta siempre se calcula
 * sumando estos registros, nunca se guarda directo, así que no se desactualiza ni se puede escribir
 * mal. Folio, nota y quién lo registró viven en el recibo. tipo, meses y aportación pueden faltar
 * solo en abonos anteriores a que existieran: se completan a mano.
 */
@Entity
@Table(name = "pago_venta")
public class PagoVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_id", nullable = false)
    private ReciboVenta recibo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoAbono tipo;

    /** Solo para tipo APORTACION: la aportación programada que paga. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aportacion_id")
    private VentaAportacion aportacion;

    /** Solo para tipo MENSUALIDAD: los meses que cubre (puede ser más de uno). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "pago_venta_mes", joinColumns = @JoinColumn(name = "pago_id"))
    private List<MesCubierto> meses = new ArrayList<>();

    protected PagoVenta() {
        // JPA
    }

    public PagoVenta(
            Venta venta,
            ReciboVenta recibo,
            BigDecimal monto,
            TipoAbono tipo,
            VentaAportacion aportacion,
            List<MesCubierto> meses) {
        this.venta = venta;
        this.recibo = recibo;
        this.fecha = recibo.getFecha();
        this.monto = monto;
        this.tipo = tipo;
        this.aportacion = aportacion;
        if (meses != null) this.meses.addAll(meses);
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public ReciboVenta getRecibo() {
        return recibo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public TipoAbono getTipo() {
        return tipo;
    }

    public VentaAportacion getAportacion() {
        return aportacion;
    }

    public List<MesCubierto> getMeses() {
        return meses;
    }

    public String getFolio() {
        return recibo.getFolio();
    }

    public String getNotas() {
        return recibo.getNotas();
    }

    public Usuario getRegistradoPor() {
        return recibo.getRegistradoPor();
    }

    public LocalDateTime getFechaCreacion() {
        return recibo.getFechaCreacion();
    }
}
