package com.werealestate.backend.controller;

import com.werealestate.backend.dto.PromocionCodigoDto;
import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.service.RegistroVentaPanelService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/** "Registrar venta" en el panel del asesor interno: los mismos formularios que el enlace público, con su sesión. */
@RestController
@RequestMapping("/api/registro-venta")
public class RegistroVentaPanelController {

    private final RegistroVentaPanelService service;
    private final ObjectMapper objectMapper;

    public RegistroVentaPanelController(RegistroVentaPanelService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /** Formularios activos de SAMAI y Nanuu. */
    @GetMapping("/formularios")
    public List<WeFormDtos.PanelLista> listar() {
        return service.listar();
    }

    @GetMapping("/{slug}")
    public WeFormDtos.Publico obtener(@PathVariable String slug) {
        return service.obtener(slug);
    }

    @PostMapping("/{slug}/respuestas")
    public WeFormDtos.EnvioResultado enviar(
            @PathVariable String slug, @RequestParam("datos") String datos, MultipartHttpServletRequest multipart) {
        WeFormPublicoController.Envio envio = WeFormPublicoController.leerEnvio(objectMapper, datos, multipart);
        return service.enviar(slug, envio.datos(), envio.archivos());
    }

    /** La promoción de un código escrito en el formulario; los equivocados se limitan por usuario (10 por hora). */
    @PostMapping("/{slug}/codigo-promocion")
    public PromocionCodigoDto codigoPromocion(@PathVariable String slug, @RequestBody WeFormDtos.CodigoPromocionRequest body) {
        return service.codigoPromocion(slug, body.codigo());
    }
}
