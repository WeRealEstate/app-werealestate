package com.werealestate.backend.dto;

/** Los tres interruptores de los correos automáticos. */
public record ConfigCorreosDto(boolean bienvenida, boolean liquidacion, boolean recordatorio) {
}
