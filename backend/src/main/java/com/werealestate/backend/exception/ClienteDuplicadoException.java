package com.werealestate.backend.exception;

/** Ya existe un cliente con esos datos; clienteId es el existente, para ofrecer usarlo. */
public class ClienteDuplicadoException extends RuntimeException {

    private final Long clienteId;

    public ClienteDuplicadoException(String message, Long clienteId) {
        super(message);
        this.clienteId = clienteId;
    }

    public Long getClienteId() {
        return clienteId;
    }
}
