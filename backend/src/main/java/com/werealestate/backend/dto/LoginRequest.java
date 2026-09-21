package com.werealestate.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        // Token del widget de Cloudflare Turnstile; solo se exige cuando app.turnstile.enabled=true
        // (ver TurnstileService), así que no lleva @NotBlank aquí.
        String captchaToken) {
}
