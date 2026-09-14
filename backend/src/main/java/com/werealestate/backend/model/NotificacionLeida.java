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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** Marca que un usuario ya vio una notificación "en vivo" concreta (ver NotificacionService),
 * para que no vuelva a aparecer aunque la condición que la generó siga vigente. (tipo, entidadId,
 * firma) identifica esa ocurrencia puntual: si la firma cambia (la condición se renovó de verdad),
 * ya no coincide con ningún registro y vuelve a notificar. */
@Entity
@Table(
        name = "notificacion_leida",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "tipo", "entidad_id", "firma"}))
public class NotificacionLeida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(nullable = false, length = 100)
    private String firma;

    @Column(name = "fecha_marcada", nullable = false)
    private LocalDateTime fechaMarcada = LocalDateTime.now();

    protected NotificacionLeida() {
        // JPA
    }

    public NotificacionLeida(Usuario usuario, String tipo, Long entidadId, String firma) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.entidadId = entidadId;
        this.firma = firma;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public String getFirma() {
        return firma;
    }

    public LocalDateTime getFechaMarcada() {
        return fechaMarcada;
    }
}
