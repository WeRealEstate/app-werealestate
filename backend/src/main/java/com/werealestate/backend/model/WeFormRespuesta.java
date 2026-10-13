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
import java.time.LocalDateTime;

/** Un precontrato enviado: quién lo llenó, qué cliente y qué venta resultaron. */
@Entity
@Table(name = "we_form_respuesta")
public class WeFormRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private WeForm form;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_usuario_id")
    private Usuario asesorUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_externo_id")
    private AsesorExterno asesorExterno;

    @Column(name = "asesor_nombre", nullable = false, length = 200)
    private String asesorNombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "cliente_nuevo", nullable = false)
    private boolean clienteNuevo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    protected WeFormRespuesta() {
        // JPA
    }

    public WeFormRespuesta(
            WeForm form, Usuario asesorUsuario, AsesorExterno asesorExterno, String asesorNombre,
            Cliente cliente, boolean clienteNuevo, Venta venta) {
        this.form = form;
        this.asesorUsuario = asesorUsuario;
        this.asesorExterno = asesorExterno;
        this.asesorNombre = asesorNombre;
        this.cliente = cliente;
        this.clienteNuevo = clienteNuevo;
        this.venta = venta;
    }

    public Long getId() { return id; }
    public WeForm getForm() { return form; }
    public Usuario getAsesorUsuario() { return asesorUsuario; }
    public AsesorExterno getAsesorExterno() { return asesorExterno; }
    public String getAsesorNombre() { return asesorNombre; }
    public Cliente getCliente() { return cliente; }
    public boolean isClienteNuevo() { return clienteNuevo; }
    public Venta getVenta() { return venta; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
}
