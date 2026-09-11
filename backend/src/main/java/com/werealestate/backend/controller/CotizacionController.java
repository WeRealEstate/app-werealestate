package com.werealestate.backend.controller;

import com.werealestate.backend.dto.CotizacionCreateRequest;
import com.werealestate.backend.dto.CotizacionDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.service.CotizacionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @PostMapping
    public CotizacionDto registrar(@Valid @RequestBody CotizacionCreateRequest request) {
        return cotizacionService.registrar(request);
    }

    /** Único endpoint que puede llamar /cotizador-publico (sin sesión, ver SecurityConfig):
     * registra igual que {@link #registrar}, pero atribuido al usuario de sistema en vez de a un
     * asesor autenticado. No expone lectura de ningún tipo. */
    @PostMapping("/publica")
    public CotizacionDto registrarPublica(@Valid @RequestBody CotizacionCreateRequest request) {
        return cotizacionService.registrarPublica(request);
    }

    @GetMapping
    public List<CotizacionDto> listar() {
        return cotizacionService.listar();
    }

    @GetMapping("/buscar")
    public PaginaDto<CotizacionDto> buscar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long asesorId,
            @RequestParam(required = false) String proyecto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return cotizacionService.buscarPaginado(busqueda, asesorId, proyecto, pagina, tamano);
    }
}
