package com.werealestate.backend.controller;

import com.werealestate.backend.dto.ModuloCatalogoDto;
import com.werealestate.backend.dto.UsuarioCreateRequest;
import com.werealestate.backend.dto.UsuarioDto;
import com.werealestate.backend.dto.UsuarioNominaRequest;
import com.werealestate.backend.dto.UsuarioResetPasswordRequest;
import com.werealestate.backend.dto.UsuarioResumenDto;
import com.werealestate.backend.dto.UsuarioUpdateRequest;
import com.werealestate.backend.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioDto> listar() {
        return usuarioService.listar();
    }

    @GetMapping("/me")
    public UsuarioDto perfil() {
        return usuarioService.perfil();
    }

    @GetMapping("/modulos")
    public List<ModuloCatalogoDto> catalogoModulos() {
        return usuarioService.catalogoModulos();
    }

    @GetMapping("/asignables")
    public List<UsuarioResumenDto> asignables() {
        return usuarioService.asignables();
    }

    @GetMapping("/para-venta")
    public List<UsuarioResumenDto> paraVenta() {
        return usuarioService.paraVenta();
    }

    @PostMapping
    public UsuarioDto crear(@Valid @RequestBody UsuarioCreateRequest request) {
        return usuarioService.crear(request);
    }

    @PutMapping("/{id}")
    public UsuarioDto actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest request) {
        return usuarioService.actualizar(id, request);
    }

    /** Nómina semanal de un usuario (solo admin): monto y desde cuándo; sin monto la quita. */
    @PutMapping("/{id}/nomina")
    public UsuarioDto actualizarNomina(@PathVariable Long id, @RequestBody UsuarioNominaRequest request) {
        return usuarioService.actualizarNomina(id, request);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> restablecerPassword(
            @PathVariable Long id, @Valid @RequestBody UsuarioResetPasswordRequest request) {
        usuarioService.restablecerPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
