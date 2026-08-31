package com.werealestate.backend.service;

import com.werealestate.backend.dto.UsuarioCreateRequest;
import com.werealestate.backend.dto.UsuarioDto;
import com.werealestate.backend.dto.UsuarioUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioDto> listar() {
        exigirAdmin();
        return usuarioRepository.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getNombre))
                .map(UsuarioDto::from)
                .toList();
    }

    public UsuarioDto crear(UsuarioCreateRequest request) {
        exigirAdmin();
        if (usuarioRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }

        Usuario usuario = new Usuario(
                request.nombre(), request.email(), passwordEncoder.encode(request.password()), request.rol(), null);
        return UsuarioDto.from(usuarioRepository.save(usuario));
    }

    public UsuarioDto actualizar(Long id, UsuarioUpdateRequest request) {
        Usuario actual = exigirAdmin();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        boolean esUnoMismo = usuario.getId().equals(actual.getId());
        if (esUnoMismo && (request.rol() != Role.ADMIN || !request.activo())) {
            throw new ForbiddenOperationException("No puedes cambiar tu propio rol ni desactivar tu cuenta");
        }

        usuario.setNombre(request.nombre());
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());
        return UsuarioDto.from(usuarioRepository.save(usuario));
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar usuarios");
        }
        return actual;
    }
}
