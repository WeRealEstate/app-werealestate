package com.werealestate.backend.controller;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.dto.NotificacionMarcarLeidaRequest;
import com.werealestate.backend.service.NotificacionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public List<NotificacionDto> listar() {
        return notificacionService.listar();
    }

    @PostMapping("/leida")
    public void marcarLeida(@Valid @RequestBody NotificacionMarcarLeidaRequest request) {
        notificacionService.marcarLeida(request);
    }
}
