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

/** Un recordatorio de pago que salió bien por correo al cliente titular de una venta, con lo que se le recordó (el
 * próximo pago y lo atrasado). Ver RecordatorioPagoService. */
@Entity
@Table(name = "recordatorio_pago")
public class RecordatorioPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "correo_destino", nullable = false)
    private String correoDestino;

    @Column(name = "proximo_pago_fecha")
    private LocalDate proximoPagoFecha;

    @Column(name = "proximo_pago_monto", precision = 14, scale = 2)
    private BigDecimal proximoPagoMonto;

    @Column(name = "atraso_monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal atrasoMonto;

    protected RecordatorioPago() {
    }

    public RecordatorioPago(Venta venta, Usuario usuario, String correoDestino, LocalDate proximoPagoFecha,
            BigDecimal proximoPagoMonto, BigDecimal atrasoMonto) {
        this.venta = venta;
        this.usuario = usuario;
        this.enviadoEn = LocalDateTime.now();
        this.correoDestino = correoDestino;
        this.proximoPagoFecha = proximoPagoFecha;
        this.proximoPagoMonto = proximoPagoMonto;
        this.atrasoMonto = atrasoMonto == null ? BigDecimal.ZERO : atrasoMonto;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public Usuario getUsuario() { return usuario; }
    public String getCorreoDestino() { return correoDestino; }
    public LocalDate getProximoPagoFecha() { return proximoPagoFecha; }
    public BigDecimal getProximoPagoMonto() { return proximoPagoMonto; }
    public BigDecimal getAtrasoMonto() { return atrasoMonto; }
}
