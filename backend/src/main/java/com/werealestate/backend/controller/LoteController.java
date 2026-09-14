package com.werealestate.backend.controller;

import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteCreateRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.LoteImportBatchRequest;
import com.werealestate.backend.dto.LoteImportResultado;
import com.werealestate.backend.dto.LoteUpdateRequest;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.service.LoteService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping("/buscar")
    public PaginaDto<LoteDto> buscar(
            @RequestParam(required = false) String manzana,
            @RequestParam(required = false) String numeroLote,
            @RequestParam(required = false) Long desarrolloId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) BigDecimal superficieMin,
            @RequestParam(required = false) BigDecimal superficieMax,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return loteService.buscarPaginado(
                manzana, numeroLote, desarrolloId, estado, superficieMin, superficieMax, pagina, tamano);
    }

    @GetMapping("/disponibles")
    public List<LoteDto> listarDisponibles(@RequestParam Long desarrolloId) {
        return loteService.listarDisponibles(desarrolloId);
    }

    @GetMapping("/{id}")
    public LoteDto obtener(@PathVariable Long id) {
        return loteService.obtener(id);
    }

    @PostMapping
    public LoteDto crear(@Valid @RequestBody LoteCreateRequest request) {
        return loteService.crear(request);
    }

    @PostMapping("/importar")
    public LoteImportResultado importar(@Valid @RequestBody LoteImportBatchRequest request) {
        return loteService.importar(request);
    }

    @PutMapping("/{id}")
    public LoteDto actualizar(@PathVariable Long id, @Valid @RequestBody LoteUpdateRequest request) {
        return loteService.actualizar(id, request);
    }

    @PutMapping("/{id}/estado")
    public LoteDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoLoteRequest request) {
        return loteService.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        loteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
