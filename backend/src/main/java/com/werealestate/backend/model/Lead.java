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
import java.time.LocalDateTime;

@Entity
@Table(name = "lead")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_cliente", nullable = false, length = 150)
    private String nombreCliente;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(length = 100)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desarrollo_id", nullable = false)
    private Desarrollo desarrollo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLead estado = EstadoLead.NUEVO;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_ultimo_contacto", nullable = false)
    private LocalDateTime fechaUltimoContacto = LocalDateTime.now();

    @Column(name = "valor_estimado")
    private BigDecimal valorEstimado;

    private Integer edad;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Pais pais;

    /** Solo aplica cuando pais = MEXICANO; para EXTRANJERO se deja en null. */
    @Column(name = "estado_republica", length = 50)
    private String estadoRepublica;

    /** Un lead archivado se conserva como métrica pero desaparece de la lista activa; no se elimina. */
    @Column(nullable = false)
    private boolean archivado = false;

    /** Posición extra en el tablero de tarjetas del asesor; no reemplaza ni afecta {@link #estado}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "columna_personalizada_id")
    private ColumnaPersonalizada columnaPersonalizada;

    protected Lead() {
        // JPA
    }

    public Lead(
            String nombreCliente,
            String telefono,
            String email,
            String origen,
            Desarrollo desarrollo,
            Usuario asesor,
            BigDecimal valorEstimado) {
        this.nombreCliente = nombreCliente;
        this.telefono = telefono;
        this.email = email;
        this.origen = origen;
        this.desarrollo = desarrollo;
        this.asesor = asesor;
        this.valorEstimado = valorEstimado;
    }

    public Long getId() {
        return id;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public Desarrollo getDesarrollo() {
        return desarrollo;
    }

    public Usuario getAsesor() {
        return asesor;
    }

    public void setAsesor(Usuario asesor) {
        this.asesor = asesor;
    }

    public EstadoLead getEstado() {
        return estado;
    }

    public void setEstado(EstadoLead estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaUltimoContacto() {
        return fechaUltimoContacto;
    }

    public void setFechaUltimoContacto(LocalDateTime fechaUltimoContacto) {
        this.fechaUltimoContacto = fechaUltimoContacto;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public void setValorEstimado(BigDecimal valorEstimado) {
        this.valorEstimado = valorEstimado;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public Pais getPais() {
        return pais;
    }

    public void setPais(Pais pais) {
        this.pais = pais;
    }

    public String getEstadoRepublica() {
        return estadoRepublica;
    }

    public void setEstadoRepublica(String estadoRepublica) {
        this.estadoRepublica = estadoRepublica;
    }

    public boolean isArchivado() {
        return archivado;
    }

    public void setArchivado(boolean archivado) {
        this.archivado = archivado;
    }

    public ColumnaPersonalizada getColumnaPersonalizada() {
        return columnaPersonalizada;
    }

    public void setColumnaPersonalizada(ColumnaPersonalizada columnaPersonalizada) {
        this.columnaPersonalizada = columnaPersonalizada;
    }
}
