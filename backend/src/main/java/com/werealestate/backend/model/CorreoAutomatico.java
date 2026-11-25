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
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un correo automático de una venta: en cola, enviado o por qué no salió (ver CorreoAutomaticoService). */
@Entity
@Table(name = "correo_automatico")
public class CorreoAutomatico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCorreoAutomatico tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCorreoAutomatico estado;

    /** Solo recordatorios: la fecha de pago que se recuerda. */
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "enviar_desde", nullable = false)
    private LocalDateTime enviarDesde;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(length = 500)
    private String detalle;

    protected CorreoAutomatico() {
    }

    public CorreoAutomatico(Venta venta, TipoCorreoAutomatico tipo, LocalDate fechaPago, LocalDateTime ahora,
            LocalDateTime enviarDesde) {
        this.venta = venta;
        this.tipo = tipo;
        this.fechaPago = fechaPago;
        this.creadoEn = ahora;
        this.enviarDesde = enviarDesde;
        this.estado = EstadoCorreoAutomatico.PENDIENTE;
    }

    public void resolver(EstadoCorreoAutomatico estado, String detalle, LocalDateTime ahora) {
        this.estado = estado;
        this.detalle = detalle;
        this.resueltoEn = ahora;
    }

    /** Cuenta el intento y deja fijada la hora del siguiente, por si este no llega a registrarse. */
    public void intentar(LocalDateTime siguiente) {
        this.intentos++;
        this.enviarDesde = siguiente;
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public TipoCorreoAutomatico getTipo() { return tipo; }
    public EstadoCorreoAutomatico getEstado() { return estado; }
    public LocalDate getFechaPago() { return fechaPago; }
    public LocalDateTime getEnviarDesde() { return enviarDesde; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getResueltoEn() { return resueltoEn; }
    public int getIntentos() { return intentos; }
    public String getDetalle() { return detalle; }
}
