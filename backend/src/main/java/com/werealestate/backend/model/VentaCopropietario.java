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

/** Un copropietario de una venta (además del cliente principal, ver Venta.clienteRef). Máximo 4. */
@Entity
@Table(name = "venta_copropietario")
public class VentaCopropietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private int orden;

    protected VentaCopropietario() {
        // JPA
    }

    public VentaCopropietario(Venta venta, Cliente cliente, int orden) {
        this.venta = venta;
        this.cliente = cliente;
        this.orden = orden;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public int getOrden() {
        return orden;
    }
}
