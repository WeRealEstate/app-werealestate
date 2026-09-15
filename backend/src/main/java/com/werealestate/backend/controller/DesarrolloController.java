package com.werealestate.backend.controller;

import com.werealestate.backend.dto.DesarrolloDto;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.service.DesarrolloService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/desarrollos")
public class DesarrolloController {

    private final DesarrolloRepository desarrolloRepository;
    private final DesarrolloService desarrolloService;

    public DesarrolloController(DesarrolloRepository desarrolloRepository, DesarrolloService desarrolloService) {
        this.desarrolloRepository = desarrolloRepository;
        this.desarrolloService = desarrolloService;
    }

    @GetMapping
    public List<DesarrolloDto> listar() {
        return desarrolloRepository.findAll().stream().map(DesarrolloDto::from).toList();
    }

    /** Subir/reemplazar la imagen del plano de un desarrollo, para /panel/plano; exclusivo de
     * admin, ver DesarrolloService. */
    @PostMapping(value = "/{id}/plano", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DesarrolloDto subirPlano(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return desarrolloService.actualizarPlano(id, archivo);
    }
}
