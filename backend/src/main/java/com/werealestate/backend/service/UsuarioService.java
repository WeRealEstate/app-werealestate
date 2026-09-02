package com.werealestate.backend.service;

import com.werealestate.backend.dto.UsuarioCreateRequest;
import com.werealestate.backend.dto.UsuarioDto;
import com.werealestate.backend.dto.UsuarioResetPasswordRequest;
import com.werealestate.backend.dto.UsuarioResumenDto;
import com.werealestate.backend.dto.UsuarioUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ComisionRepository;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
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
    private final LeadRepository leadRepository;
    private final TareaRepository tareaRepository;
    private final ComisionRepository comisionRepository;
    private final EventoCalendarioRepository eventoCalendarioRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            LeadRepository leadRepository,
            TareaRepository tareaRepository,
            ComisionRepository comisionRepository,
            EventoCalendarioRepository eventoCalendarioRepository,
            SeguimientoRepository seguimientoRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.leadRepository = leadRepository;
        this.tareaRepository = tareaRepository;
        this.comisionRepository = comisionRepository;
        this.eventoCalendarioRepository = eventoCalendarioRepository;
        this.seguimientoRepository = seguimientoRepository;
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

    /**
     * Lista ligera (solo id/nombre) de equipo interno activo, para el picker de "asignar a" en
     * tareas. Solo lo usan admin y líderes de área, que son quienes pueden asignar tareas.
     */
    public List<UsuarioResumenDto> asignables() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("No tienes acceso a la lista de usuarios");
        }
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && u.getRol() == Role.EQUIPO_INTERNO)
                .sorted(Comparator.comparing(Usuario::getNombre))
                .map(UsuarioResumenDto::from)
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

    /**
     * Elimina definitivamente a un usuario. Se rechaza si tiene actividad registrada (leads,
     * tareas, comisiones, eventos o seguimientos) para no perder historial ni romper referencias;
     * en ese caso hay que desactivarlo en vez de eliminarlo.
     */
    public void eliminar(Long id) {
        Usuario actual = exigirAdmin();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (usuario.getId().equals(actual.getId())) {
            throw new ForbiddenOperationException("No puedes eliminar tu propia cuenta");
        }

        boolean tieneActividad = leadRepository.existsByAsesorId(id)
                || tareaRepository.existsByAsignadoAId(id)
                || tareaRepository.existsByCreadoPorId(id)
                || comisionRepository.existsByAsesorId(id)
                || eventoCalendarioRepository.existsByUsuarioId(id)
                || seguimientoRepository.existsByAsesorId(id);
        if (tieneActividad) {
            throw new ConflictException("No se puede eliminar a " + usuario.getNombre()
                    + ": tiene actividad registrada (leads, tareas, comisiones, seguimientos o eventos). "
                    + "Desactívalo para quitarle el acceso sin perder ese historial.");
        }

        usuarioRepository.delete(usuario);
    }

    /** Solo un admin puede fijar la contraseña de cualquier usuario (incluida la propia). Los usuarios no la cambian ellos mismos. */
    public void restablecerPassword(Long id, UsuarioResetPasswordRequest request) {
        exigirAdmin();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuarioRepository.save(usuario);
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar usuarios");
        }
        return actual;
    }
}
