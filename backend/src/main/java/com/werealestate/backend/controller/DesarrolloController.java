package com.werealestate.backend.controller;

import com.werealestate.backend.dto.DesarrolloDto;
import com.werealestate.backend.repository.DesarrolloRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/desarrollos")
public class DesarrolloController {

    private final DesarrolloRepository desarrolloRepository;

    public DesarrolloController(DesarrolloRepository desarrolloRepository) {
        this.desarrolloRepository = desarrolloRepository;
    }

    @GetMapping
    public List<DesarrolloDto> listar() {
        return desarrolloRepository.findAll().stream().map(DesarrolloDto::from).toList();
    }
}
