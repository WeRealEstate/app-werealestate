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
 * Un cambio del plan de pagos de una venta (p. ej. de mensualidades a anualidades). Lo abonado hasta la fecha de corte
 * se respeta tal cual y el plan nuevo (el que queda en la venta) reparte solo el saldo de ese día, atrasos incluidos
 * (decisión del usuario). Guarda el plan que reemplazó para que la tabla de amortización siga mostrando sus pagos
 * (ver CalendarioPagos). Una venta puede reestructurarse varias veces; el precio nunca cambia.
 */
@Entity
@Table(name = "venta_reestructuracion")
public class VentaReestructuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    /** Lo abonado (recibos vigentes) al momento de reestructurar: lo que cubre el plan anterior. */
    @Column(name = "abonado_al_corte", nullable = false, precision = 14, scale = 2)
    private BigDecimal abonadoAlCorte;

    /** Precio menos lo abonado: lo que reparte el plan nuevo. */
    @Column(name = "saldo_al_corte", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoAlCorte;

    @Column(nullable = false, length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "ant_fecha_inicio", nullable = false)
    private LocalDate antFechaInicio;

    @Column(name = "ant_forma_pago", nullable = false, length = 50)
    private String antFormaPago;

    @Column(name = "ant_mensualidad", precision = 14, scale = 2)
    private BigDecimal antMensualidad;

    @Column(name = "ant_plazo_meses")
    private Integer antPlazoMeses;

    @Column(name = "ant_dia_pago", nullable = false)
    private int antDiaPago;

    @Column(name = "ant_primera_mes_inicio", nullable = false)
    private boolean antPrimeraMesInicio;

    @Column(name = "ant_enganche_label", length = 30)
    private String antEngancheLabel;

    @Column(name = "ant_enganche", precision = 14, scale = 2)
    private BigDecimal antEnganche;

    @Column(name = "resumen_anterior", nullable = false, length = 300)
    private String resumenAnterior;

    @Column(name = "resumen_nuevo", nullable = false, length = 300)
    private String resumenNuevo;

    protected VentaReestructuracion() {
        // JPA
    }

    public VentaReestructuracion(
            Venta venta,
            LocalDate fechaCorte,
            BigDecimal abonadoAlCorte,
            BigDecimal saldoAlCorte,
            String motivo,
            Usuario usuario,
            LocalDate antFechaInicio,
            String antFormaPago,
            BigDecimal antMensualidad,
            Integer antPlazoMeses,
            int antDiaPago,
            boolean antPrimeraMesInicio,
            String antEngancheLabel,
            BigDecimal antEnganche,
            String resumenAnterior,
            String resumenNuevo) {
        this.venta = venta;
        this.fechaCorte = fechaCorte;
        this.abonadoAlCorte = abonadoAlCorte;
        this.saldoAlCorte = saldoAlCorte;
        this.motivo = motivo;
        this.usuario = usuario;
        this.antFechaInicio = antFechaInicio;
        this.antFormaPago = antFormaPago;
        this.antMensualidad = antMensualidad;
        this.antPlazoMeses = antPlazoMeses;
        this.antDiaPago = antDiaPago;
        this.antPrimeraMesInicio = antPrimeraMesInicio;
        this.antEngancheLabel = antEngancheLabel;
        this.antEnganche = antEnganche;
        this.resumenAnterior = resumenAnterior;
        this.resumenNuevo = resumenNuevo;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public LocalDate getFechaCorte() { return fechaCorte; }
    public BigDecimal getAbonadoAlCorte() { return abonadoAlCorte; }
    public BigDecimal getSaldoAlCorte() { return saldoAlCorte; }
    public String getMotivo() { return motivo; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public LocalDate getAntFechaInicio() { return antFechaInicio; }
    public String getAntFormaPago() { return antFormaPago; }
    public BigDecimal getAntMensualidad() { return antMensualidad; }
    public Integer getAntPlazoMeses() { return antPlazoMeses; }
    public int getAntDiaPago() { return antDiaPago; }
    public boolean isAntPrimeraMesInicio() { return antPrimeraMesInicio; }
    public String getAntEngancheLabel() { return antEngancheLabel; }
    public BigDecimal getAntEnganche() { return antEnganche; }
    public String getResumenAnterior() { return resumenAnterior; }
    public String getResumenNuevo() { return resumenNuevo; }
}
