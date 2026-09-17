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

    /** Posición (0-100, porcentaje del ancho/alto de la imagen) del pin de este lote sobre el plano
     * de {@link Desarrollo#getPlanoUrl()}; null si todavía no se ha delimitado en el editor. */
    @Column(name = "mapa_x", precision = 6, scale = 3)
    private BigDecimal mapaX;

    @Column(name = "mapa_y", precision = 6, scale = 3)
    private BigDecimal mapaY;

    /** Solo tiene valor mientras estado == APARTADO_A_PLAZO; el scheduler la usa para revertir a
     * DISPONIBLE justo como hace con el APARTADO simple, pero con una fecha propia por lote en vez
     * del plazo fijo global. Se limpia a null en cualquier otro estado. */
    @Column(name = "fecha_expira_apartado")
    private LocalDateTime fechaExpiraApartado;

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

    public BigDecimal getMapaX() {
        return mapaX;
    }

    public BigDecimal getMapaY() {
        return mapaY;
    }

    public LocalDateTime getFechaExpiraApartado() {
        return fechaExpiraApartado;
    }

    public void setFechaExpiraApartado(LocalDateTime fechaExpiraApartado) {
        this.fechaExpiraApartado = fechaExpiraApartado;
    }

    /** Fija (o borra, pasando ambos en null) la posición del pin de este lote en el plano. */
    public void actualizarPosicionMapa(BigDecimal mapaX, BigDecimal mapaY) {
        this.mapaX = mapaX;
        this.mapaY = mapaY;
    }

    /** Único punto de cambio de estado: siempre actualiza también la fecha y quién lo hizo, para
     * que el contador de 3 días y la bitácora estén siempre consistentes. */
    public void cambiarEstado(EstadoLote nuevoEstado, Usuario usuario) {
        this.estado = nuevoEstado;
        this.fechaCambioEstado = LocalDateTime.now();
        this.cambiadoPor = usuario;
    }
}
