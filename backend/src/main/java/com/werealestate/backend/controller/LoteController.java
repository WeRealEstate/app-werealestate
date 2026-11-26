package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ActualizarPoligonoMapaRequest;
import com.werealestate.backend.dto.ApartarVariosPublicoRequest;
import com.werealestate.backend.dto.ApartarVariosRequest;
import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LimpiarMapaRequest;
import com.werealestate.backend.dto.LoteCreateRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.LoteImportBatchRequest;
import com.werealestate.backend.dto.LoteImportResultado;
import com.werealestate.backend.dto.LoteUpdateRequest;
import com.werealestate.backend.dto.MovimientoLoteDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.dto.PlanoDesarrolloDto;
import com.werealestate.backend.dto.ResumenLotesDto;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.service.AsesorPublicoService;
import com.werealestate.backend.service.ClaveAsesor;
import com.werealestate.backend.service.HistorialLoteService;
import com.werealestate.backend.service.LimitadorPinPublico;
import com.werealestate.backend.service.LoteApartadoService;
import com.werealestate.backend.service.LoteConsultaService;
import com.werealestate.backend.service.LoteVentaService;
import com.werealestate.backend.service.LoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;
    private final LoteConsultaService consultaService;
    private final HistorialLoteService historialService;
    private final LoteVentaService loteVentaService;
    private final LoteApartadoService apartadoService;
    private final LimitadorPinPublico limitadorPin;

    public LoteController(LoteService loteService, LoteConsultaService consultaService,
            HistorialLoteService historialService, LoteVentaService loteVentaService,
            LoteApartadoService apartadoService, LimitadorPinPublico limitadorPin) {
        this.loteService = loteService;
        this.consultaService = consultaService;
        this.historialService = historialService;
        this.loteVentaService = loteVentaService;
        this.apartadoService = apartadoService;
        this.limitadorPin = limitadorPin;
    }

    @GetMapping("/buscar")
    public PaginaDto<LoteDto> buscar(
            @RequestParam(required = false) String manzana,
            @RequestParam(required = false) String numeroLote,
            @RequestParam(required = false) Long desarrolloId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) BigDecimal superficieMin,
            @RequestParam(required = false) BigDecimal superficieMax,
            @RequestParam(required = false) Boolean reservadoWe,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return consultaService.buscarPaginado(
                manzana, numeroLote, desarrolloId, estado, superficieMin, superficieMax, reservadoWe, pagina, tamano);
    }

    /** Inventario por desarrollo y estado, para las cifras del inicio del admin. */
    @GetMapping("/resumen")
    public List<ResumenLotesDto> resumen() {
        return consultaService.resumenPorDesarrollo();
    }

    @GetMapping("/disponibles")
    public List<LoteDto> listarDisponibles(@RequestParam Long desarrolloId) {
        return consultaService.listarDisponibles(desarrolloId);
    }

    /** Para el cotizador del panel: los lotes disponibles del desarrollo más los apartados que el asesor puede cotizar
     * (los suyos; admin y administración, todos). Los habilita el módulo Cotizador (ver ModuloAccesoInterceptor). */
    @GetMapping("/cotizables")
    public List<LoteDto> listarCotizables(@RequestParam Long desarrolloId) {
        return apartadoService.listarCotizables(desarrolloId);
    }

    /** Endpoints públicos de este controlador, sin sesión (ver SecurityConfig): la disponibilidad de lotes en los
     * planos públicos y el cotizador público, y apartar desde ahí, que exige el PIN del asesor (X-Asesor-Pin) con acceso
     * a ese desarrollo: el apartado queda a su nombre. */
    @GetMapping("/publico")
    public List<LoteDto> listarPublico(@RequestParam String proyecto) {
        return consultaService.listarPublicoPorProyecto(proyecto);
    }

    /** Apartar varios lotes a la vez desde el plano público (todo o nada, máximo 5). */
    @PutMapping("/publico/apartar-varios")
    public List<LoteDto> apartarVariosPublico(
            @Valid @RequestBody ApartarVariosPublicoRequest request,
            @RequestHeader(value = "X-Asesor-Pin", required = false) String pin,
            HttpServletRequest http) {
        AsesorPublicoService.Identificado asesor = exigirAsesor(http, pin, request.loteIds());
        return loteService.apartarVariosPublico(
                new ApartarVariosPublicoRequest(
                        request.loteIds(), asesor.nombre(), request.nombreCliente(), request.nota()),
                ClaveAsesor.de(asesor.usuario(), asesor.externo()));
    }

    @PutMapping("/publico/{id}/estado")
    public LoteDto cambiarEstadoPublico(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoLotePublicoRequest request,
            @RequestHeader(value = "X-Asesor-Pin", required = false) String pin,
            HttpServletRequest http) {
        AsesorPublicoService.Identificado asesor = exigirAsesor(http, pin, List.of(id));
        return loteService.cambiarEstadoPublico(
                id,
                new CambiarEstadoLotePublicoRequest(
                        request.estado(), asesor.nombre(), request.nombreCliente(), request.nota()),
                ClaveAsesor.de(asesor.usuario(), asesor.externo()));
    }

    /** Los apartados del asesor dueño del PIN (X-Asesor-Pin, nunca en la URL) en un plano, para que los cotice en el
     * cotizador público además de los disponibles. Sin PIN, 400; con uno que no sirve, el mismo error que el resto de los
     * endpoints con PIN (404 / 403 / 429) y cuenta contra el tope de intentos por IP: la página simplemente no los muestra. */
    @GetMapping("/publico/mis-apartados")
    public List<LoteDto> misApartadosPublico(
            @RequestParam String proyecto,
            @RequestHeader(value = "X-Asesor-Pin", required = false) String pin,
            HttpServletRequest http) {
        if (pin == null || pin.isBlank()) throw new ValidationException("Falta el PIN del asesor");
        AsesorPublicoService.Identificado asesor = limitadorPin.exigir(http, pin, proyecto);
        return apartadoService.listarApartadosPublicos(
                proyecto, ClaveAsesor.de(asesor.usuario(), asesor.externo()), asesor.nombre());
    }

    /** El asesor del PIN, con acceso a los planos de todos esos lotes. Sin PIN, 400; con uno que no sirve, 404 / 403 /
     * 429 como los demás endpoints con PIN (y cuenta contra el tope de intentos por IP). El nombre que se registra es el
     * del PIN, no el que mande el navegador. */
    private AsesorPublicoService.Identificado exigirAsesor(HttpServletRequest http, String pin, List<Long> loteIds) {
        if (pin == null || pin.isBlank()) throw new ValidationException("Falta el PIN del asesor");
        AsesorPublicoService.Identificado asesor = null;
        for (String proyecto : loteService.proyectosPublicosDe(loteIds)) {
            asesor = limitadorPin.exigir(http, pin, proyecto);
        }
        return asesor;
    }

    /** Historial de movimientos de todos los lotes (no solo uno), para /panel/lotes/historial. */
    @GetMapping("/movimientos")
    public PaginaDto<MovimientoLoteDto> buscarMovimientos(
            @RequestParam(required = false) String manzana,
            @RequestParam(required = false) String numeroLote,
            @RequestParam(required = false) Long desarrolloId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return historialService.buscarMovimientos(manzana, numeroLote, desarrolloId, pagina, tamano);
    }

    @DeleteMapping("/movimientos/{id}")
    public ResponseEntity<Void> eliminarMovimiento(@PathVariable Long id) {
        historialService.eliminarMovimiento(id);
        return ResponseEntity.noContent().build();
    }

    /** Plano interactivo de un desarrollo, para /panel/plano. */
    @GetMapping("/mapa")
    public PlanoDesarrolloDto obtenerMapa(@RequestParam Long desarrolloId) {
        return consultaService.obtenerMapa(desarrolloId);
    }

    @GetMapping("/{id}")
    public LoteDto obtener(@PathVariable Long id) {
        return loteService.obtener(id);
    }

    /** Historial completo de un lote específico, para el ícono de "ver información" en /panel/lotes. */
    @GetMapping("/{id}/historial")
    public List<MovimientoLoteDto> historialDeLote(@PathVariable Long id) {
        return historialService.historialDeLote(id);
    }

    @PostMapping
    public LoteDto crear(@Valid @RequestBody LoteCreateRequest request) {
        return loteService.crear(request);
    }

    @PostMapping("/importar")
    public LoteImportResultado importar(@Valid @RequestBody LoteImportBatchRequest request) {
        return loteService.importar(request);
    }

    @PutMapping("/precio-manzana")
    public java.util.Map<String, Integer> precioManzana(
            @Valid @RequestBody com.werealestate.backend.dto.PrecioManzanaRequest request) {
        return java.util.Map.of(
                "actualizados",
                loteService.actualizarPrecioManzana(request.desarrolloId(), request.manzana(), request.precioM2()));
    }

    @PutMapping("/{id}")
    public LoteDto actualizar(@PathVariable Long id, @Valid @RequestBody LoteUpdateRequest request) {
        return loteService.actualizar(id, request);
    }

    /** Apartar varios lotes a la vez desde el plano interno (todo o nada, máximo 5). */
    @PutMapping("/apartar-varios")
    public List<LoteDto> apartarVarios(@Valid @RequestBody ApartarVariosRequest request) {
        return loteService.apartarVarios(request);
    }

    @PutMapping("/{id}/estado")
    public LoteDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoLoteRequest request) {
        return loteService.cambiarEstado(id, request);
    }

    /** Pone un lote con venta que quedó en otro estado en el que le toca por su venta, sin pedir datos (ver
     * LoteService.alinearConVenta). */
    @PutMapping("/{id}/alinear-con-venta")
    public LoteDto alinearConVenta(@PathVariable Long id) {
        return loteVentaService.alinearConVenta(id);
    }

    @PutMapping("/{id}/mapa")
    public LoteDto actualizarPoligonoMapa(
            @PathVariable Long id, @Valid @RequestBody ActualizarPoligonoMapaRequest request) {
        return loteService.actualizarPoligonoMapa(id, request);
    }

    /** Quita la delimitación de todos los lotes de un desarrollo; solo admin y con su contraseña. */
    @PostMapping("/mapa/limpiar")
    public Map<String, Integer> limpiarMapa(@Valid @RequestBody LimpiarMapaRequest request) {
        return Map.of("limpiados", loteService.limpiarMapa(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        loteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
