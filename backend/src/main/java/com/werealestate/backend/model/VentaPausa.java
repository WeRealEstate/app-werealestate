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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pausa de mensualidades: la mensualidad {@code desdeNumero} del plan pasa de {@code fechaAnterior} a
 * {@code fechaNueva} (siempre posterior) y las siguientes se recorren mes a mes desde ahí, con el día de la fecha nueva
 * como día de pago; el plazo sigue completo y los meses de la pausa no tienen renglón (decisiones del usuario,
 * 2026-11-12). Solo se mueve una mensualidad sin pagos y sin pagos posteriores. Pertenece a un plan
 * ({@code reestructuracionId}, null = el original): una reestructuración posterior arma su plan sin ella. Deshacer la
 * marca (no la borra) para que quede en la bitácora. Ver CalendarioPagos y PausaMensualidadService.
 */
@Entity
@Table(name = "venta_pausa")
public class VentaPausa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "reestructuracion_id")
    private Long reestructuracionId;

    @Column(name = "desde_numero", nullable = false)
    private int desdeNumero;

    @Column(name = "fecha_anterior", nullable = false)
    private LocalDate fechaAnterior;

    @Column(name = "fecha_nueva", nullable = false)
    private LocalDate fechaNueva;

    @Column(nullable = false, length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "deshecha_en")
    private LocalDateTime deshechaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deshecha_por_id")
    private Usuario deshechaPor;

    @Column(name = "motivo_deshacer", length = 500)
    private String motivoDeshacer;

    protected VentaPausa() {
    }

    public VentaPausa(Venta venta, Long reestructuracionId, int desdeNumero, LocalDate fechaAnterior,
            LocalDate fechaNueva, String motivo, Usuario usuario) {
        this.venta = venta;
        this.reestructuracionId = reestructuracionId;
        this.desdeNumero = desdeNumero;
        this.fechaAnterior = fechaAnterior;
        this.fechaNueva = fechaNueva;
        this.motivo = motivo;
        this.usuario = usuario;
    }

    public void deshacer(Usuario quien, String motivo) {
        this.deshechaEn = LocalDateTime.now();
        this.deshechaPor = quien;
        this.motivoDeshacer = motivo;
    }

    public boolean isVigente() {
        return deshechaEn == null;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public Long getReestructuracionId() {
        return reestructuracionId;
    }

    public int getDesdeNumero() {
        return desdeNumero;
    }

    public LocalDate getFechaAnterior() {
        return fechaAnterior;
    }

    public LocalDate getFechaNueva() {
        return fechaNueva;
    }

    public String getMotivo() {
        return motivo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public LocalDateTime getDeshechaEn() {
        return deshechaEn;
    }

    public Usuario getDeshechaPor() {
        return deshechaPor;
    }

    public String getMotivoDeshacer() {
        return motivoDeshacer;
    }
}
