package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ClienteDto;
import com.werealestate.backend.dto.ClienteListaDto;
import com.werealestate.backend.dto.ClienteNumeroRequest;
import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.dto.NumeracionClienteDto;
import com.werealestate.backend.service.ClienteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteListaDto> listar() {
        return clienteService.listar();
    }

    /** Para el selector de cliente de la venta: hasta 20 coincidencias (nombre, teléfono o CURP). */
    @GetMapping("/buscar")
    public List<ClienteListaDto> buscar(@RequestParam(required = false) String q) {
        return clienteService.buscar(q);
    }

    /** Siguiente número de cliente automático y, si "numero" ya lo tiene otro cliente (distinto de excluirId),
     * cuál es y cuántos se recorrerían: para avisarlo antes de guardar. */
    @GetMapping("/numeracion")
    public NumeracionClienteDto numeracion(
            @RequestParam(required = false) Long numero, @RequestParam(required = false) Long excluirId) {
        return clienteService.consultarNumeracion(numero, excluirId);
    }

    @GetMapping("/{id}")
    public ClienteDto obtener(@PathVariable Long id) {
        return clienteService.obtener(id);
    }

    @PostMapping
    public ClienteDto crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    @PutMapping("/{id}/numero")
    public ClienteDto cambiarNumero(@PathVariable Long id, @Valid @RequestBody ClienteNumeroRequest request) {
        return clienteService.cambiarNumero(id, request.numero());
    }

    @PutMapping("/{id}")
    public ClienteDto actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.actualizar(id, request);
    }
}
