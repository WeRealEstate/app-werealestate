package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Gasto con vencimiento que se repite (renta, luz, agua, nómina...). Ver GastoRecurrenteService
 * para cómo se calculan sus vencimientos. */
@Entity
@Table(name = "gasto_recurrente")
public class GastoRecurrente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Lo que se espera pagar; el monto real se captura al pagar (la luz y el agua cambian). */
    @Column(name = "monto_estimado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoEstimado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrecuenciaGasto frecuencia;

    /** Día del mes (1-31) en MENSUAL, BIMESTRAL, ANUAL y QUINCENAL (el primero); null en SEMANAL. */
    private Integer dia;

    /** Segundo día del mes, solo en QUINCENAL. */
    private Integer dia2;

    @Column(name = "primer_vencimiento", nullable = false)
    private LocalDate primerVencimiento;

    @Column(nullable = false)
    private boolean activo = true;

    /** Si es la nómina de un usuario (se administra desde Usuarios, no desde Gastos). */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected GastoRecurrente() {
        // JPA
    }

    public GastoRecurrente(
            String nombre,
            BigDecimal montoEstimado,
            FrecuenciaGasto frecuencia,
            Integer dia,
            Integer dia2,
            LocalDate primerVencimiento) {
        actualizar(nombre, montoEstimado, frecuencia, dia, dia2, primerVencimiento);
    }

    public void actualizar(
            String nombre,
            BigDecimal montoEstimado,
            FrecuenciaGasto frecuencia,
            Integer dia,
            Integer dia2,
            LocalDate primerVencimiento) {
        this.nombre = nombre;
        this.montoEstimado = montoEstimado;
        this.frecuencia = frecuencia;
        this.dia = dia;
        this.dia2 = dia2;
        this.primerVencimiento = primerVencimiento;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getMontoEstimado() {
        return montoEstimado;
    }

    public FrecuenciaGasto getFrecuencia() {
        return frecuencia;
    }

    public Integer getDia() {
        return dia;
    }

    public Integer getDia2() {
        return dia2;
    }

    public LocalDate getPrimerVencimiento() {
        return primerVencimiento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
