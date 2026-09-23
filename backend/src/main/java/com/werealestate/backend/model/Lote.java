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

    /** Vértices (0-100, porcentaje del ancho/alto de la imagen) del polígono que delimita este lote
     * sobre el plano de {@link Desarrollo#getPlanoUrl()}, en JSON; null si todavía no se ha
     * delimitado en el editor. Ver com.werealestate.backend.dto.PoligonoMapaJson. */
    @Column(name = "mapa_poligono", columnDefinition = "TEXT")
    private String mapaPoligonoJson;

    /** Solo tiene valor mientras estado == APARTADO_A_PLAZO; el scheduler la usa para revertir a
     * DISPONIBLE justo como hace con el APARTADO simple, pero con una fecha propia por lote en vez
     * del plazo fijo global. Se limpia a null en cualquier otro estado. */
    @Column(name = "fecha_expira_apartado")
    private LocalDateTime fechaExpiraApartado;

    /** Cuánto dinero se recibió al apartar, solo mientras estado == APARTADO_CON_DINERO. Se limpia
     * a null al salir de ese estado (ver LoteService.cambiarEstado). El formulario de Ventas lo lee
     * (vía LoteDto) antes de registrar la venta, para preguntar si ese dinero baja la mensualidad o
     * el saldo; LoteService.marcarVendido lo limpia una vez que el lote ya quedó Vendido. */
    @Column(name = "monto_apartado", precision = 14, scale = 2)
    private BigDecimal montoApartado;

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

    public String getMapaPoligonoJson() {
        return mapaPoligonoJson;
    }

    public LocalDateTime getFechaExpiraApartado() {
        return fechaExpiraApartado;
    }

    public void setFechaExpiraApartado(LocalDateTime fechaExpiraApartado) {
        this.fechaExpiraApartado = fechaExpiraApartado;
    }

    public BigDecimal getMontoApartado() {
        return montoApartado;
    }

    public void setMontoApartado(BigDecimal montoApartado) {
        this.montoApartado = montoApartado;
    }

    /** Fija (o borra, pasando null) el polígono que delimita este lote en el plano. */
    public void actualizarPoligonoMapa(String mapaPoligonoJson) {
        this.mapaPoligonoJson = mapaPoligonoJson;
    }

    /** Único punto de cambio de estado: siempre actualiza también la fecha y quién lo hizo, para
     * que el contador de 3 días y la bitácora estén siempre consistentes. */
    public void cambiarEstado(EstadoLote nuevoEstado, Usuario usuario) {
        this.estado = nuevoEstado;
        this.fechaCambioEstado = LocalDateTime.now();
        this.cambiadoPor = usuario;
    }
}
