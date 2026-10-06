package com.werealestate.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CustomUserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Cadena aparte, evaluada antes que la principal, exclusiva de /uploads/** (los planos): son
     * imágenes de varios MB que casi nunca cambian, así que interesa que el navegador las cachee.
     * Por default, el header writer de Security manda "Cache-Control: no-cache, no-store,
     * max-age=0, must-revalidate" en TODA respuesta — incluida esta, forzando a descargarla entera
     * en cada visita al link público del plano aunque no haya cambiado desde la última. Con
     * cache.disable() esta cadena deja de mandar ese header, y el que sí queda (uno cacheable, ver
     * WebConfig) es el único que el navegador ve. Seguro de cachear "para siempre": la URL del
     * plano lleva un query de cache-busting (?v=timestamp) que cambia solo cuando se reemplaza. */
    @Bean
    @Order(1)
    public SecurityFilterChain planosEstaticosFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/uploads/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.cacheControl(cache -> cache.disable()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Únicos endpoints públicos además del login, ambos para /cotizador-publico
                        // (sin sesión): consultar las promociones vigentes y registrar la cotización
                        // en el historial atribuida al usuario de sistema (ver CotizacionService).
                        // Todo lo demás, incluido el resto de /api/promociones y /api/cotizaciones
                        // (listar, buscar, registrar con sesión), sigue exigiendo autenticación.
                        .requestMatchers(HttpMethod.GET, "/api/promociones/activas").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cotizaciones/publica").permitAll()
                        // /cotizador-publico/lotes y /samai, /aldea-nanuu: ver disponibilidad y
                        // apartar un lote sin sesión, con las mismas restricciones que un asesor
                        // (ver LoteService). /uploads/** tiene su propia cadena arriba.
                        .requestMatchers(HttpMethod.GET, "/api/lotes/publico").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/lotes/publico/*/estado").permitAll()
                        // Botón "Asesor" de /samai y /aldea-nanuu: confirma que el nombre existe.
                        .requestMatchers(HttpMethod.POST, "/api/lotes/publico/verificar-asesor").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write("{\"message\":\"No autenticado\"}");
                }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
