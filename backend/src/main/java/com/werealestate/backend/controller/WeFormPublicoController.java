package com.werealestate.backend.controller;

import tools.jackson.databind.ObjectMapper;
import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.service.AsesorPublicoService;
import com.werealestate.backend.service.LimitadorPinPublico;
import com.werealestate.backend.service.WeFormPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Link público de We Forms (sin sesión, ver SecurityConfig). Cada petición exige el PIN del asesor en la
 * cabecera X-Asesor-Pin (nunca en la URL); los intentos fallidos se limitan por IP. Vive fuera de
 * /api/we-forms a propósito: ese prefijo exige el módulo, y aquí entra cualquier asesor con PIN. */
@RestController
@RequestMapping("/api/forms-publico")
public class WeFormPublicoController {

    private final WeFormPublicoService service;
    private final LimitadorPinPublico limitador;
    private final ObjectMapper objectMapper;

    public WeFormPublicoController(WeFormPublicoService service, LimitadorPinPublico limitador, ObjectMapper objectMapper) {
        this.service = service;
        this.limitador = limitador;
        this.objectMapper = objectMapper;
    }

    /** Formularios activos del desarrollo de un plano (para elegir desde el botón "Registrar venta"). */
    @GetMapping("/formularios")
    public List<WeFormDtos.PublicoLista> listar(
            @RequestParam String proyecto, @RequestHeader("X-Asesor-Pin") String pin, HttpServletRequest http) {
        limitador.exigir(http, pin, proyecto);
        return service.listarActivos(proyecto);
    }

    @GetMapping("/{slug}")
    public WeFormDtos.Publico obtener(
            @PathVariable String slug, @RequestHeader("X-Asesor-Pin") String pin, HttpServletRequest http) {
        AsesorPublicoService.Identificado asesor = limitador.exigir(http, pin, service.proyectoDe(slug));
        return service.obtener(slug, asesor);
    }

    /** multipart: la parte "datos" es el JSON con las respuestas y cada archivo va en "archivo-{preguntaId}". */
    @PostMapping("/{slug}/respuestas")
    public WeFormDtos.EnvioResultado enviar(
            @PathVariable String slug,
            @RequestHeader("X-Asesor-Pin") String pin,
            @RequestParam("datos") String datos,
            MultipartHttpServletRequest multipart,
            HttpServletRequest http) {
        AsesorPublicoService.Identificado asesor = limitador.exigir(http, pin, service.proyectoDe(slug));
        WeFormDtos.EnvioRequest envio;
        try {
            envio = objectMapper.readValue(datos, WeFormDtos.EnvioRequest.class);
        } catch (RuntimeException e) {
            throw new ValidationException("No se pudieron leer las respuestas");
        }
        Map<Long, MultipartFile> archivos = new HashMap<>();
        multipart.getFileMap().forEach((nombre, archivo) -> {
            if (nombre.startsWith("archivo-")) {
                try {
                    archivos.put(Long.parseLong(nombre.substring("archivo-".length())), archivo);
                } catch (NumberFormatException ignorado) {
                    // parte con un nombre que no es de una pregunta: se ignora
                }
            }
        });
        return service.enviar(slug, asesor, envio, archivos);
    }
}
