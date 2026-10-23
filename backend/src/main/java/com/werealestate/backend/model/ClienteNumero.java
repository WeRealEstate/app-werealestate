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

/** El número de un cliente en un desarrollo (S-40 en SAMAI, N-12 en Aldea Nanuu); ver ClienteNumeracionService. Una
 * persona tiene a lo más uno por desarrollo; el mismo número lo comparten el titular y los copropietarios de una
 * compra. */
@Entity
@Table(name = "cliente_numero")
public class ClienteNumero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desarrollo_id", nullable = false)
    private Desarrollo desarrollo;

    @Column(nullable = false)
    private long numero;

    protected ClienteNumero() {
        // JPA
    }

    public ClienteNumero(Cliente cliente, Desarrollo desarrollo, long numero) {
        this.cliente = cliente;
        this.desarrollo = desarrollo;
        this.numero = numero;
    }

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public Desarrollo getDesarrollo() { return desarrollo; }
    public long getNumero() { return numero; }
}
