package com.werealestate.backend.model;

import jakarta.persistence.Column;
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

/** Dinero de la comisión efectivamente entregado al asesor. Cada entrega genera un gasto de tipo
 * "Comisiones" (gasto) con el mismo monto y fecha; anular la entrega lo borra.
 * Las entregas desde V69 llevan además lo necesario para su ticket de pago (IVA, factura, forma de pago y
 * cómo estaba la comisión al entregar); las anteriores tienen esos campos en null y no tienen ticket. */
@Entity
@Table(name = "venta_comision_entrega")
public class VentaComisionEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comision_id", nullable = false)
    private VentaComision comision;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(length = 500)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasto_id")
    private Gasto gasto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrada_por_id", nullable = false)
    private Usuario registradaPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** Con o sin IVA y con o sin factura: solo se marcan y salen en el ticket, no cambian el monto. */
    @Column(name = "con_iva")
    private Boolean conIva;

    @Column(name = "con_factura")
    private Boolean conFactura;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", length = 20)
    private ModalidadPago formaPago;

    // Cómo estaba la comisión al entregar (para que el ticket no cambie si después se edita la comisión,
    // se anula un recibo o se elimina la venta). entregadoAcumulado ya incluye esta entrega.
    @Column(name = "comision_monto", precision = 14, scale = 2)
    private BigDecimal comisionMonto;

    @Column(name = "comision_porcentaje", precision = 9, scale = 4)
    private BigDecimal comisionPorcentaje;

    @Column(name = "entregado_acumulado", precision = 14, scale = 2)
    private BigDecimal entregadoAcumulado;

    @Column(length = 200)
    private String desarrollo;

    @Column(length = 300)
    private String lotes;

    protected VentaComisionEntrega() {
        // JPA
    }

    public VentaComisionEntrega(
            VentaComision comision,
            LocalDate fecha,
            BigDecimal monto,
            String notas,
            Gasto gasto,
            Usuario registradaPor,
            boolean conIva,
            boolean conFactura,
            ModalidadPago formaPago) {
        this.comision = comision;
        this.fecha = fecha;
        this.monto = monto;
        this.notas = notas;
        this.gasto = gasto;
        this.registradaPor = registradaPor;
        this.conIva = conIva;
        this.conFactura = conFactura;
        this.formaPago = formaPago;
    }

    /** Guarda cómo estaba la comisión al momento de entregar, para el ticket. */
    public void fijarDatosDelTicket(
            BigDecimal comisionMonto,
            BigDecimal comisionPorcentaje,
            BigDecimal entregadoAcumulado,
            String desarrollo,
            String lotes) {
        this.comisionMonto = comisionMonto;
        this.comisionPorcentaje = comisionPorcentaje;
        this.entregadoAcumulado = entregadoAcumulado;
        this.desarrollo = desarrollo;
        this.lotes = lotes;
    }

    /** Solo las entregas registradas desde que existen los tickets (V69) tienen uno. */
    public boolean tieneTicket() {
        return conIva != null && conFactura != null && formaPago != null && comisionMonto != null;
    }

    public Long getId() {
        return id;
    }

    public VentaComision getComision() {
        return comision;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getNotas() {
        return notas;
    }

    public Gasto getGasto() {
        return gasto;
    }

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public Boolean getConIva() {
        return conIva;
    }

    public Boolean getConFactura() {
        return conFactura;
    }

    public ModalidadPago getFormaPago() {
        return formaPago;
    }

    public BigDecimal getComisionMonto() {
        return comisionMonto;
    }

    public BigDecimal getComisionPorcentaje() {
        return comisionPorcentaje;
    }

    public BigDecimal getEntregadoAcumulado() {
        return entregadoAcumulado;
    }

    public String getDesarrollo() {
        return desarrollo;
    }

    public String getLotes() {
        return lotes;
    }
}
