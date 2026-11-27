package com.werealestate.backend.integracion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.ClienteDto;
import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.service.ClienteService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** «Factura» del cliente (V99): sin factura de origen, se cambia en la ficha y editar sin mandarla no la toca. */
class ClienteFacturaIntegracionTest extends PruebaDeIntegracion {

    @Autowired ClienteService clientes;

    private static ClienteRequest peticion(String nombre, Boolean requiereFactura) {
        String telefono = "9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        return new ClienteRequest(nombre, "Factura", null, LocalDate.of(1990, 1, 1), telefono, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, requiereFactura);
    }

    @Test
    void nacerSinFacturaMarcarlaYConservarlaAlEditarSinMandarla() {
        iniciarSesionComo(usuario(Role.LIDER_AREA));
        String nombre = "Prueba " + etiqueta();
        ClienteDto nuevo = clientes.crear(peticion(nombre, null));
        assertFalse(nuevo.requiereFactura());

        assertTrue(clientes.actualizar(nuevo.id(), peticion(nombre, true)).requiereFactura());
        assertTrue(clientes.actualizar(nuevo.id(), peticion(nombre, null)).requiereFactura());
        assertFalse(clientes.actualizar(nuevo.id(), peticion(nombre, false)).requiereFactura());
    }
}
