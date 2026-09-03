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
import java.time.LocalDateTime;

@Entity
@Table(name = "seguimiento")
public class Seguimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSeguimiento tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String nota;

    @Column(length = 200)
    private String resultado;

    @Column(name = "proximo_seguimiento")
    private LocalDateTime proximoSeguimiento;

    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    protected Seguimiento() {
        // JPA
    }

    public Seguimiento(
            Lead lead,
            Usuario asesor,
            TipoSeguimiento tipo,
            String nota,
            String resultado,
            LocalDateTime proximoSeguimiento,
            Integer duracionMinutos) {
        this.lead = lead;
        this.asesor = asesor;
        this.tipo = tipo;
        this.nota = nota;
        this.resultado = resultado;
        this.proximoSeguimiento = proximoSeguimiento;
        this.duracionMinutos = proximoSeguimiento != null ? duracionMinutos : null;
    }

    public Long getId() {
        return id;
    }

    public Lead getLead() {
        return lead;
    }

    public Usuario getAsesor() {
        return asesor;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public TipoSeguimiento getTipo() {
        return tipo;
    }

    public String getNota() {
        return nota;
    }

    public String getResultado() {
        return resultado;
    }

    public LocalDateTime getProximoSeguimiento() {
        return proximoSeguimiento;
    }

    public Integer getDuracionMinutos() {
        return duracionMinutos;
    }
}
