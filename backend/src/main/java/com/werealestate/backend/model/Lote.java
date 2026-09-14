package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.FetchType;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lote")
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desarrollo_id", nullable = false)
    private Desarrollo desarrollo;

    @Column(nullable = false, length = 20)
    private String manzana;

    @Column(name = "numero_lote", nullable = false, length = 20)
    private String numeroLote;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal superficie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLote estado = EstadoLote.DISPONIBLE;

    @Column(name = "fecha_cambio_estado", nullable = false)
    private LocalDateTime fechaCambioEstado = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambiado_por_id")
    private Usuario cambiadoPor;

    protected Lote() {
        // JPA
    }

    public Lote(Desarrollo desarrollo, String manzana, String numeroLote, BigDecimal superficie) {
        this.desarrollo = desarrollo;
        this.manzana = manzana;
        this.numeroLote = numeroLote;
        this.superficie = superficie;
    }

    public Long getId() {
        return id;
    }

    public Desarrollo getDesarrollo() {
        return desarrollo;
    }

    public String getManzana() {
        return manzana;
    }

    public void setManzana(String manzana) {
        this.manzana = manzana;
    }

    public String getNumeroLote() {
        return numeroLote;
    }

    public void setNumeroLote(String numeroLote) {
        this.numeroLote = numeroLote;
    }

    public BigDecimal getSuperficie() {
        return superficie;
    }

    public void setSuperficie(BigDecimal superficie) {
        this.superficie = superficie;
    }

    public void setDesarrollo(Desarrollo desarrollo) {
        this.desarrollo = desarrollo;
    }

    public EstadoLote getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCambioEstado() {
        return fechaCambioEstado;
    }

    public Usuario getCambiadoPor() {
        return cambiadoPor;
    }

    /** Único punto de cambio de estado: siempre actualiza también la fecha y quién lo hizo, para
     * que el contador de 3 días y la bitácora estén siempre consistentes. */
    public void cambiarEstado(EstadoLote nuevoEstado, Usuario usuario) {
        this.estado = nuevoEstado;
        this.fechaCambioEstado = LocalDateTime.now();
        this.cambiadoPor = usuario;
    }
}
