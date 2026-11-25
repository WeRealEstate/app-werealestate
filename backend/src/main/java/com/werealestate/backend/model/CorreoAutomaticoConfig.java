package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** El interruptor de un correo automático. Sin fila = apagado. */
@Entity
@Table(name = "correo_automatico_config")
public class CorreoAutomaticoConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoCorreoAutomatico tipo;

    @Column(nullable = false)
    private boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambiado_por")
    private Usuario cambiadoPor;

    @Column(name = "cambiado_en", nullable = false)
    private LocalDateTime cambiadoEn;

    protected CorreoAutomaticoConfig() {
    }

    public CorreoAutomaticoConfig(TipoCorreoAutomatico tipo) {
        this.tipo = tipo;
    }

    public void cambiar(boolean activo, Usuario usuario) {
        this.activo = activo;
        this.cambiadoPor = usuario;
        this.cambiadoEn = LocalDateTime.now();
    }

    public TipoCorreoAutomatico getTipo() { return tipo; }
    public boolean isActivo() { return activo; }
}
