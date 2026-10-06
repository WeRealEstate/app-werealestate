package com.werealestate.backend.controller;

import com.werealestate.backend.dto.GastoDto;
import com.werealestate.backend.dto.GastoRecurrenteDto;
import com.werealestate.backend.dto.GastoRecurrentePagoDto;
import com.werealestate.backend.dto.GastoRecurrenteRequest;
import com.werealestate.backend.dto.GastoResumenDto;
import com.werealestate.backend.dto.NominaPagadaDto;
import com.werealestate.backend.service.GastoRecurrenteService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/gastos")
public class GastoRecurrenteController {

    private final GastoRecurrenteService service;

    public GastoRecurrenteController(GastoRecurrenteService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    public GastoResumenDto resumen(@RequestParam(required = false) String mes) {
        return service.resumen(mes);
    }

    @GetMapping("/recurrentes")
    public List<GastoRecurrenteDto> listar() {
        return service.listar();
    }

    @PostMapping("/recurrentes")
    public GastoRecurrenteDto crear(@Valid @RequestBody GastoRecurrenteRequest request) {
        return service.crear(request);
    }

    @PutMapping("/recurrentes/{id}")
    public GastoRecurrenteDto editar(@PathVariable Long id, @Valid @RequestBody GastoRecurrenteRequest request) {
        return service.editar(id, request);
    }

    @DeleteMapping("/recurrentes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recurrentes/pagos")
    public List<GastoRecurrentePagoDto> pagosSinPagar() {
        return service.pagosSinPagar();
    }

    @PostMapping(value = "/recurrentes/pagos/{id}/pagar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GastoDto pagar(
            @PathVariable Long id,
            @RequestParam BigDecimal monto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(value = "ticket", required = false) MultipartFile ticket) {
        return service.pagar(id, monto, fecha, ticket);
    }

    /** Paga la nómina de un sábado de una vez (todas sus líneas pendientes). */
    @PostMapping("/recurrentes/pagos/pagar-nomina")
    public NominaPagadaDto pagarNomina(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sabado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago) {
        return service.pagarNomina(sabado, fechaPago);
    }

    @PostMapping("/recurrentes/pagos/{id}/omitir")
    public ResponseEntity<Void> omitir(@PathVariable Long id) {
        service.omitir(id);
        return ResponseEntity.noContent().build();
    }

    /** id = el del gasto que generó el pago. */
    @DeleteMapping("/recurrentes/pagos/gasto/{gastoId}")
    public ResponseEntity<Void> deshacerPago(@PathVariable Long gastoId) {
        service.deshacerPago(gastoId);
        return ResponseEntity.noContent().build();
    }
}
