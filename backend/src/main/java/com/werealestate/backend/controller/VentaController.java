package com.werealestate.backend.controller;

import com.werealestate.backend.dto.AplicarDepositoRequest;
import com.werealestate.backend.dto.ContadoresVentasDto;
import com.werealestate.backend.dto.NumeracionVentaDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PagoVentaCreateRequest;
import com.werealestate.backend.dto.PagoVentaDto;
import com.werealestate.backend.dto.ReciboAnularRequest;
import com.werealestate.backend.dto.ReciboEditarRequest;
import com.werealestate.backend.dto.ReciboHistorialDto;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaEliminarRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaUpdateRequest;
import com.werealestate.backend.service.VentaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public VentaDto crear(@Valid @RequestBody VentaCreateRequest request) {
        return ventaService.crear(request);
    }

    @GetMapping("/buscar")
    public PaginaDto<VentaDto> buscar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "desc") String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @RequestParam(required = false) Long desarrolloId) {
        return ventaService.buscarPaginado(busqueda, "asc".equalsIgnoreCase(orden), pagina, tamano, desarrolloId);
    }

    /** desarrolloId opcional: solo las ventas y lotes de ese desarrollo. */
    @GetMapping("/contadores")
    public ContadoresVentasDto contadores(@RequestParam(required = false) Long desarrolloId) {
        return ventaService.contadores(desarrolloId);
    }

    /** Siguiente número automático y, si "numero" ya lo tiene otra venta (distinta de excluirId), cuál es
     * y cuántas se recorrerían: para avisarlo en el formulario antes de guardar. */
    @GetMapping("/numeracion")
    public NumeracionVentaDto numeracion(
            @RequestParam(required = false) Long numero, @RequestParam(required = false) Long excluirId) {
        return ventaService.consultarNumeracion(numero, excluirId);
    }

    @GetMapping("/samai-campestre/lotes-vendidos")
    public long contarLotesVendidosSamaiCampestre() {
        return ventaService.contarLotesVendidosSamaiCampestre();
    }

    /** Abrir una venta por su número visible (/panel/ventas/11), ver VentaService.obtenerPorNumero. */
    @GetMapping("/por-numero/{numero}")
    public VentaDto obtenerPorNumero(@PathVariable Long numero) {
        return ventaService.obtenerPorNumero(numero);
    }

    /** Aplica el depósito pendiente de una venta que nació de un precontrato (We Forms). */
    @PostMapping("/{id}/aplicar-deposito")
    public VentaDto aplicarDeposito(@PathVariable Long id, @Valid @RequestBody AplicarDepositoRequest request) {
        return ventaService.aplicarDeposito(id, request);
    }

    @GetMapping("/{id}")
    public VentaDto obtener(@PathVariable Long id) {
        return ventaService.obtener(id);
    }

    /** Registra o corrige si el contrato está firmado y desde cuándo. */
    @PutMapping("/{id}/contrato")
    public VentaDto registrarContrato(@PathVariable Long id, @Valid @RequestBody com.werealestate.backend.dto.ContratoRequest request) {
        return ventaService.registrarContrato(id, request);
    }

    /** Edita los datos no financieros de un recibo (folio, nota, tipo, meses, aportación). */
    @PutMapping("/{id}/recibos/{reciboId}")
    public List<PagoVentaDto> editarRecibo(
            @PathVariable Long id, @PathVariable Long reciboId, @Valid @RequestBody ReciboEditarRequest request) {
        return ventaService.editarRecibo(id, reciboId, request);
    }

    /** Anula un recibo (con motivo); no se borra. */
    @PostMapping("/{id}/recibos/{reciboId}/anular")
    public List<PagoVentaDto> anularRecibo(
            @PathVariable Long id, @PathVariable Long reciboId, @Valid @RequestBody ReciboAnularRequest request) {
        return ventaService.anularRecibo(id, reciboId, request);
    }

    /** Bitácora de los recibos de la venta. */
    @GetMapping("/{id}/recibos/historial")
    public List<ReciboHistorialDto> historialDeRecibos(@PathVariable Long id) {
        return ventaService.historialDeRecibos(id);
    }

    /** Temporal: modifica los datos capturados de una venta (cliente, fechas, términos). */
    /** Elimina la venta por completo (solo admin, con su contraseña). POST y no DELETE porque lleva
     * cuerpo (la contraseña). */
    @PostMapping("/{id}/eliminar")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @Valid @RequestBody VentaEliminarRequest request) {
        ventaService.eliminar(id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public VentaDto actualizar(@PathVariable Long id, @Valid @RequestBody VentaUpdateRequest request) {
        return ventaService.actualizar(id, request);
    }

    /** Para /panel/lotes: qué venta corresponde a un lote marcado como vendido. */
    @GetMapping("/por-lote/{loteId}")
    public VentaDto obtenerPorLote(@PathVariable Long loteId) {
        return ventaService.obtenerPorLote(loteId);
    }

    @GetMapping("/{id}/pagos")
    public List<PagoVentaDto> listarPagos(@PathVariable Long id) {
        return ventaService.listarPagos(id);
    }

    @PostMapping("/{id}/pagos")
    public List<PagoVentaDto> registrarPago(@PathVariable Long id, @Valid @RequestBody PagoVentaCreateRequest request) {
        return ventaService.registrarPago(id, request);
    }
}
