package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ConfigCorreosDto;
import com.werealestate.backend.dto.CorreosVentaDto;
import com.werealestate.backend.model.NumeracionVisible;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import com.werealestate.backend.service.CorreoAutomaticoService;
import com.werealestate.backend.service.CorreoVentaConsultaService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Correos automáticos: interruptores y registro por venta (cuelga de /api/ventas, módulo VENTAS). */
@RestController
@RequestMapping("/api/ventas")
public class CorreoVentaController {

    private final CorreoVentaConsultaService servicio;

    private final CorreoAutomaticoService envio;

    public CorreoVentaController(CorreoVentaConsultaService servicio, CorreoAutomaticoService envio) {
        this.servicio = servicio;
        this.envio = envio;
    }

    /** Manda ahora un correo automático que no salió y devuelve el registro actualizado. */
    @PostMapping("/{id}/correos/{correoId}/reintentar")
    public CorreosVentaDto reintentar(@PathVariable Long id, @PathVariable Long correoId) {
        // El correo lleva el número oficial de la venta aunque lo mande la cuenta con numeración propia.
        NumeracionVisible.sinRenumerar(() -> {
            envio.reintentar(id, correoId);
            return null;
        });
        return servicio.deVenta(id);
    }

    @GetMapping("/correos-automaticos")
    public ConfigCorreosDto configuracion() {
        return servicio.configuracion();
    }

    /** Cuerpo: {"activo": true|false}. */
    @PutMapping("/correos-automaticos/{tipo}")
    public ConfigCorreosDto cambiar(@PathVariable TipoCorreoAutomatico tipo, @RequestBody Map<String, Boolean> cuerpo) {
        return servicio.cambiar(tipo, Boolean.TRUE.equals(cuerpo.get("activo")));
    }

    @GetMapping("/{id}/correos")
    public CorreosVentaDto deVenta(@PathVariable Long id) {
        return servicio.deVenta(id);
    }

    /** Cuerpo: {"excluido": true|false}. */
    @PutMapping("/{id}/correos/recordatorio-automatico")
    public CorreosVentaDto excluir(@PathVariable Long id, @RequestBody Map<String, Boolean> cuerpo) {
        return servicio.excluirRecordatorio(id, Boolean.TRUE.equals(cuerpo.get("excluido")));
    }
}
