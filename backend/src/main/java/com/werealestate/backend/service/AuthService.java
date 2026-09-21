package com.werealestate.backend.service;

import com.werealestate.backend.dto.LoginRequest;
import com.werealestate.backend.dto.LoginResponse;
import com.werealestate.backend.dto.UsuarioDto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final TurnstileService turnstileService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            JwtService jwtService,
            TurnstileService turnstileService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.turnstileService = turnstileService;
    }

    public LoginResponse login(LoginRequest request) {
        // Se valida antes que las credenciales: así un intento de fuerza bruta nunca llega a
        // comparar contraseñas (ni gasta el costo de BCrypt) sin haber resuelto el captcha primero.
        turnstileService.verificar(request.captchaToken());

        // Lanza BadCredentialsException (-> 401) si el email no existe o la contraseña no coincide.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Usuario usuario = usuarioRepository
                .findByEmail(request.email())
                .orElseThrow(); // no debería pasar: authenticate() ya validó que existe

        String token = jwtService.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol().name());

        return new LoginResponse(token, UsuarioDto.from(usuario));
    }
}
