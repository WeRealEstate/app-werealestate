package com.werealestate.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.werealestate.backend.exception.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifica el captcha (Cloudflare Turnstile) del formulario de login contra la API de Cloudflare,
 * como defensa contra fuerza bruta: sin esto, /api/auth/login no tenía ningún límite de intentos.
 * Si app.turnstile.enabled=false (default en dev/test) no llama a Cloudflare y deja pasar cualquier
 * request, para no depender de llaves reales fuera del VPS.
 */
@Service
public class TurnstileService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean habilitado;
    private final String secretKey;
    private final RestClient restClient;

    public TurnstileService(
            @Value("${app.turnstile.enabled}") boolean habilitado,
            @Value("${app.turnstile.secret-key}") String secretKey) {
        this.habilitado = habilitado;
        this.secretKey = secretKey;
        this.restClient = RestClient.create();
    }

    public void verificar(String token) {
        if (!habilitado) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new ValidationException("Completa el captcha para continuar.");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);

        TurnstileResponse respuesta;
        try {
            respuesta = restClient
                    .post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(TurnstileResponse.class);
        } catch (RestClientException e) {
            // Cloudflare no responde o responde con error: se rechaza el login en vez de dejarlo
            // pasar sin validar (fail-closed), para no anular la protección justo cuando más podría
            // estar bajo ataque.
            throw new ValidationException("No se pudo validar el captcha en este momento. Intenta de nuevo.");
        }

        if (respuesta == null || !respuesta.success()) {
            throw new ValidationException("Captcha inválido o expirado, intenta de nuevo.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileResponse(boolean success) {
    }
}
