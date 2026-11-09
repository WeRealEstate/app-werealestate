package com.werealestate.backend.security;

import io.jsonwebtoken.Claims;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lee el JWT del header Authorization y, si es válido, autentica la request. La firma no basta: se consulta al
 * usuario en la base de datos para que quien fue desactivado (o borrado) pierda el acceso en la siguiente petición
 * y el rol que cuenta sea el actual, no el que quedó escrito en el token al iniciar sesión. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                String email = claims.getSubject();

                // Sin usuario activo no se autentica: la petición sigue como anónima y los endpoints protegidos
                // responden 401, con lo que el frontend cierra la sesión.
                Usuario usuario = email == null ? null : usuarioRepository.findByEmail(email).orElse(null);
                if (usuario != null && usuario.isActivo()) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, authorities);
                    // Guardamos la entidad cargada en details para que ModuloAccesoInterceptor y los servicios
                    // que llaman a CurrentUserProvider no repitan SELECT * FROM usuario en la misma petición.
                    authentication.setDetails(usuario);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
