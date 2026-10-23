package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ClienteDto;
import com.werealestate.backend.dto.ClienteListaDto;
import com.werealestate.backend.dto.ClienteNumeroRequest;
import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.dto.NumeracionClienteDto;
import com.werealestate.backend.dto.NumeroContratoRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.service.ClienteService;
import com.werealestate.backend.service.VentaService;
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
    private final VentaService ventaService;

    public ClienteController(ClienteService clienteService, VentaService ventaService) {
        this.clienteService = clienteService;
        this.ventaService = ventaService;
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

    /** Siguiente número de cliente automático de ese desarrollo y, si "numero" ya lo tiene otro cliente (distinto de
     * excluirId), cuál es y cuántos se recorrerían: para avisarlo antes de guardar. */
    @GetMapping("/numeracion")
    public NumeracionClienteDto numeracion(
            @RequestParam Long desarrolloId,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) Long excluirId) {
        return clienteService.consultarNumeracion(desarrolloId, numero, excluirId);
    }

    @GetMapping("/{id}")
    public ClienteDto obtener(@PathVariable Long id) {
        return clienteService.obtener(id);
    }

    @PostMapping
    public ClienteDto crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    /** El número de contrato de una de las ventas del cliente, editable desde su detalle. Va bajo /api/clientes (y
     * no /api/ventas) para que lo pueda usar quien tenga el módulo Clientes aunque no tenga el de Ventas. */
    @PutMapping("/ventas/{ventaId}/numero-contrato")
    public VentaDto numeroContratoDeVenta(@PathVariable Long ventaId, @Valid @RequestBody NumeroContratoRequest request) {
        return ventaService.registrarNumeroContrato(ventaId, request);
    }

    @PutMapping("/{id}/numero")
    public ClienteDto cambiarNumero(@PathVariable Long id, @Valid @RequestBody ClienteNumeroRequest request) {
        return clienteService.cambiarNumero(id, request.desarrolloId(), request.numero());
    }

    @PutMapping("/{id}")
    public ClienteDto actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.actualizar(id, request);
    }
}
