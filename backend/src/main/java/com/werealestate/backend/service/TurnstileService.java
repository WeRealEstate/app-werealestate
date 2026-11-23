package com.werealestate.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.werealestate.backend.exception.ValidationException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean habilitado;
    private final String secretKey;
    private final RestClient restClient;

    public TurnstileService(
            @Value("${app.turnstile.enabled}") boolean habilitado,
            @Value("${app.turnstile.secret-key}") String secretKey) {
        this.habilitado = habilitado;
        this.secretKey = secretKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
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
            log.error("Error al contactar a Cloudflare Turnstile: {}", e.getMessage(), e);
            throw new ValidationException("No se pudo validar el captcha en este momento. Intenta de nuevo.");
        }

        if (respuesta == null || !respuesta.success()) {
            log.warn("Turnstile rechazado por Cloudflare: errorCodes={}, hostname={}",
                    respuesta != null ? respuesta.errorCodes() : null,
                    respuesta != null ? respuesta.hostname() : null);
            throw new ValidationException("Captcha inválido o expirado, intenta de nuevo.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileResponse(
            boolean success,
            @JsonProperty("error-codes") List<String> errorCodes,
            String hostname
    ) {}
}
