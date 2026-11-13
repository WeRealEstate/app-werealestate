package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promocion")
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String proyecto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_precio", nullable = false, length = 20)
    private TipoPrecioPromocion tipoPrecio = TipoPrecioPromocion.LOTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModalidadPromocion modalidad = ModalidadPromocion.MENSUALIDAD_FIJA;

    @Column(name = "mensualidad_fija", precision = 14, scale = 2)
    private BigDecimal mensualidadFija;

    @Column(name = "bono_dos_lotes", precision = 14, scale = 2)
    private BigDecimal bonoDosLotes;

    @Column(name = "bono_tres_lotes", precision = 14, scale = 2)
    private BigDecimal bonoTresLotes;

    @Column(name = "plazo_fijo")
    private Integer plazoFijo;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.tipoPrecio == null) {
            this.tipoPrecio = TipoPrecioPromocion.LOTE;
        }
        if (this.modalidad == null) {
            this.modalidad = ModalidadPromocion.MENSUALIDAD_FIJA;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getProyecto() {
        return proyecto;
    }

    public void setProyecto(String proyecto) {
        this.proyecto = proyecto;
    }

    public TipoPrecioPromocion getTipoPrecio() {
        return tipoPrecio;
    }

    public void setTipoPrecio(TipoPrecioPromocion tipoPrecio) {
        this.tipoPrecio = tipoPrecio;
    }

    public ModalidadPromocion getModalidad() {
        return modalidad;
    }

    public void setModalidad(ModalidadPromocion modalidad) {
        this.modalidad = modalidad;
    }

    public BigDecimal getMensualidadFija() {
        return mensualidadFija;
    }

    public void setMensualidadFija(BigDecimal mensualidadFija) {
        this.mensualidadFija = mensualidadFija;
    }

    public BigDecimal getBonoDosLotes() {
        return bonoDosLotes;
    }

    public void setBonoDosLotes(BigDecimal bonoDosLotes) {
        this.bonoDosLotes = bonoDosLotes;
    }

    public BigDecimal getBonoTresLotes() {
        return bonoTresLotes;
    }

    public void setBonoTresLotes(BigDecimal bonoTresLotes) {
        this.bonoTresLotes = bonoTresLotes;
    }

    public Integer getPlazoFijo() {
        return plazoFijo;
    }

    public void setPlazoFijo(Integer plazoFijo) {
        this.plazoFijo = plazoFijo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDateTime fechaFin) {
        this.fechaFin = fechaFin;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
