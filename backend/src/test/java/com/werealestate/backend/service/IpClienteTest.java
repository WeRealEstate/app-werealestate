package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IpClienteTest {

    @Test
    void sinProxyIgnoraLaCabeceraFalsificada() {
        assertEquals("203.0.113.9", IpCliente.resolver("203.0.113.9", "1.2.3.4"));
    }

    @Test
    void detrasDeProxyUsaLaIpQueAnadioElProxy() {
        assertEquals("198.51.100.7", IpCliente.resolver("127.0.0.1", "198.51.100.7"));
        assertEquals("203.0.113.5", IpCliente.resolver("172.18.0.2", "203.0.113.5, 172.18.0.3"));
    }

    @Test
    void laIpInventadaPorElClienteNoGana() {
        // el cliente manda "9.9.9.9"; nginx añade la real al final
        assertEquals("198.51.100.7", IpCliente.resolver("127.0.0.1", "9.9.9.9, 198.51.100.7"));
    }

    @Test
    void cabeceraBasuraCaeALaIpDirecta() {
        assertEquals("127.0.0.1", IpCliente.resolver("127.0.0.1", "no-es-ip, ../etc"));
        assertEquals("127.0.0.1", IpCliente.resolver("127.0.0.1", null));
    }
}
