package com.werealestate.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

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

    /** Bono por número EXACTO de lotes (V98, 12.20.0, decisión del usuario): una venta con un número de lotes que no
     * tiene nivel no recibe bono. Reemplaza a las columnas bono_un_lote / bono_dos_lotes / bono_tres_lotes (que se
     * conservan en la BD sin usarse). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promocion_bono", joinColumns = @JoinColumn(name = "promocion_id"))
    @MapKeyColumn(name = "lotes")
    @Column(name = "bono", nullable = false, precision = 14, scale = 2)
    private Map<Integer, BigDecimal> bonos = new TreeMap<>();

    @Column(name = "plazo_fijo")
    private Integer plazoFijo;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    /** Desde cuándo vale (null = las de antes de V95: desde siempre). */
    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    /** false = programada: espera su fecha de inicio para activarse sola (ver PromocionService.actualizarVigencias). */
    @Column(name = "inicio_aplicado", nullable = false)
    private boolean inicioAplicado = true;

    /** Código de 6 letras o números (en mayúsculas) para aplicarla en el registro de venta (null = sin código). Único; solo lo ve el admin. */
    @Column(name = "codigo", length = 6)
    private String codigo;

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public boolean isInicioAplicado() {
        return inicioAplicado;
    }

    public void setInicioAplicado(boolean inicioAplicado) {
        this.inicioAplicado = inicioAplicado;
    }

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

    /** Los niveles, de menos a más lotes. */
    public Map<Integer, BigDecimal> getBonos() {
        return new TreeMap<>(bonos);
    }

    public void setBonos(Map<Integer, BigDecimal> nuevos) {
        bonos.clear();
        if (nuevos != null) bonos.putAll(nuevos);
    }

    /** Bono para exactamente esos lotes; null = la promoción no aplica a ese número de lotes. */
    public BigDecimal bonoPara(int lotes) {
        return bonos.get(lotes);
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
